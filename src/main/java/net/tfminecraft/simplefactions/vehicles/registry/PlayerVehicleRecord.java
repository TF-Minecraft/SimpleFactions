package net.tfminecraft.simplefactions.vehicles.registry;

import java.util.UUID;

public final class PlayerVehicleRecord {
    private final UUID playerUuid;
    private final String vehicleUuid;
    private final String vehicleTypeId;
    private final OwnershipMode mode;
    private final String installationId;
    private final String factionId;
    /** The account that played {@code playerUuid} when the row was written; null for older rows. */
    private final UUID accountUuid;
    /** The guild keeping an {@link OwnershipMode#UPKEEP} vehicle; null for every other row and for a faction's. */
    private final String guildId;

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
        this(playerUuid, vehicleUuid, vehicleTypeId, mode, installationId, factionId, null);
    }

    public PlayerVehicleRecord(
            UUID playerUuid,
            String vehicleUuid,
            String vehicleTypeId,
            OwnershipMode mode,
            String installationId,
            String factionId,
            UUID accountUuid) {
        this(playerUuid, vehicleUuid, vehicleTypeId, mode, installationId, factionId, accountUuid, null);
    }

    public PlayerVehicleRecord(
            UUID playerUuid,
            String vehicleUuid,
            String vehicleTypeId,
            OwnershipMode mode,
            String installationId,
            String factionId,
            UUID accountUuid,
            String guildId) {
        this.playerUuid = playerUuid;
        this.vehicleUuid = vehicleUuid;
        this.vehicleTypeId = vehicleTypeId;
        this.mode = mode;
        this.installationId = installationId;
        this.factionId = factionId;
        this.accountUuid = accountUuid;
        this.guildId = guildId;
    }

    /**
     * An upkeep-only row: {@code factionId} for a faction's vehicle, or {@code guildId} for a guild's.
     * {@code previousOwner} is the character the vehicle came from.
     */
    public static PlayerVehicleRecord upkeep(
            UUID previousOwner, String vehicleUuid, String vehicleTypeId, String factionId, String guildId) {
        return new PlayerVehicleRecord(previousOwner, vehicleUuid, vehicleTypeId, OwnershipMode.UPKEEP, null,
                guildId == null ? factionId : null, null, guildId);
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

    public UUID getAccountUuid() {
        return accountUuid;
    }

    public String getGuildId() {
        return guildId;
    }

    public PlayerVehicleRecord withAccount(UUID account) {
        return new PlayerVehicleRecord(
                playerUuid, vehicleUuid, vehicleTypeId, mode, installationId, factionId, account, guildId);
    }
}
