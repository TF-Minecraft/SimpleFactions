package net.tfminecraft.simplefactions.objects.request;

import java.util.UUID;

import net.tfminecraft.tlibs.character.CharacterRef;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;

public final class VehicleGiveConsentRequest extends Request {
    private final String factionId;
    private final String vehicleUuid;
    private final String vehicleTypeId;
    private final UUID recipientUuid;
    private final UUID leaderUuid;
    private final String leaderName;
    private final CharacterRef leaderCharacter;
    private final CharacterRef recipientCharacter;

    /** Both characters are captured when the gift is offered; accepting needs the same ones. */
    public VehicleGiveConsentRequest(
            Guild sender,
            String factionId,
            String vehicleUuid,
            String vehicleTypeId,
            UUID recipientUuid,
            UUID leaderUuid,
            CharacterRef leaderCharacter,
            CharacterRef recipientCharacter) {
        super(sender);
        this.leaderCharacter = leaderCharacter;
        this.recipientCharacter = recipientCharacter;
        String leaderName = net.tfminecraft.simplefactions.identity.Members.key(leaderCharacter);
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

    /** The leader's member key when the gift was offered. */
    public String getLeaderName() {
        return leaderName;
    }

    public CharacterRef getLeaderCharacter() {
        return leaderCharacter;
    }

    public CharacterRef getRecipientCharacter() {
        return recipientCharacter;
    }
}
