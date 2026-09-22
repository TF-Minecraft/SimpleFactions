package me.Plugins.SimpleFactions.vehicles.registry;


import me.Plugins.SimpleFactions.vehicles.berth.VehicleSlotGuard;
import me.Plugins.SimpleFactions.vehicles.berth.InstallationVehicleService.TryRegisterResult;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import net.tfminecraft.VehicleFramework.Vehicles.ActiveVehicle;

public final class VehicleRegistryClaimService {
    private final PlayerVehicleRegistry registry;

    public VehicleRegistryClaimService(PlayerVehicleRegistry registry) {
        this.registry = registry;
    }

    public interface VehicleClaimTarget {
        String getVehicleUuid();

        String getVehicleTypeId();
    }

    public record ClaimRegisterResult(TryRegisterResult status, CanBuildResult buildFailure) {
        public static ClaimRegisterResult skip() {
            return new ClaimRegisterResult(TryRegisterResult.SKIP, null);
        }

        public static ClaimRegisterResult allowed() {
            return new ClaimRegisterResult(TryRegisterResult.ALLOWED, null);
        }

        public static ClaimRegisterResult failUnknownType() {
            return new ClaimRegisterResult(TryRegisterResult.FAIL_UNKNOWN_TYPE, CanBuildResult.UNKNOWN_TYPE);
        }

        public static ClaimRegisterResult failSlotLimit(CanBuildResult buildFailure) {
            return new ClaimRegisterResult(TryRegisterResult.FAIL_SLOT_LIMIT, buildFailure);
        }
    }

    public ClaimRegisterResult tryRegisterOnClaim(Player player, ActiveVehicle vehicle) {
        if (vehicle == null) {
            return ClaimRegisterResult.skip();
        }
        return tryRegisterOnClaim(player, adapt(vehicle));
    }

    ClaimRegisterResult tryRegisterOnClaim(Player player, VehicleClaimTarget vehicle) {
        if (player == null || vehicle == null) {
            return ClaimRegisterResult.skip();
        }

        String vehicleUuid = vehicle.getVehicleUuid();
        if (vehicleUuid == null) {
            return ClaimRegisterResult.skip();
        }

        if (registry.isBerthed(vehicleUuid)) {
            return ClaimRegisterResult.skip();
        }

        String vehicleTypeId = vehicle.getVehicleTypeId();
        if (vehicleTypeId == null || vehicleTypeId.isEmpty()
                || !VehiclesConfigLoader.isKnownType(vehicleTypeId)) {
            return ClaimRegisterResult.failUnknownType();
        }

        CanBuildResult slotResult = VehicleSlotGuard.checkCanBuild(
                player.getName(), vehicleTypeId, registry);
        if (slotResult != CanBuildResult.OK) {
            return ClaimRegisterResult.failSlotLimit(slotResult);
        }

        return ClaimRegisterResult.allowed();
    }

    private static VehicleClaimTarget adapt(ActiveVehicle vehicle) {
        return new VehicleClaimTarget() {
            @Override
            public String getVehicleUuid() {
                return vehicle.getUUID();
            }

            @Override
            public String getVehicleTypeId() {
                return vehicle.getId();
            }
        };
    }
}
