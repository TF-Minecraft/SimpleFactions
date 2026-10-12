package net.tfminecraft.simplefactions.vehicles.maintenance;


import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import java.util.UUID;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.player.income.PlayerLedger;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService;
import net.tfminecraft.vehicleframework.data.OwnedVehicleSummary;

public final class VehicleUpkeepProjection {
    private VehicleUpkeepProjection() {}

    public static double projectedDailyUpkeep(UUID playerUuid) {
        if (playerUuid == null) {
            return 0.0;
        }
        return projected(playerUuid, SimpleFactions.getVehicleRegistry(), false);
    }

    public static double projectedDailyUpkeep(String playerName, PlayerVehicleRegistry registry) {
        return projected(character(playerName), registry, false);
    }

    public static double projectedDailyTax(String playerName, PlayerVehicleRegistry registry) {
        return projected(character(playerName), registry, true);
    }

    private static UUID character(String name) {
        UUID character = Members.characterId(name);
        if (character != null) return character;
        if (name == null || name.isBlank() || org.bukkit.Bukkit.getServer() == null) return null;
        return Members.session(org.bukkit.Bukkit.getPlayerExact(name)).map(session -> session.ref().characterId()).orElse(null);
    }

    private static double projected(UUID character, PlayerVehicleRegistry registry, boolean tax) {
        if (character == null || registry == null) return 0;
        double total = 0;
        for (var vehicle : VehicleOwnershipQueries.allPersonalVehicles(registry)) {
            var row = registry.getByVehicleUuid(vehicle.getUuid()).orElse(null);
            if (row == null || !character.equals(row.getPlayerUuid())) continue;
            if (tax) {
                var quote = VehicleFeeService.quote(FeeKind.VEHICLE_TAX, Members.key(character), vehicle.getTypeId());
                if (quote != null) total += quote.amount();
            } else total += VehiclesConfigLoader.getUpkeep(vehicle.getTypeId());
        }
        return Formatter.formatDouble(total);
    }

    /** Settled tax once the day has charged it, otherwise the projected tax. */
    public static double displayVehicleTax(PlayerLedger ledger, UUID playerUuid) {
        if (ledger == null) {
            return 0.0;
        }
        double settled = ledger.getAmount(PlayerCashflow.VEHICLE_TAX);
        if (settled != 0.0 || ledger.getAmount(PlayerCashflow.VEHICLE_UPKEEP) != 0.0 || playerUuid == null) {
            return settled;
        }
        double projected = projected(playerUuid, SimpleFactions.getVehicleRegistry(), true);
        return projected > 0.0 ? -projected : 0.0;
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
        double settledTax = ledger.getAmount(PlayerCashflow.VEHICLE_TAX);
        double displayTax = displayVehicleTax(ledger, playerUuid);
        if (settledTax == 0.0 && displayTax < 0.0) {
            net += displayTax;
        }
        return net;
    }
}
