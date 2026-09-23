package net.tfminecraft.simplefactions.guild.income;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.objects.Faction;

/** Income deltas for laws, favour, treaties, and taxes. Live faction state is not changed. */
public final class EconomicPreview {
    private static Prepared shared;
    private static int sharedTick = Integer.MIN_VALUE;

    private EconomicPreview() {}

    public static final class Prepared {
        private final ProvinceManager snapshot;

        private Prepared(ProvinceManager snapshot) {
            this.snapshot = snapshot;
        }
    }

    /** One province copy per server tick, shared by every preview opened from that tick's menus. */
    public static Prepared current() {
        int tick = Bukkit.getCurrentTick();
        if (shared == null || sharedTick != tick) {
            shared = prepare(SimpleFactions.getInstance().getProvinceManager());
            sharedTick = tick;
        }
        return shared;
    }

    public static Prepared prepare(ProvinceManager live) {
        return new Prepared(copy(live));
    }

    public static ProvinceManager copyOf(Prepared prepared) {
        return copy(prepared.snapshot);
    }

    public static Map<Guild, Double> law(ProvinceManager live, Faction faction, LawGroup group, Law law) {
        return law(prepare(live), faction, group, law);
    }

    public static Map<Guild, Double> law(Prepared prepared, Faction faction, LawGroup group, Law law) {
        ProvinceManager snapshot = copy(prepared.snapshot);
        Map<Guild, Double> before = project(snapshot, IncomePreviewContext.scratch(), true);
        Map<Guild, Double> after = project(snapshot, IncomePreviewContext.law(faction, group, law), true);
        return diff(before, after);
    }

    public static Map<Guild, Double> favour(ProvinceManager live, Guild guild, boolean favour) {
        return favour(prepare(live), guild, favour);
    }

    public static Map<Guild, Double> favour(Prepared prepared, Guild guild, boolean favour) {
        ProvinceManager snapshot = copy(prepared.snapshot);
        Map<Guild, Double> before = project(snapshot, IncomePreviewContext.scratch(), true);
        Map<Guild, Double> after = project(snapshot, IncomePreviewContext.favour(guild, favour), true);
        return diff(before, after);
    }

    public static Map<Guild, Double> trade(
            ProvinceManager live, Faction origin, Faction target, RelationType agreement) {
        return trade(prepare(live), origin, target, agreement);
    }

    public static Map<Guild, Double> trade(
            Prepared prepared, Faction origin, Faction target, RelationType agreement) {
        ProvinceManager snapshot = copy(prepared.snapshot);
        Map<Guild, Double> before = project(snapshot, IncomePreviewContext.scratch(), true);
        Map<Guild, Double> after = project(snapshot, IncomePreviewContext.trade(origin, target, agreement), true);
        return diff(before, after);
    }

    public static Map<Guild, Double> tax(Faction faction, TaxTarget target, String id, double rate) {
        Map<Guild, Double> before = nets(false);
        IncomePreviewContext.open(IncomePreviewContext.tax(faction, target, id, rate));
        try {
            return diff(before, nets(false));
        } finally {
            IncomePreviewContext.clear();
        }
    }

    private static Map<Guild, Double> project(
            ProvinceManager snapshot, IncomePreviewContext context, boolean capitalsOnly) {
        IncomePreviewContext.open(context);
        try {
            snapshot.recalculate();
            return nets(capitalsOnly);
        } finally {
            IncomePreviewContext.clear();
        }
    }

    private static Map<Guild, Double> nets(boolean capitalsOnly) {
        Map<Guild, Double> nets = new HashMap<>();
        for (Guild guild : FactionManager.getAllGuilds()) {
            if (guild == null || guild.getLedger() == null) {
                continue;
            }
            if (capitalsOnly && !guild.hasCapital()) {
                continue;
            }
            nets.put(guild, guild.getLedger().getNetIncome());
        }
        return nets;
    }

    private static Map<Guild, Double> diff(Map<Guild, Double> before, Map<Guild, Double> after) {
        Map<Guild, Double> deltas = new HashMap<>();
        for (Map.Entry<Guild, Double> entry : before.entrySet()) {
            double next = after.getOrDefault(entry.getKey(), entry.getValue());
            deltas.put(entry.getKey(), Math.round((next - entry.getValue()) * 100.0) / 100.0);
        }
        return deltas;
    }

    private static ProvinceManager copy(ProvinceManager source) {
        ProvinceManager snapshot = source.createSnapshotShell();
        snapshot.copyAllDataFrom(source);
        return snapshot;
    }
}
