package net.tfminecraft.simplefactions.guild.income;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.tfminecraft.simplefactions.economy.FrozenReads;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.branch.BranchModifier;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Branch up/down income delta on a private province copy. Live branch level,
 * the shared province snapshot, and guild trade breakdowns stay untouched.
 *
 * <p>Changes are settled the way the daily ledger settles them: guild tax on gross trade,
 * then trade upkeep and tariffs. The realm guild pays no guild tax.
 */
public final class BranchIncomePreview {
    private BranchIncomePreview() {}

    public static final class Prepared {
        private final ProvinceManager snapshot;
        private final FrozenReads reads = FrozenReads.capture();
        private final long inputs = TradeInputs.fingerprint();
        private final Map<Object, Estimate> estimates = new ConcurrentHashMap<>();

        private Prepared(ProvinceManager snapshot) {
            this.snapshot = snapshot;
        }

        /** Main-thread state as it was when this copy was made, for work off the main thread. */
        public FrozenReads reads() {
            return reads;
        }

        /** An estimate already computed on this copy for the same guild and modifiers, or null. */
        public Estimate cached(Guild guild, Map<GuildModifier, Double> current, Map<GuildModifier, Double> hypothetical) {
            return estimates.get(key(guild, current, hypothetical));
        }

        public void remember(
                Guild guild,
                Map<GuildModifier, Double> current,
                Map<GuildModifier, Double> hypothetical,
                Estimate estimate) {
            if (estimate != null && estimate != Estimate.UNAVAILABLE) {
                estimates.put(key(guild, current, hypothetical), estimate);
            }
        }

        private static Object key(Guild guild, Map<GuildModifier, Double> current, Map<GuildModifier, Double> hypothetical) {
            return List.of(guild.getId(), guild.getIncarnationId(), Map.copyOf(current), Map.copyOf(hypothetical));
        }
    }

    /** The guild's trade on a copy with its current modifiers, shared by every hypothetical change. */
    public static final class Baseline {
        private final Prepared prepared;
        private final Guild guild;
        private final List<Guild> guilds;
        private final Map<Guild, TradeIncome> before;

        private Baseline(Prepared prepared, Guild guild, List<Guild> guilds, Map<Guild, TradeIncome> before) {
            this.prepared = prepared;
            this.guild = guild;
            this.guilds = guilds;
            this.before = before;
        }
    }

    /**
     * Daily net change for the guild that changes its branch, and for its whole faction: every
     * guild of the faction plus the treasury's guild tax and tariffs. For the realm guild the
     * treasury is its own bank, so its own change already includes the treasury.
     */
    public record Estimate(double own, double realm) {
        public static final Estimate UNAVAILABLE = new Estimate(Double.NaN, Double.NaN);
    }

    /** False once tariffs, guild tax or realms changed after this copy was made. Main thread. */
    public static boolean current(Prepared prepared) {
        return prepared.inputs == TradeInputs.fingerprint();
    }

    /** Copy province trade data once. Later estimates only read this copy. */
    public static Prepared prepare(ProvinceManager live) {
        ProvinceManager snapshot = live.createSnapshotShell();
        snapshot.copyAllDataFrom(live);
        return new Prepared(snapshot);
    }

    public static double estimate(ProvinceManager live, Guild guild, Branch branch, int levelDelta) {
        return estimate(prepare(live), guild, branch, levelDelta);
    }

    public static double estimate(Prepared prepared, Guild guild, Branch branch, int levelDelta) {
        Map<GuildModifier, Double> current = modifiers(guild);
        return estimate(prepared, guild, current, adjust(current, branch, branch.getLevel(), levelDelta)).own();
    }

    public static Estimate estimate(
            Prepared prepared,
            Guild guild,
            Map<GuildModifier, Double> current,
            Map<GuildModifier, Double> hypothetical) {
        return estimate(baseline(prepared, guild, current), hypothetical);
    }

