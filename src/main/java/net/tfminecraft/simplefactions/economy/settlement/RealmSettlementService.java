package net.tfminecraft.simplefactions.economy.settlement;

import java.io.IOException;
import java.nio.file.Path;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.identity.death.CharacterDeaths;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleSettlement;

/** Coordinates one prepared day and its immutable, realm-wide monetary settlement. */
public final class RealmSettlementService {
    private final DailyPreparationJournal preparation;
    private final SettlementJournal settlements;
    private final RealmSettlementEffects effects;

    public RealmSettlementService(Path folder, Database database) {
        preparation = new DailyPreparationJournal(folder.resolve("day-preparation"), database);
        settlements = new SettlementJournal(folder.resolve("settlements"));
        effects = new RealmSettlementEffects(folder, database, PlayerEconomyManager.get(),
                CharacterDeaths::applyPayments, VehicleSettlement::apply);
    }

    public void recover() throws IOException {
        settlements.recover(effects);
        for (long day : preparation.unfinishedDays()) settle(day);
    }

    public void settle(long day) throws IOException {
        var prepared = preparation.prepare(day, System.currentTimeMillis());
        Result result = settlements.settle(day, () -> {
            net.tfminecraft.simplefactions.inactivity.InactivityService.armAll(FactionManager.factions, prepared.timestamp());
            net.tfminecraft.simplefactions.guild.network.TradeGraph.forgetRoutes();
            SimpleFactions plugin = SimpleFactions.getInstance();
            if (!plugin.refreshTrackProvinces()) plugin.getProvinceManager().recalculate();
            var claims = CharacterDeaths.pendingPayments().stream().map(payment -> new RealmSettlementCalculation.Claim(
                    payment.id(), payment.guildIncarnation(), payment.characterId(), payment.cents(), payment.kind())).toList();
            return new RealmSettlementCalculation(day, prepared.timestamp(), FactionManager.getAllGuilds(), claims)
                    .withPreparedUpkeep(prepared.upkeep()).calculate((balances, legs, changes) -> VehicleSettlement.calculate(balances, legs, changes, prepared.timestamp()));
        }, effects);
        if (result != Result.APPLIED) throw new IOException("Realm settlement " + day + " remains " + result);
        preparation.complete(day);
        FactionManager.day = Math.max(FactionManager.day, Math.toIntExact(day + 1));
        FactionManager.timer = 0;
    }
}
