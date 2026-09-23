package net.tfminecraft.simplefactions.guild.income;

import java.util.HashMap;
import java.util.Map;

import net.tfminecraft.simplefactions.diplomacy.DiplomacyHandler;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.enums.Brackets;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawEffect;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.TaxHandler;
import net.tfminecraft.simplefactions.utils.BracketToTaxTarget;

/**
 * Hypothetical law, favour, treaty, or tax rate for one income preview.
 * Only the thread that opened the context can see it, and nothing here writes
 * the live faction.
 */
public final class IncomePreviewContext {
    private static final ThreadLocal<IncomePreviewContext> CURRENT = new ThreadLocal<>();

    private final boolean scratch;
    private final Faction faction;
    private final LawGroup lawGroup;
    private final Law law;
    private final Guild favourGuild;
    private final Boolean favoured;
    private final Boolean repressed;
    private final DiplomacyHandler originHandler;
    private final DiplomacyHandler targetHandler;
    private final String originId;
    private final String targetId;
    private final RelationType originRelation;
    private final RelationType targetRelation;
    private final boolean overrideTargetRelation;
    private final TaxTarget taxTarget;
    private final String taxId;
    private final Double taxRate;
    private final Map<String, TradeBreakdown> breakdowns = new HashMap<>();

    private IncomePreviewContext(
            boolean scratch,
            Faction faction,
            LawGroup lawGroup,
            Law law,
            Guild favourGuild,
            Boolean favoured,
            Boolean repressed,
            DiplomacyHandler originHandler,
            DiplomacyHandler targetHandler,
            String originId,
            String targetId,
            RelationType originRelation,
            RelationType targetRelation,
            boolean overrideTargetRelation,
            TaxTarget taxTarget,
            String taxId,
            Double taxRate) {
        this.scratch = scratch;
        this.faction = faction;
        this.lawGroup = lawGroup;
        this.law = law;
        this.favourGuild = favourGuild;
        this.favoured = favoured;
        this.repressed = repressed;
        this.originHandler = originHandler;
        this.targetHandler = targetHandler;
        this.originId = originId;
        this.targetId = targetId;
        this.originRelation = originRelation;
        this.targetRelation = targetRelation;
        this.overrideTargetRelation = overrideTargetRelation;
        this.taxTarget = taxTarget;
        this.taxId = taxId;
        this.taxRate = taxRate;
    }

    public static IncomePreviewContext scratch() {
        return new IncomePreviewContext(
                true, null, null, null, null, null, null,
                null, null, null, null, null, null, false,
                null, null, null);
    }

    public static IncomePreviewContext law(Faction faction, LawGroup group, Law law) {
        return new IncomePreviewContext(
                true, faction, group, law, null, null, null,
                null, null, null, null, null, null, false,
                null, null, null);
    }

    public static IncomePreviewContext favour(Guild guild, boolean favourMode) {
        boolean nextFavour = guild.isFavoured();
        boolean nextRepress = guild.isRepressed();
        if (favourMode) {
            if (!guild.isRepressed()) {
                nextFavour = !nextFavour;
            }
        } else if (!guild.isFavoured()) {
            nextRepress = !nextRepress;
        }
        return new IncomePreviewContext(
                true, guild.getFaction(), null, null, guild, nextFavour, nextRepress,
                null, null, null, null, null, null, false,
                null, null, null);
    }

    public static IncomePreviewContext trade(Faction origin, Faction target, RelationType agreement) {
        DiplomacyHandler originHandler = origin.getDiplomacyHandler();
        DiplomacyHandler targetHandler = target.getDiplomacyHandler();
        RelationType current = originHandler.getTradeRelation(target.getId());
        RelationType targetCurrent = targetHandler.getTradeRelation(origin.getId());
        RelationType originRelation = agreement;
        RelationType targetRelation = targetCurrent;
        boolean overrideTarget = false;
        if (agreement != null) {
            if (agreement.hasLink()) {
                targetRelation = agreement.getLink();
                overrideTarget = true;
            }
        } else {
            originRelation = null;
            if (current != null && current.isMutual()) {
                targetRelation = null;
                overrideTarget = true;
            }
        }
        return new IncomePreviewContext(
                true, null, null, null, null, null, null,
                originHandler, targetHandler, origin.getId(), target.getId(),
                originRelation, targetRelation, overrideTarget,
                null, null, null);
    }

    public static IncomePreviewContext tax(Faction faction, TaxTarget target, String id, double rate) {
        return new IncomePreviewContext(
                false, faction, null, null, null, null, null,
                null, null, null, null, null, null, false,
                target, id, rate);
    }

