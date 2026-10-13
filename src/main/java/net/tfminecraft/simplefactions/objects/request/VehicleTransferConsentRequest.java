package net.tfminecraft.simplefactions.objects.request;

import java.util.UUID;

import net.tfminecraft.tlibs.character.CharacterRef;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;

public final class VehicleTransferConsentRequest extends Request {
    private final String installationId;
    private final String installationName;
    private final String vehicleUuid;
    private final String vehicleTypeId;
    private final UUID ownerUuid;
    private final UUID proposerLeaderUuid;
    private final boolean pool;
    private final String destinationFactionId;
    private final CharacterRef ownerCharacter;
    private final CharacterRef leaderCharacter;

    public VehicleTransferConsentRequest(
            Guild sender,
            String installationId,
            String installationName,
            String vehicleUuid,
            String vehicleTypeId,
            CharacterRef ownerCharacter,
            CharacterRef leaderCharacter,
            boolean pool) {
        super(sender);
        UUID ownerUuid = ownerCharacter.ownerId();
        UUID proposerLeaderUuid = leaderCharacter.ownerId();
        this.ownerCharacter = ownerCharacter;
        this.leaderCharacter = leaderCharacter;
        this.installationId = installationId;
        this.installationName = installationName;
        this.vehicleUuid = vehicleUuid;
        this.vehicleTypeId = vehicleTypeId;
        this.ownerUuid = ownerUuid;
        this.proposerLeaderUuid = proposerLeaderUuid;
        this.pool = pool;
        this.destinationFactionId = sender.getFaction() == null ? null : sender.getFaction().getId();
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

    public String getDestinationFactionId() {
        return destinationFactionId;
    }

    public boolean isPool() {
        return pool;
    }

    /** The vehicle owner's character when the request was sent. */
    public CharacterRef getOwnerCharacter() {
        return ownerCharacter;
    }

    /** The proposing leader's character when the request was sent. */
    public CharacterRef getLeaderCharacter() {
        return leaderCharacter;
    }
}
