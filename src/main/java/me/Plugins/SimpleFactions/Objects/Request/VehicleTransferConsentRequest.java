package me.Plugins.SimpleFactions.Objects.Request;

import java.util.UUID;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.InstallationConfigLoader;

public final class VehicleTransferConsentRequest extends Request {
    private final String installationId;
    private final String installationName;
    private final String vehicleUuid;
    private final String vehicleTypeId;
    private final UUID ownerUuid;
    private final UUID proposerLeaderUuid;

    public VehicleTransferConsentRequest(
            Guild sender,
            String installationId,
            String installationName,
            String vehicleUuid,
            String vehicleTypeId,
            UUID ownerUuid,
            UUID proposerLeaderUuid) {
        super(sender);
        this.installationId = installationId;
        this.installationName = installationName;
        this.vehicleUuid = vehicleUuid;
        this.vehicleTypeId = vehicleTypeId;
        this.ownerUuid = ownerUuid;
        this.proposerLeaderUuid = proposerLeaderUuid;
        this.time = System.currentTimeMillis()
                + InstallationConfigLoader.getTransferRequestTimeoutSeconds() * 1000L;
    }

    public String getInstallationId() {
        return installationId;
    }

    public String getInstallationName() {
        return installationName;
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

    public UUID getProposerLeaderUuid() {
        return proposerLeaderUuid;
    }
}
