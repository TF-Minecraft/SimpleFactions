package net.tfminecraft.simplefactions.economy.settlement;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.TimerData;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;

/** Applies saved after-values only after the complete realm posting is APPLIED. */
public final class RealmSettlementEffects implements SettlementJournal.Effects {
    @FunctionalInterface public interface Payments { void apply(String id, Map<String, Long> paid) throws IOException; }
    @FunctionalInterface public interface Vehicles { void apply(JsonObject changes) throws IOException; }
    private final Path folder;
    private final Database database;
    private final PlayerEconomyManager economy;
    private final Payments deaths;
    private final Vehicles vehicles;

    public RealmSettlementEffects(Path folder, Database database, PlayerEconomyManager economy, Payments deaths, Vehicles vehicles) {
        this.folder = folder;
        this.database = database;
        this.economy = economy;
        this.deaths = deaths;
        this.vehicles = vehicles;
    }

    @Override public void apply(SettlementJournal.Plan plan) throws IOException {
        RealmSettlementChanges changes = JsonUtil.GSON.fromJson(plan.changes(), RealmSettlementChanges.class);
        // Validate every incarnation before changing anything, including on a mixed-backup restore.
        for (var saved : changes.guilds) {
            Guild guild = FactionManager.getGuildByString(saved.id);
            if (guild == null || !guild.getIncarnationId().equals(saved.incarnation))
                throw new IOException("Settlement guild missing or replaced: " + saved.id);
        }
        Set<Faction> save = new LinkedHashSet<>();
        java.util.List<Runnable> notices = new java.util.ArrayList<>();
        for (FactionUpkeepPlan saved : changes.upkeep) {
            Faction faction = FactionManager.getByString(saved.faction());
            if (faction == null) throw new IOException("Settlement faction missing: " + saved.faction());
            if (faction.getMilitary() == null && !saved.regimentSlots().isEmpty()) throw new IOException("Settlement military missing: " + saved.faction());
            if (faction.getMilitary() != null) {
                for (var regiment : saved.regimentSlots().entrySet()) {
                    var current = faction.getMilitary().getRegiment(regiment.getKey());
                    if (current == null) throw new IOException("Settlement regiment missing: " + regiment.getKey());
                    current.setCurrentSlots(regiment.getValue());
                }
            }
            for (var current : faction.getInstallationHandler().getAll()) {
                var after = saved.installations().stream().filter(i -> i.id.equals(current.getId())).findFirst().orElse(null);
                if (after == null || after.level < current.getLevel()) {
                    String text = "§c" + current.getKind().getDisplayName() + " §f" + current.getName()
                            + (after == null ? " §chas been destroyed" : " §cdropped to level " + after.level)
                            + " §7(unable to pay upkeep)";
                    notices.add(() -> {
                        var leader = net.tfminecraft.simplefactions.identity.Members.player(faction.getLeader());
                        if (leader != null) leader.sendMessage(text);
                    });
                }
            }
            faction.getInstallationHandler().restoreSettlement(saved.installations(), saved.construction());
            save.add(faction);
        }
        for (var saved : changes.guilds) {
            Guild guild = FactionManager.getGuildByString(saved.id);
            guild.setDonationAmount(saved.donation);
            guild.getLedger().setCitizenTaxes(saved.citizenTaxes);
            guild.getLedger().setCasinoProfit(saved.casinoProfit);
            guild.getLedger().setVehicleFeeIncome(saved.vehicleFeeIncome);
            guild.getLoanHandler().restoreCreditScore(saved.creditScore);
            for (var loan : saved.loans) {
                var current = guild.getLoanHandler().getLoanById(loan.id);
                if (current == null) throw new IOException("Settlement loan missing: " + loan.id);
                current.restoreSettlement(loan);
            }
            if (saved.company != null) {
                if (guild.getCompany() == null) throw new IOException("Settlement company missing: " + saved.id);
                guild.getCompany().restoreSettlement(saved.company);
            }
            for (var branch : saved.branches.entrySet()) {
                var current = guild.getBranch(branch.getKey());
                if (current == null) throw new IOException("Settlement branch missing: " + branch.getKey());
                current.restoreLevel(branch.getValue());
            }
            guild.restoreDividendEligibility(saved.dividendEligible);
            guild.getLedger().getHistory().load(saved.ledgerLastDay, saved.ledgerLifetime, Map.of());
            guild.updateWealth();
            save.add(guild.getFaction());
        }
        for (var entry : changes.reparations.entrySet()) {
            Faction faction = FactionManager.getByString(entry.getKey());
            if (faction == null) throw new IOException("Settlement reparations payer missing: " + entry.getKey());
            faction.getWarReparationsObligations().clear();
            faction.getWarReparationsObligations().addAll(entry.getValue());
            save.add(faction);
        }
        var saves = new DurableJson.Batch();
        for (Faction faction : save) {
            if (!database.saveFactionDurably(faction, saves)) throw new IOException("Cannot persist settlement for " + faction.getId());
        }
        // Every faction is on disk before vehicles, death payments, the clock or the journal move on.
        saves.commit();
        vehicles.apply(changes.vehicleChanges);
        deaths.apply("sf:settle:" + plan.day(), changes.deathPayments);
        economy.clearAllDaily();
        changes.playerIncome.forEach((character, flows) -> flows.forEach((source, amount) ->
                economy.getLedger(character).add(PlayerCashflow.valueOf(source), amount)));
        TimerData timer = new TimerData();
        timer.day = Math.toIntExact(plan.day() + 1);
        DurableJson.write(folder.resolve("Cache/data.json"), timer);
        FactionManager.day = Math.max(FactionManager.day, timer.day);
        FactionManager.timer = 0;
        notices.forEach(Runnable::run);
    }
}
