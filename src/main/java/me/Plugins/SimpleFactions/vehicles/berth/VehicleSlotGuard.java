package me.Plugins.SimpleFactions.vehicles.berth;


import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.VehicleOwnershipQueries;
import java.util.List;

import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import net.tfminecraft.VehicleFramework.Data.OwnedVehicleSummary;

public final class VehicleSlotGuard {
    private VehicleSlotGuard() {}

    public static CanBuildResult checkCanBuild(Player player, String vehicleTypeId) {
        if (player == null) {
            return CanBuildResult.UNKNOWN_TYPE;
        }
        return checkCanBuild(
                player.getName(),
                vehicleTypeId,
                me.Plugins.SimpleFactions.SimpleFactions.getVehicleRegistry());
    }

    public static CanBuildResult checkCanBuild(
            String playerName, String vehicleTypeId, PlayerVehicleRegistry registry) {
        if (playerName == null || playerName.isBlank() || registry == null) {
            return CanBuildResult.UNKNOWN_TYPE;
        }
        if (vehicleTypeId == null || vehicleTypeId.isEmpty()) {
            return CanBuildResult.UNKNOWN_TYPE;
        }
        if (!VehiclesConfigLoader.isKnownType(vehicleTypeId)) {
            return CanBuildResult.UNKNOWN_TYPE;
        }

        List<OwnedVehicleSummary> personal =
                VehicleOwnershipQueries.personalVehicles(playerName, registry);

        if (VehicleOwnershipQueries.countOfType(personal, vehicleTypeId)
                >= VehiclesConfigLoader.getPerPersonLimit(vehicleTypeId)) {
            return CanBuildResult.PER_TYPE_LIMIT;
        }

        if (VehiclesConfigLoader.ignoresPersonalSlotLimit(vehicleTypeId)) {
            return CanBuildResult.OK;
        }

        int totalLimit = VehiclesConfigLoader.getPersonalSlotLimit();
        if (totalLimit <= 0) {
            return CanBuildResult.OK;
        }

        if (VehicleOwnershipQueries.countExcludingIgnoreLimit(personal) >= totalLimit) {
            return CanBuildResult.TOTAL_LIMIT;
        }

        return CanBuildResult.OK;
    }

    public enum CanBuildResult {
        OK,
        TOTAL_LIMIT,
        PER_TYPE_LIMIT,
        UNKNOWN_TYPE
    }
}