    public static Baseline baseline(Prepared prepared, Guild guild, Map<GuildModifier, Double> current) {
        List<Guild> guilds = measured(guild);
        return new Baseline(prepared, guild, guilds, incomes(copy(prepared.snapshot), guild, current, guilds));
    }

    public static Estimate estimate(Baseline baseline, Map<GuildModifier, Double> hypothetical) {
        Guild guild = baseline.guild;
        List<Guild> guilds = baseline.guilds;
        Map<Guild, TradeIncome> before = baseline.before;
        Map<Guild, TradeIncome> after = incomes(copy(baseline.prepared.snapshot), guild, hypothetical, guilds);
        Faction faction = guild.getFaction();
        String factionId = faction == null ? null : faction.getId();

        double own = 0;
        double members = 0;
        double treasury = 0;
        for (Guild other : guilds) {
            TradeIncome was = before.get(other);
            TradeIncome now = after.get(other);
            treasury += now.tariffsPaidTo(factionId) - was.tariffsPaidTo(factionId);
            if (other != guild && (faction == null || other.getFaction() != faction)) {
                continue;
            }
            double gross = now.gross() - was.gross();
            double tax = taxFraction(other);
            double net = gross * (1.0 - tax) - (now.upkeep() - was.upkeep()) - (now.tariffs() - was.tariffs());
            treasury += gross * tax;
            members += net;
            if (other == guild) {
                own = net;
            }
        }
        if (guild.isBase()) {
            own += treasury;
        }
        return new Estimate(round(own), round(members + treasury));
    }

    public static Map<GuildModifier, Double> modifiers(Guild guild) {
        EnumMap<GuildModifier, Double> amounts = new EnumMap<>(GuildModifier.class);
        for (GuildModifier modifier : GuildModifier.values()) {
            amounts.put(modifier, guild.getModifier(modifier));
        }
        return amounts;
    }

    public static Map<GuildModifier, Double> adjust(
            Map<GuildModifier, Double> current,
            Branch branch,
            int level,
            int levelDelta) {
        int target = Math.max(0, level + levelDelta);
        EnumMap<GuildModifier, Double> next = new EnumMap<>(GuildModifier.class);
        for (GuildModifier modifier : GuildModifier.values()) {
            double amount = current.getOrDefault(modifier, 0.0);
            BranchModifier branchModifier = branch.getModifier(modifier);
            if (branchModifier != null) {
                amount += branchModifier.getCurrent(target) - branchModifier.getCurrent(level);
            }
            next.put(modifier, amount);
        }
        return next;
    }

    /** Share of gross trade the guild pays its faction. The realm guild pays none. */
    public static double taxFraction(Guild guild) {
        Faction faction = guild.getFaction();
        if (faction == null || guild.isBase()) {
            return 0.0;
        }
        return faction.getTaxRate(TaxTarget.GUILDS, guild.getId(), true) / 100.0;
    }

    /** The changing guild first, then every other guild that can trade, for tariffs and siblings. */
    private static List<Guild> measured(Guild guild) {
        Set<Guild> guilds = new LinkedHashSet<>();
        guilds.add(guild);
        for (Guild other : FactionManager.getAllGuilds()) {
            if (other != null && other.hasCapital()) {
                guilds.add(other);
            }
        }
        return new ArrayList<>(guilds);
    }

    private static ProvinceManager copy(ProvinceManager source) {
        ProvinceManager snapshot = source.createSnapshotShell();
        snapshot.copyAllDataFrom(source);
        return snapshot;
    }

    private static Map<Guild, TradeIncome> incomes(
            ProvinceManager snapshot,
            Guild guild,
            Map<GuildModifier, Double> amounts,
            List<Guild> guilds) {
        GuildModifierOverride.use(guild, amounts);
        try {
            snapshot.recalculateForSingleGuild(guild, false);
            Map<Guild, TradeIncome> incomes = new HashMap<>();
            for (Guild other : guilds) {
                incomes.put(other, snapshot.getTradeIncome(other));
            }
            return incomes;
        } finally {
            GuildModifierOverride.clear();
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
