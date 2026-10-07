package net.tfminecraft.simplefactions.vehicles.handover;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.objects.request.VehicleHandoverRequest;
import net.tfminecraft.simplefactions.vehicles.berth.FactionCampaignBattleLock;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeMessages;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService.Quote;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.vehicleframework.data.OwnedVehicleSummary;

/**
 * Hands a personal vehicle from one player to another. The owner pays their faction's
 * transfer fee when the recipient accepts; faction leaders and players with no faction
 * pay nothing.
 */
public final class VehicleHandoverService {
    public enum Status {
        OK,
        NOT_OWNER,
        IN_BATTLE,
        UNKNOWN_TYPE,
        NO_ROOM
    }

    public record Outcome(Status status, CanBuildResult slotFailure, String vehicleTypeId) {}

    private final PlayerVehicleRegistry registry;
    private final VehicleFeeStore store;
    private final Runnable saver;
    private final BiPredicate<String, String> ownerAssigner;

    public VehicleHandoverService(PlayerVehicleRegistry registry, VehicleFeeStore store, Runnable saver) {
        this(registry, store, saver, FactionVehicleReleaseService::assignFrameworkOwner);
    }

    VehicleHandoverService(
            PlayerVehicleRegistry registry,
            VehicleFeeStore store,
            Runnable saver,
            BiPredicate<String, String> ownerAssigner) {
        this.registry = registry;
        this.store = store;
        this.saver = saver;
        this.ownerAssigner = ownerAssigner;
    }

    /** Whether the owner may hand this vehicle to the recipient right now. */
    public Outcome evaluate(String ownerName, String recipientName, String vehicleUuid) {
        String typeId = personalVehicleType(ownerName, vehicleUuid);
        if (typeId == null) {
            return new Outcome(Status.NOT_OWNER, null, null);
        }
        if (!VehiclesConfigLoader.isKnownType(typeId)) {
            return new Outcome(Status.UNKNOWN_TYPE, null, typeId);
        }
        if (FactionCampaignBattleLock.blocks(FactionManager.getByMember(ownerName))) {
            return new Outcome(Status.IN_BATTLE, null, typeId);
        }
        CanBuildResult slot = VehicleSlotGuard.checkCanBuild(recipientName, typeId, registry);
        if (slot != CanBuildResult.OK) {
            return new Outcome(Status.NO_ROOM, slot, typeId);
        }
        return new Outcome(Status.OK, null, typeId);
    }

    /** The type of the vehicle if it is one of the owner's personal (not faction) vehicles. */
    private String personalVehicleType(String ownerName, String vehicleUuid) {
        if (ownerName == null || vehicleUuid == null || registry.isFactionOwned(vehicleUuid)) {
            return null;
        }
        for (OwnedVehicleSummary vehicle : VehicleOwnershipQueries.personalVehicles(ownerName, registry)) {
            if (vehicleUuid.equals(vehicle.getUuid())) {
                return vehicle.getTypeId();
            }
        }
        return null;
    }

    public void offer(Player owner, Player recipient, String vehicleUuid, String vehicleTypeId) {
        Guild guild = FactionManager.getGuildByMember(owner.getName());
        VehicleHandoverRequest request = new VehicleHandoverRequest(
                guild,
                vehicleUuid,
                vehicleTypeId,
                owner.getUniqueId(),
                owner.getName(),
                recipient.getUniqueId());
        RequestManager.addRequest(owner, recipient, request);
        // addRequest refuses when the recipient is already considering another request.
        if (RequestManager.getRequest(recipient) != request) {
            return;
        }
        recipient.sendMessage(VehicleHandoverMessages.prompt(owner.getName(), vehicleTypeId));
        owner.sendMessage(VehicleHandoverMessages.sent(recipient.getName()));
    }

    public void acceptRequest(Player recipient) {
        if (!(RequestManager.getRequest(recipient) instanceof VehicleHandoverRequest req)) {
            return;
        }
        if (!recipient.getUniqueId().equals(req.getRecipientUuid())) {
            recipient.sendMessage("§cYou cannot accept this request.");
            return;
        }
        if (req.timedOut()) {
            notifyExpired(req, recipient);
            return;
        }
        Outcome outcome = evaluate(req.getOwnerName(), recipient.getName(), req.getVehicleUuid());
        if (outcome.status() != Status.OK) {
            tellBoth(req, recipient, VehicleHandoverMessages.forOutcome(outcome, recipient.getName()));
            return;
        }
        Quote quote = VehicleFeeService.quote(FeeKind.TRANSFER_FEE, req.getOwnerName(), outcome.vehicleTypeId());
        if (!Objects.equals(req.getVehicleTypeId(), outcome.vehicleTypeId()) || !req.matchesFee(quote)) {
            tellBoth(req, recipient, VehicleHandoverMessages.feeChanged());
            return;
        }
        UUID payer = null;
        if (quote != null) {
            payer = VehicleFeeService.resolve(req.getOwnerName());
            if (payer == null) {
                payer = req.getOwnerUuid();
            }
            if (!VehicleFeeService.collect(payer, quote)) {
                tellBoth(req, recipient, VehicleFeeMessages.handoverUnaffordable(quote));
                return;
            }
        }
        if (!ownerAssigner.test(req.getVehicleUuid(), recipient.getName())) {
            if (quote != null) {
                VehicleFeeService.refund(payer, quote.faction().getId(), FeeKind.TRANSFER_FEE, quote.amount());
            }
            tellBoth(req, recipient, VehicleHandoverMessages.ownershipUnavailable());
            return;
        }
        store.setLastOwner(req.getVehicleUuid(), recipient.getName());
        saver.run();
        recipient.sendMessage(VehicleHandoverMessages.successRecipient(req.getOwnerName()));
        Player owner = Bukkit.getPlayer(req.getOwnerUuid());
        if (owner != null && owner.isOnline()) {
            if (quote != null) {
                owner.sendMessage(VehicleFeeMessages.handoverPaid(quote));
            }
            owner.sendMessage(VehicleHandoverMessages.successOwner(recipient.getName()));
        }
    }

    public void notifyExpired(VehicleHandoverRequest req, Player recipient) {
        tellBoth(req, recipient, VehicleHandoverMessages.expired());
    }

    private static void tellBoth(VehicleHandoverRequest req, Player recipient, String message) {
        if (recipient != null && recipient.isOnline()) {
            recipient.sendMessage(message);
        }
        Player owner = req.getOwnerUuid() == null ? null : Bukkit.getPlayer(req.getOwnerUuid());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(message);
        }
    }
}
