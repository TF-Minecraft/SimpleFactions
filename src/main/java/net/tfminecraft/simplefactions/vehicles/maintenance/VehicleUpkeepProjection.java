package net.tfminecraft.simplefactions.vehicles.maintenance;


import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import java.util.UUID;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.player.income.PlayerLedger;
import net.tfminecraft.vehicleframework.data.OwnedVehicleSummary;

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
