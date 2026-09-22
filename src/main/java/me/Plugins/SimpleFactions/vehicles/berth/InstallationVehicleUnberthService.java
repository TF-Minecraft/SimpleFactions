package me.Plugins.SimpleFactions.vehicles.berth;


import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.registry.OwnershipMode;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.installation.Installation;
import net.tfminecraft.VehicleFramework.VehicleFramework;

public final class InstallationVehicleUnberthService {
    private final PlayerVehicleRegistry registry;
    private final Consumer<String> ownershipClearer;

    public InstallationVehicleUnberthService(PlayerVehicleRegistry registry) {
        this(registry, uuid -> VehicleFramework.getVehicleManager().clearOwnership(uuid));
    }

    InstallationVehicleUnberthService(
            PlayerVehicleRegistry registry, Consumer<String> ownershipClearer) {
        this.registry = registry;
        this.ownershipClearer = ownershipClearer;
    }

    public enum UnberthResult {
        OK,
        NOT_LEADER,
        NOT_BERTHED,
        EMBARGO
    }

    public UnberthResult unberth(
            Faction faction,
            String leaderName,
            Installation installation,
            String vehicleUuid) {
        if (faction == null
                || leaderName == null
                || installation == null
                || vehicleUuid == null
                || vehicleUuid.isBlank()) {
            return UnberthResult.NOT_BERTHED;
        }
        if (faction.getLeader() == null || !faction.getLeader().equalsIgnoreCase(leaderName)) {
            return UnberthResult.NOT_LEADER;
        }

        Optional<PlayerVehicleRecord> recordOpt = registry.getByVehicleUuid(vehicleUuid);
        if (recordOpt.isEmpty()
                || recordOpt.get().getMode() != OwnershipMode.INSTALLATION
                || !installation.getId().equals(recordOpt.get().getInstallationId())) {
            return UnberthResult.NOT_BERTHED;
        }

        if (VehicleInstallationLockService.isVehicleLocked(installation.getId(), Instant.now())) {
            return UnberthResult.EMBARGO;
        }

        ownershipClearer.accept(vehicleUuid);
        registry.unregister(vehicleUuid);
        SimpleFactions.getInstance().saveVehicleRegistry();
        return UnberthResult.OK;
    }

    public static String messageFor(UnberthResult result) {
        return switch (result) {
            case OK -> "§aVehicle unberthed and released as unclaimed.";
            case NOT_LEADER -> VehicleTransferMessages.notLeader();
            case NOT_BERTHED -> "§cThat vehicle is not berthed at this installation.";
            case EMBARGO -> VehicleInstallationLockService.UNBERTH_BLOCKED;
        };
    }
}
