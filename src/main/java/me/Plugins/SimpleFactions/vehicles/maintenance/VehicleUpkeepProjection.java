package me.Plugins.SimpleFactions.vehicles.maintenance;


import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.VehicleOwnershipQueries;
import java.util.UUID;

import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.player.income.PlayerCashflow;
import me.Plugins.SimpleFactions.player.income.PlayerLedger;
import net.tfminecraft.VehicleFramework.Data.OwnedVehicleSummary;

public final class VehicleUpkeepProjection {
    private VehicleUpkeepProjection() {}

    public static double projectedDailyUpkeep(UUID playerUuid) {
        if (playerUuid == null) {
            return 0.0;
        }
        String playerName = VehicleOwnershipQueries.resolvePlayerName(playerUuid);
        return projectedDailyUpkeep(playerName, SimpleFactions.getVehicleRegistry());
    }

    public static double projectedDailyUpkeep(String playerName, PlayerVehicleRegistry registry) {
        if (playerName == null || playerName.isBlank()) {
            return 0.0;
        }
        double total = 0.0;
        for (OwnedVehicleSummary vehicle :
                VehicleOwnershipQueries.personalVehicles(playerName, registry)) {
            total += VehiclesConfigLoader.getUpkeep(vehicle.getTypeId());
        }
        return total;
    }

    public static double displayVehicleExpense(PlayerLedger ledger, UUID playerUuid) {
        if (ledger == null) {
            return 0.0;
        }
        double settled = ledger.getAmount(PlayerCashflow.VEHICLE_UPKEEP);
        if (settled != 0.0) {
            return settled;
        }
        double projected = projectedDailyUpkeep(playerUuid);
        return projected > 0.0 ? -projected : 0.0;
    }

    public static double displayNetDaily(PlayerLedger ledger, UUID playerUuid) {
        if (ledger == null) {
            return 0.0;
        }
        double net = ledger.getNetDaily();
        double settledVehicle = ledger.getAmount(PlayerCashflow.VEHICLE_UPKEEP);
        double displayVehicle = displayVehicleExpense(ledger, playerUuid);
        if (settledVehicle == 0.0 && displayVehicle < 0.0) {
            net += displayVehicle;
        }
        return net;
    }
}
