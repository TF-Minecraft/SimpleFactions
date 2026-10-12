package net.tfminecraft.simplefactions.objects.request;

import java.util.UUID;

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
    private final String ownerName;
    /** The owner's character when the offer was made; it pays the fee even if they log off. */
    private final String ownerKey;
    private final UUID recipientUuid;
    private final String feeFactionId;
    private final double feeAmount;

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
        this.ownerKey = net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries.ownerKey(ownerName);
        Quote quote = VehicleFeeService.quote(FeeKind.TRANSFER_FEE, ownerKey, vehicleTypeId);
        this.feeFactionId = quote == null ? null : quote.faction().getId();
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

    public String getOwnerName() {
        return ownerName;
    }

    public String getOwnerKey() {
        return ownerKey;
    }

    public UUID getRecipientUuid() {
        return recipientUuid;
    }

    public boolean matchesFee(Quote quote) {
        return java.util.Objects.equals(feeFactionId, quote == null ? null : quote.faction().getId())
                && Double.compare(feeAmount, quote == null ? 0.0 : quote.amount()) == 0;
    }
}
