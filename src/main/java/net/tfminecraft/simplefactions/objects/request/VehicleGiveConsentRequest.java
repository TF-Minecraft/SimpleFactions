package net.tfminecraft.simplefactions.objects.request;

import java.util.UUID;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;

public final class VehicleGiveConsentRequest extends Request {
    private final String factionId;
    private final String vehicleUuid;
    private final String vehicleTypeId;
    private final UUID recipientUuid;
    private final UUID leaderUuid;
    private final String leaderName;

    public VehicleGiveConsentRequest(
            Guild sender,
            String factionId,
            String vehicleUuid,
            String vehicleTypeId,
            UUID recipientUuid,
            UUID leaderUuid,
            String leaderName) {
        super(sender);
        this.factionId = factionId;
        this.vehicleUuid = vehicleUuid;
        this.vehicleTypeId = vehicleTypeId;
        this.recipientUuid = recipientUuid;
        this.leaderUuid = leaderUuid;
        this.leaderName = leaderName;
        this.time = System.currentTimeMillis()
                + InstallationConfigLoader.getTransferRequestTimeoutSeconds() * 1000L;
    }

    public String getFactionId() {
        return factionId;
    }

    public String getVehicleUuid() {
        return vehicleUuid;
    }

    public String getVehicleTypeId() {
        return vehicleTypeId;
    }

    public UUID getRecipientUuid() {
        return recipientUuid;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public String getLeaderName() {
        return leaderName;
    }
}
