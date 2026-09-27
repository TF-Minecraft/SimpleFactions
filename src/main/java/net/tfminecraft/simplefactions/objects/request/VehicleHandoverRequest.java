package net.tfminecraft.simplefactions.objects.request;

import java.util.UUID;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;

/** A player offering one of their personal vehicles to another player. */
public final class VehicleHandoverRequest extends Request {
    private final String vehicleUuid;
    private final String vehicleTypeId;
    private final UUID ownerUuid;
    private final String ownerName;
    private final UUID recipientUuid;

    /** The sender guild is null when the owner is in no faction. */
    public VehicleHandoverRequest(
            Guild sender,
            String vehicleUuid,
            String vehicleTypeId,
            UUID ownerUuid,
            String ownerName,
            UUID recipientUuid) {
        super(sender);
        this.vehicleUuid = vehicleUuid;
        this.vehicleTypeId = vehicleTypeId;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.recipientUuid = recipientUuid;
        this.time = System.currentTimeMillis()
                + InstallationConfigLoader.getTransferRequestTimeoutSeconds() * 1000L;
    }

    public String getVehicleUuid() {
        return vehicleUuid;
    }

    public String getVehicleTypeId() {
        return vehicleTypeId;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public UUID getRecipientUuid() {
        return recipientUuid;
    }
}
