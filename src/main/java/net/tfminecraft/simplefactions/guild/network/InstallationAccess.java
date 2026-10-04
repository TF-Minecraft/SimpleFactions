package net.tfminecraft.simplefactions.guild.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawEffect;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;

public final class InstallationAccess {
    private record Pair(Faction guild, Faction owner) {}

    private static final ThreadLocal<Map<Pair, Double>> RECALCULATION_CACHE = new ThreadLocal<>();

    private InstallationAccess() {}

    public static double of(Faction guildFaction, Faction ownerFaction) {
        Map<Pair, Double> cache = RECALCULATION_CACHE.get();
        if (cache != null) {
            return cache.computeIfAbsent(
                    new Pair(guildFaction, ownerFaction),
                    ignored -> calculate(guildFaction, ownerFaction));
        }
        return calculate(guildFaction, ownerFaction);
    }

    public static void beginRecalculation() {
        RECALCULATION_CACHE.set(new HashMap<>());
    }

    public static void endRecalculation() {
        RECALCULATION_CACHE.remove();
    }

    private static double calculate(Faction guildFaction, Faction ownerFaction) {
        if (ownerFaction == null) return 1;
        if (guildFaction == null) return 0;
        Faction guildRealm = topRealm(guildFaction);
        Faction ownerRealm = topRealm(ownerFaction);
        if (sameFaction(guildRealm, ownerRealm)) return 1;
        if (guildRealm == null || ownerRealm == null || blocked(guildRealm, ownerRealm)
                || blocked(ownerRealm, guildRealm) || WarManager.existsHostile(guildRealm, ownerRealm)) {
            return 0;
        }

        RelationType relation = guildRealm.getDiplomacyHandler() == null ? null
                : guildRealm.getDiplomacyHandler().getTradeRelation(ownerRealm.getId());
        double agreement = relation == null ? 0 : relation.getInstallationAccess();
        double grant = amount(economyLaw(ownerRealm), Scope.FOREIGN_GUILDS, Region.OUR_TERRITORY);
        double law = grant <= 0 ? 0 : grant
                + amount(economyLaw(guildRealm), Scope.DOMESTIC_GUILDS, Region.FOREIGN_TERRITORY);
        return clamp(Math.max(agreement, law));
    }

    public static Faction topRealm(Faction faction) {
        Set<Faction> seen = new HashSet<>();
        Faction current = faction;
        while (current != null) {
            if (!seen.add(current)) return faction;
            Faction overlord = current.getOverlord();
            if (overlord == null) return current;
            current = overlord;
        }
        return null;
    }

    public static double amount(Law law, Scope scope, Region region) {
        if (law == null) return 0;
        LawEffect effect = law.getScopedEffects().get(scope);
        Double explicit = effect == null ? null
                : effect.getModifierAmount(FactionModifiers.INSTALLATION_ACCESS, region);
        if (explicit != null) return explicit;
        boolean grant = scope == Scope.FOREIGN_GUILDS && region == Region.OUR_TERRITORY;
        boolean reach = scope == Scope.DOMESTIC_GUILDS && region == Region.FOREIGN_TERRITORY;
        if (!grant && !reach) return 0;
        return switch (law.getId()) {
            case "free_trade" -> grant ? 0.50 : 0.10;
            case "decentralized" -> grant ? 0.35 : 0.00;
            case "mercantilism" -> grant ? 0.15 : 0.20;
            case "protectionism" -> grant ? 0.10 : 0.00;
            case "isolationism" -> grant ? 0.00 : -0.25;
            default -> 0;
        };
    }

    private static boolean blocked(Faction from, Faction to) {
        if (from.getDiplomacyHandler() == null || to.getId() == null) return false;
        RelationType relation = from.getDiplomacyHandler().getTradeRelation(to.getId());
        return relation != null && relation.blocksInstallations();
    }

    private static Law economyLaw(Faction faction) {
        if (faction == null || faction.getLawHandler() == null) return null;
        LawGroup group = faction.getLawHandler().getGroup("economy");
        if (group == null) return null;
        IncomePreviewContext context = IncomePreviewContext.current();
        Law overlay = context != null && context.previewsLaw(faction)
                ? IncomePreviewContext.overlayLaw(group) : null;
        return overlay != null ? overlay : group.getCurrent();
    }

    private static boolean sameFaction(Faction first, Faction second) {
        return first == second || (first != null && second != null && first.getId() != null
                && second.getId() != null && first.getId().equalsIgnoreCase(second.getId()));
    }

    private static double clamp(double amount) {
        return Math.max(0, Math.min(1, amount));
    }
}
