package net.tfminecraft.simplefactions.vehicles.handover;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments;

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
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeMessages;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService.Quote;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerMeta;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Reason;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;
import net.tfminecraft.tlibs.character.CharacterRef;

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
    private final BiPredicate<String, UUID> ownerAssigner;

    public VehicleHandoverService(PlayerVehicleRegistry registry, VehicleFeeStore store, Runnable saver) {
        this(registry, store, saver, VehicleHandoverService::assignFrameworkOwner);
    }

    VehicleHandoverService(
            PlayerVehicleRegistry registry,
            VehicleFeeStore store,
            Runnable saver,
            BiPredicate<String, UUID> ownerAssigner) {
        this.registry = registry;
        this.store = store;
        this.saver = saver;
        this.ownerAssigner = ownerAssigner;
    }

    /** Gives the vehicle to the recipient's character, emptying its crew list. */
    static boolean assignFrameworkOwner(String vehicleUuid, UUID character) {
        return VehicleOwnerWrites.write(vehicleUuid, OwnerTags.character(character), Reason.HANDOVER) == Result.OK;
    }

    /** Whether the owner's character may hand this vehicle to the recipient's account right now. */
    public Outcome evaluate(CharacterRef owner, UUID recipientAccount, String vehicleUuid) {
        String typeId = owner == null ? null : personalVehicleType(owner.characterId(), vehicleUuid);
        if (typeId == null) {
            return new Outcome(Status.NOT_OWNER, null, null);
        }
        if (!VehiclesConfigLoader.isKnownType(typeId)) {
            return new Outcome(Status.UNKNOWN_TYPE, null, typeId);
        }
        if (FactionCampaignBattleLock.blocks(FactionManager.getByMember(Members.key(owner)))) {
            return new Outcome(Status.IN_BATTLE, null, typeId);
        }
        CanBuildResult slot = VehicleSlotGuard.checkCanBuild(recipientAccount, typeId, registry);
        if (slot != CanBuildResult.OK) {
            return new Outcome(Status.NO_ROOM, slot, typeId);
        }
        return new Outcome(Status.OK, null, typeId);
    }

    /** The type of the vehicle if this character owns it personally (not as a faction vehicle). */
    private String personalVehicleType(UUID owner, String vehicleUuid) {
        if (vehicleUuid == null || registry.isFactionOwned(vehicleUuid)) {
            return null;
        }
        return VehicleOwnerWrites.read(vehicleUuid)
                .filter(meta -> OwnerTags.names(meta.currentOwner(), owner))
                .map(OwnerMeta::typeId)
                .orElse(null);
    }

    public void offer(Player owner, Player recipient, String vehicleUuid, String vehicleTypeId) {
        if (Members.session(owner).isEmpty() || Members.session(recipient).isEmpty()) {
            owner.sendMessage("§cBoth players must select a living character."); return;
        }
        Guild guild = FactionManager.getGuildByMember(Members.key(owner));
        VehicleHandoverRequest request = new VehicleHandoverRequest(
                guild,
                vehicleUuid,
                vehicleTypeId,
                owner.getUniqueId(),
                recipient.getUniqueId());
        RequestManager.addRequest(owner, recipient, request);
        // addRequest refuses when the recipient is already considering another request.
        if (RequestManager.getRequest(recipient) != request) {
            return;
        }
        recipient.sendMessage(VehicleHandoverMessages.prompt(Members.name(owner), vehicleTypeId));
        owner.sendMessage(VehicleHandoverMessages.sent(Members.name(recipient)));
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
        if (req.getOwnerCharacter() == null || !Members.session(recipient).map(value -> value.ref())
                .filter(value -> value.equals(req.getRecipientCharacter())).isPresent()) {
            recipient.sendMessage("§cThis request belongs to another character."); return;
        }
        var payments = VehiclePayments.current();
        String scope = "handover:" + req.getVehicleUuid();
        if (payments == null || !payments.ready() || payments.blockedVehicle(req.getVehicleUuid())) {
            tellBoth(req, recipient, "§cVehicle payment needs staff review or recovery."); return;
        }
        Outcome outcome = evaluate(req.getOwnerCharacter(), recipient.getUniqueId(), req.getVehicleUuid());
        if (outcome.status() != Status.OK) {
            tellBoth(req, recipient, VehicleHandoverMessages.forOutcome(outcome, Members.name(recipient)));
            return;
        }
        String ownerKey = req.getOwnerKey();
        Quote quote = VehicleFeeService.quote(FeeKind.TRANSFER_FEE, ownerKey, outcome.vehicleTypeId());
        if (!Objects.equals(req.getVehicleTypeId(), outcome.vehicleTypeId()) || !req.matchesFee(quote)) {
            tellBoth(req, recipient, VehicleHandoverMessages.feeChanged());
            return;
        }
        if (quote != null && payments.charge(scope, req.getOperationId(), req.getOwnerCharacter(), quote) != net.tfminecraft.denareconomy.api.Result.APPLIED) {
            tellBoth(req, recipient, VehicleFeeMessages.handoverUnaffordable(quote)); return;
        }
        // The recipient becomes the payer before VehicleFramework's owner changes, and stops being it if that fails.
        var previous = registry.getByVehicleUuid(req.getVehicleUuid()).orElse(null);
        var recipientCharacter = req.getRecipientCharacter();
        if (!payments.personal(req.getVehicleUuid(), outcome.vehicleTypeId(), recipientCharacter.characterId(),
                recipientCharacter.ownerId())) {
            if (quote != null) payments.refund(scope);
            tellBoth(req, recipient, VehicleHandoverMessages.ownershipUnavailable());
            return;
        }
        if (!ownerAssigner.test(req.getVehicleUuid(), recipientCharacter.characterId())) {
            payments.replaceRow(req.getVehicleUuid(), previous);
            if (quote != null) {
                payments.refund(scope);
            }
            tellBoth(req, recipient, VehicleHandoverMessages.ownershipUnavailable());
            return;
        }
        if (quote != null) payments.complete(scope, Members.key(req.getRecipientCharacter()));
        else { store.setLastOwner(req.getVehicleUuid(), Members.key(req.getRecipientCharacter())); saver.run(); }
        recipient.sendMessage(VehicleHandoverMessages.successRecipient(Members.display(ownerKey)));
        Player owner = Bukkit.getPlayer(req.getOwnerUuid());
        if (owner != null && owner.isOnline()) {
            if (quote != null) {
                owner.sendMessage(VehicleFeeMessages.handoverPaid(quote));
            }
            owner.sendMessage(VehicleHandoverMessages.successOwner(Members.name(recipient)));
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
