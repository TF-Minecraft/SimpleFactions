package net.tfminecraft.simplefactions.vehicles.registry;

import java.util.UUID;

public final class PlayerVehicleRecord {
    private final UUID playerUuid;
    private final String vehicleUuid;
    private final String vehicleTypeId;
    private final OwnershipMode mode;
    private final String installationId;
    private final String factionId;

    public PlayerVehicleRecord(
            UUID playerUuid,
            String vehicleUuid,
            String vehicleTypeId,
            OwnershipMode mode,
            String installationId) {
        this(playerUuid, vehicleUuid, vehicleTypeId, mode, installationId, null);
    }

    public PlayerVehicleRecord(
            UUID playerUuid,
            String vehicleUuid,
            String vehicleTypeId,
            OwnershipMode mode,
            String installationId,
            String factionId) {
        this.playerUuid = playerUuid;
        this.vehicleUuid = vehicleUuid;
        this.vehicleTypeId = vehicleTypeId;
        this.mode = mode;
        this.installationId = installationId;
        this.factionId = factionId;
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

    public String getFactionId() {
        return factionId;
    }
}
