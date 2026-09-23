package net.tfminecraft.simplefactions.guild.income;

import java.util.EnumMap;
import java.util.Map;

import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.branch.BranchModifier;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Branch up/down income delta on a private province copy. Live branch level,
 * the shared province snapshot, and guild trade breakdowns stay untouched.
 */
public final class BranchIncomePreview {
    private BranchIncomePreview() {}

    public static final class Prepared {
        private final ProvinceManager snapshot;

        private Prepared(ProvinceManager snapshot) {
            this.snapshot = snapshot;
        }
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
        int level = branch.getLevel();
        Map<GuildModifier, Double> current = modifiers(guild);
        return estimate(
                prepared,
                guild,
                current,
                adjust(current, branch, level, levelDelta),
                taxFraction(guild));
    }

    public static double estimate(
            Prepared prepared,
            Guild guild,
            Map<GuildModifier, Double> current,
            Map<GuildModifier, Double> hypothetical,
            double taxFraction) {
        ProvinceManager before = copy(prepared.snapshot);
        ProvinceManager after = copy(prepared.snapshot);
        double baseline = income(before, guild, current);
        double changed = income(after, guild, hypothetical);
        double net = (changed - baseline) * (1.0 - taxFraction);
        return Math.round(net * 100.0) / 100.0;
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

    public static double taxFraction(Guild guild) {
        Faction faction = guild.getFaction();
        if (faction == null) {
            return 0.0;
        }
        return faction.getTaxRate(TaxTarget.GUILDS, guild.getId(), true) / 100.0;
    }

    private static ProvinceManager copy(ProvinceManager source) {
        ProvinceManager snapshot = source.createSnapshotShell();
        snapshot.copyAllDataFrom(source);
        return snapshot;
    }

    private static double income(ProvinceManager snapshot, Guild guild, Map<GuildModifier, Double> amounts) {
        GuildModifierOverride.use(guild, amounts);
        try {
            snapshot.recalculateForSingleGuild(guild, false);
            return snapshot.getIncome(guild, false);
        } finally {
            GuildModifierOverride.clear();
        }
    }
}
