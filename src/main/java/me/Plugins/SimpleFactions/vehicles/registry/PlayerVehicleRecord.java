package me.Plugins.SimpleFactions.vehicles.registry;

import java.util.UUID;

public final class PlayerVehicleRecord {
    private final UUID playerUuid;
    private final String vehicleUuid;
    private final String vehicleTypeId;
    private final OwnershipMode mode;
    private final String installationId;

    public PlayerVehicleRecord(
            UUID playerUuid,
            String vehicleUuid,
            String vehicleTypeId,
            OwnershipMode mode,
            String installationId) {
        this.playerUuid = playerUuid;
        this.vehicleUuid = vehicleUuid;
        this.vehicleTypeId = vehicleTypeId;
        this.mode = mode;
        this.installationId = installationId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getVehicleUuid() {
        return vehicleUuid;
    }

    public String getVehicleTypeId() {
        return vehicleTypeId;
    }

    public OwnershipMode getMode() {
        return mode;
    }

    public String getInstallationId() {
        return installationId;
    }
}