    public static void open(IncomePreviewContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static IncomePreviewContext current() {
        return CURRENT.get();
    }

    public static Law overlayLaw(LawGroup group) {
        IncomePreviewContext context = current();
        if (context == null || context.lawGroup != group) {
            return null;
        }
        return context.law;
    }

    public static Boolean overlayFavoured(Guild guild) {
        IncomePreviewContext context = current();
        if (context == null || context.favourGuild != guild) {
            return null;
        }
        return context.favoured;
    }

    public static Boolean overlayRepressed(Guild guild) {
        IncomePreviewContext context = current();
        if (context == null || context.favourGuild != guild) {
            return null;
        }
        return context.repressed;
    }

    public static TradeBreakdown scratchBreakdown(Guild guild) {
        IncomePreviewContext context = current();
        if (context == null || !context.scratch || guild == null) {
            return null;
        }
        String id = guild.getId() == null ? "guild-" + System.identityHashCode(guild) : guild.getId();
        return context.breakdowns.computeIfAbsent(id, ignored -> new TradeBreakdown());
    }

    public static boolean overridesTrade(DiplomacyHandler handler, String otherId) {
        IncomePreviewContext context = current();
        return context != null && context.tradeOverride(handler, otherId) != TradeLookup.ABSENT;
    }

    public static RelationType overlayTrade(DiplomacyHandler handler, String otherId) {
        IncomePreviewContext context = current();
        if (context == null) {
            return null;
        }
        TradeLookup lookup = context.tradeOverride(handler, otherId);
        return lookup == TradeLookup.ABSENT ? null : lookup.relation;
    }

    public double adjustTax(Faction owner, TaxHandler handler, TaxTarget target, String id, double rate) {
        if (owner == null) {
            return rate;
        }
        if (taxRate != null && faction == owner && replaces(handler, target, id)) {
            return taxRate;
        }
        if (law != null && faction == owner) {
            return clampForLaw(target, rate);
        }
        return rate;
    }

    public boolean affects(Faction owner) {
        return owner != null && faction == owner && (law != null || taxRate != null);
    }

    private boolean replaces(TaxHandler handler, TaxTarget query, String id) {
        if (taxTarget == null || query == null) {
            return false;
        }
        TaxTarget family = family(taxTarget);
        TaxTarget queryFamily = family(query);
        if (family != queryFamily) {
            return false;
        }
        if (taxTarget == TaxTarget.GUILD_ID || taxTarget == TaxTarget.VASSAL_ID || taxTarget == TaxTarget.TARIFF_ID) {
            return id != null && id.equalsIgnoreCase(taxId);
        }
        if (query == TaxTarget.GUILD_ID || query == TaxTarget.VASSAL_ID || query == TaxTarget.TARIFF_ID
                || (id != null && handler.hasSpecificTax(family, id))) {
            return !handler.hasSpecificTax(family, id);
        }
        return true;
    }

    private double clampForLaw(TaxTarget target, double rate) {
        LawEffect effect = law.getScopedEffects().get(Scope.FACTION);
        if (effect == null) {
            return rate;
        }
        TaxTarget family = family(target);
        Rules rule = ruleFor(family);
        if (rule != null && effect.hasRules() && Boolean.FALSE.equals(effect.getRules().get(rule))) {
            return 0.0;
        }
        if (effect.hasBrackets()) {
            for (Map.Entry<Brackets, Bracket> entry : effect.getBrackets().entrySet()) {
                if (BracketToTaxTarget.convert(entry.getKey()) == family && entry.getValue() != null) {
                    rate = clamp(rate, entry.getValue());
                }
            }
        }
        return rate;
    }

    private TradeLookup tradeOverride(DiplomacyHandler handler, String otherId) {
        if (handler == null || otherId == null || originHandler == null) {
            return TradeLookup.ABSENT;
        }
        if (handler == originHandler && otherId.equalsIgnoreCase(targetId)) {
            return TradeLookup.of(originRelation);
        }
        if (overrideTargetRelation && handler == targetHandler && otherId.equalsIgnoreCase(originId)) {
            return TradeLookup.of(targetRelation);
        }
        return TradeLookup.ABSENT;
    }

    private static double clamp(double value, Bracket bracket) {
        if (value < bracket.getMin()) {
            return bracket.getMin();
        }
        if (value > bracket.getMax()) {
            return bracket.getMax();
        }
        return value;
    }

    private static TaxTarget family(TaxTarget target) {
        return switch (target) {
            case GUILD_ID -> TaxTarget.GUILDS;
            case VASSAL_ID -> TaxTarget.VASSALS;
            case TARIFF_ID -> TaxTarget.TARIFFS;
            default -> target;
        };
    }

    private static Rules ruleFor(TaxTarget target) {
        return switch (target) {
            case CITIZENS -> Rules.CITIZEN_TAX;
            case GUILDS, GUILD_ID -> Rules.GUILD_TAX;
            case VASSALS, VASSAL_ID -> Rules.VASSAL_TAX;
            case DIVIDENDS -> Rules.DIVIDEND_TAX;
            case TARIFFS, TARIFF_ID -> Rules.TARIFFS;
            default -> null;
        };
    }

    private static final class TradeLookup {
        private static final TradeLookup ABSENT = new TradeLookup(null);

        private final RelationType relation;

        private TradeLookup(RelationType relation) {
            this.relation = relation;
        }

        private static TradeLookup of(RelationType relation) {
            return new TradeLookup(relation);
        }
    }
}
