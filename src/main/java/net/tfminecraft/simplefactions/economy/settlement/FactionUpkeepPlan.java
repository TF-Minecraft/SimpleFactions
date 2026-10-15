package net.tfminecraft.simplefactions.economy.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.InstallationConstructionData;
import net.tfminecraft.simplefactions.database.InstallationData;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.economy.Money;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;

/** The pre-income army and installation decisions, calculated without changing the faction. */
public record FactionUpkeepPlan(String faction, Map<String, Integer> regimentSlots,
        List<InstallationData> installations, InstallationConstructionData construction,
        long charge, double armyLedgerCost, double installationLedgerCost) {

    public static FactionUpkeepPlan calculate(Faction faction) {
        if (net.tfminecraft.simplefactions.guild.HeldRealms.isHeld(faction)) return paused(faction);
        List<Regiment> regiments = new ArrayList<>();
        if (faction.getMilitary() != null) {
            for (Regiment regiment : faction.getMilitary().getRegiments()) {
                if (regiment != null) regiments.add(new Regiment(regiment));
            }
        }
        boolean bank = SettlementGuilds.capital(faction).getBank() != null;
        double wealth = bank ? SettlementGuilds.capital(faction).getBank().getWealth() : 0;
        double factor = 1 + faction.getModifier(FactionModifiers.MILITARY_UPKEEP).getAmount() / 100;
        double army = armyCost(regiments, factor);
        if (!bank && army > 0) {
            for (Regiment regiment : regiments) {
                if (!regiment.isLevy()) regiment.setCurrentSlots(Math.min(regiment.getCurrentSlots(), regiment.getFreeSlots()));
            }
            army = 0;
        }
        int guard = 0;
        while (army > 0 && wealth < army && guard++ < 100000) {
            Regiment shrink = regiments.stream().filter(r -> !r.isLevy() && r.getCurrentSlots() > r.getFreeSlots()).findFirst().orElse(null);
            if (shrink == null) break;
            shrink.sizeDecrease();
            army = armyCost(regiments, factor);
        }
        long charge = bank && army > 0 ? Money.cents(army) : 0;
        wealth -= Money.denars(charge);
        Map<String, Integer> slots = new LinkedHashMap<>();
        for (Regiment regiment : regiments) slots.put(regiment.getId(), regiment.getCurrentSlots());
        List<Installation> sorted = new ArrayList<>(faction.getInstallationHandler().getAll());
        sorted.sort(Comparator.comparingDouble((Installation i) -> InstallationConfigLoader.getDailyUpkeep(i.getKind(), i.getLevel()))
                .thenComparingLong(Installation::getCompletedAt).thenComparing(Installation::getId));
        List<InstallationData> installations = new ArrayList<>();
        InstallationConstructionData construction = faction.getInstallationHandler().serializeConstruction();
        double ledgerCost = 0;
        for (Installation current : sorted) {
            InstallationData data = current.toData();
            double upkeep = InstallationConfigLoader.getDailyUpkeep(current.getKind(), current.getLevel());
            if (upkeep > 0 && (!bank || wealth < upkeep)) {
                if (construction != null && Boolean.TRUE.equals(construction.upgrade) && current.getId().equals(construction.id)) construction = null;
                data.level--;
            } else if (upkeep > 0) {
                long paid = Money.cents(upkeep);
                charge = Math.addExact(charge, paid);
                wealth -= Money.denars(paid);
            }
            if (data.level > 0) {
                installations.add(data);
                ledgerCost += InstallationConfigLoader.getDailyUpkeep(current.getKind(), data.level);
            }
        }
        return new FactionUpkeepPlan(faction.getId(), slots, installations, construction, charge, army, ledgerCost);
    }

    /** A held realm pays nothing and keeps its army and installations as they are. */
    private static FactionUpkeepPlan paused(Faction faction) {
        Map<String, Integer> slots = new LinkedHashMap<>();
        if (faction.getMilitary() != null) {
            for (Regiment regiment : faction.getMilitary().getRegiments()) {
                if (regiment != null) slots.put(regiment.getId(), regiment.getCurrentSlots());
            }
        }
        List<InstallationData> installations = faction.getInstallationHandler().getAll().stream()
                .sorted(Comparator.comparing(Installation::getId)).map(Installation::toData).toList();
        return new FactionUpkeepPlan(faction.getId(), slots, new ArrayList<>(installations),
                faction.getInstallationHandler().serializeConstruction(), 0, 0, 0);
    }

    private static double armyCost(List<Regiment> regiments, double factor) {
        double raw = 0;
        for (Regiment regiment : regiments) raw += regiment.getTotalUpkeep();
        return Formatter.formatDouble(Formatter.formatDouble(raw) * factor);
    }
}
