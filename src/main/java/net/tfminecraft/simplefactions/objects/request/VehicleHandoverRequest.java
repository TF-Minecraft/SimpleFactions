package net.tfminecraft.simplefactions.objects.request;

import java.util.UUID;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterRef;
import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService.Quote;

/** A player offering one of their personal vehicles to another player. */
public final class VehicleHandoverRequest extends Request {
    private final String vehicleUuid;
    private final String vehicleTypeId;
    private final UUID ownerUuid;
    /** The owner's character when the offer was made; it pays the fee even if they log off. */
    private final String ownerKey;
    private final UUID recipientUuid;
    private final String feeFactionId;
    private final double feeAmount;
    private final UUID feeIncarnation;
    private final CharacterRef ownerCharacter;
    private final CharacterRef recipientCharacter;
    private final String operationId = "sf:vehicle:handover:" + UUID.randomUUID();
    /** When the owner made the offer; the fee's intent carries it, so a retry matches. */
    private final long offeredAt = System.currentTimeMillis();

    /** The sender guild is null when the owner is in no faction. */
    public VehicleHandoverRequest(
            Guild sender,
            String vehicleUuid,
            String vehicleTypeId,
            UUID ownerUuid,
            UUID recipientUuid) {
        super(sender);
        this.vehicleUuid = vehicleUuid;
        this.vehicleTypeId = vehicleTypeId;
        this.ownerUuid = ownerUuid;
        this.recipientUuid = recipientUuid;
        this.ownerCharacter = Members.session(Bukkit.getPlayer(ownerUuid)).map(value -> value.ref()).orElse(null);
        this.recipientCharacter = Members.session(Bukkit.getPlayer(recipientUuid)).map(value -> value.ref()).orElse(null);
        this.ownerKey = Members.key(ownerCharacter);
        Quote quote = VehicleFeeService.quote(FeeKind.TRANSFER_FEE, ownerKey, vehicleTypeId);
        this.feeFactionId = quote == null ? null : quote.faction().getId();
        this.feeIncarnation = quote == null ? null : quote.faction().getOrCreateMainGuild().getIncarnationId();
        this.feeAmount = quote == null ? 0.0 : quote.amount();
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

    public String getOwnerKey() {
        return ownerKey;
    }

    public UUID getRecipientUuid() {
        return recipientUuid;
    }

    public CharacterRef getOwnerCharacter() { return ownerCharacter; }
    public CharacterRef getRecipientCharacter() { return recipientCharacter; }
    public String getOperationId() { return operationId; }
    public long getOfferedAt() { return offeredAt; }

    public boolean matchesFee(Quote quote) {
        return java.util.Objects.equals(feeFactionId, quote == null ? null : quote.faction().getId())
                && java.util.Objects.equals(feeIncarnation, quote == null ? null : quote.faction().getOrCreateMainGuild().getIncarnationId())
                && Double.compare(feeAmount, quote == null ? 0.0 : quote.amount()) == 0;
    }
}
