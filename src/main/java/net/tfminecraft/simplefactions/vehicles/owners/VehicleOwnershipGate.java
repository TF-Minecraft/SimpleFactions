package net.tfminecraft.simplefactions.vehicles.owners;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.simplefactions.economy.Money;
import net.tfminecraft.simplefactions.events.FactionLeaderChangedEvent;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeConfirmations;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeMessages;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService.Quote;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryClaimListener;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryClaimService;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.vehicleframework.api.OwnershipReason;
import net.tfminecraft.vehicleframework.events.VehicleOwnershipChangeEvent;
import net.tfminecraft.vehicleframework.events.VehicleOwnershipChangedEvent;
import net.tfminecraft.vehicleframework.events.VehicleOwnershipEditCheckEvent;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

/**
 * SimpleFactions' side of VehicleFramework's player ownership changes. Vetoes come first
 * (faction vehicles, slot limits, then the claim fee), and the payer registry changes only
 * after VehicleFramework reports the write, once per operation. Bukkit only logs listener
 * exceptions, so every veto handler refuses on its own errors.
 */
public final class VehicleOwnershipGate implements Listener {
    public static final String PAYMENT_UNAVAILABLE = "§cVehicle payment needs a living character or staff recovery.";
    public static final String FAILED = "§cVehicle ownership could not be checked, so nothing changed.";
    public static final String HELD = "§cThis vehicle is held for staff; ask them to assign its owner.";
    static final int REMEMBERED_OPERATIONS = 1024;

    private final PlayerVehicleRegistry registry;
    private final VehicleRegistryClaimService claims;
    private final VehicleFeeStore store;
    private final VehicleFeeConfirmations confirmations;
    private final Runnable saver;
    private final Logger logger;
    /** Claim fees charged under an operation, refunded if VehicleFramework reports the write failed. */
    private final Map<UUID, String> charges = new HashMap<>();
    private final Set<UUID> recorded = new LinkedHashSet<>();

    public VehicleOwnershipGate(PlayerVehicleRegistry registry, VehicleRegistryClaimService claims,
            VehicleFeeStore store, VehicleFeeConfirmations confirmations, Runnable saver, Logger logger) {
        this.registry = registry;
        this.claims = claims;
        this.store = store;
        this.confirmations = confirmations;
        this.saver = saver;
        this.logger = logger;
    }

    /** Faction vehicles are changed only through SimpleFactions; claims need a payer and a free slot. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChange(VehicleOwnershipChangeEvent event) {
        try {
            if (event.isStaff()) return;
            ActiveVehicle vehicle = event.getVehicle();
            if (VehicleQuarantine.held(vehicle.getUUID())) {
                refuse(event, event.getPlayer(), HELD);
                return;
            }
            if (LeaderStamps.isFactionRow(row(vehicle))) {
                refuse(event, event.getPlayer(), VehicleRegistryClaimListener.FACTION_VEHICLE);
                return;
            }
            if (event.getReason() != OwnershipReason.CLAIM) return;
            VehiclePayments payments = VehiclePayments.current();
            if (event.getActor().isEmpty() || payments == null || !payments.ready()
                    || payments.blockedVehicle(vehicle.getUUID())) {
                refuse(event, event.getPlayer(), PAYMENT_UNAVAILABLE);
                return;
            }
            VehicleRegistryClaimListener.handleClaimResult(event, event.getPlayer(), vehicle.getId(),
                    claims.tryRegisterOnClaim(event.getPlayer(), vehicle));
        } catch (RuntimeException | LinkageError error) {
            fail(event, event.getPlayer(), error);
        }
    }

    /**
     * Claiming a vehicle someone else last owned is a transfer: the first click shows the fee,
     * the second pays it under VehicleFramework's operation ID. Own vehicles and staff are free.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClaimFee(VehicleOwnershipChangeEvent event) {
        try {
            if (event.getReason() != OwnershipReason.CLAIM || event.isStaff()) return;
            Player player = event.getPlayer();
            ActiveVehicle vehicle = event.getVehicle();
            CharacterRef actor = event.getActor().orElseThrow();
            String lastOwner = store.getLastOwner(vehicle.getUUID());
            if (lastOwner == null || lastOwner.equals(Members.key(actor))) return;
            Quote quote = VehicleFeeService.quote(FeeKind.TRANSFER_FEE, lastOwner, vehicle.getId());
            if (quote == null) return;
            String scope = "claim:" + vehicle.getUUID();
            long now = System.currentTimeMillis();
            String target = scope + ":" + actor.characterId() + ":"
                    + quote.faction().getOrCreateMainGuild().getIncarnationId();
            if (!confirmations.confirm(player.getUniqueId(), target, quote.amount(), now)) {
                confirmations.ask(player.getUniqueId(), target, quote.amount(), now);
                refuse(event, player, VehicleFeeMessages.claimConfirm(quote, Members.display(lastOwner)));
                return;
            }
            Result paid = VehiclePayments.current()
                    .charge(scope, "sf:vehicle:claim:" + event.getOperationId(), actor, quote);
            if (paid != Result.APPLIED) {
                refuse(event, player, VehicleFeeMessages.claimUnaffordable(quote));
                return;
            }
            charges.put(event.getOperationId(), scope);
            player.sendMessage(VehicleFeeMessages.claimPaid(quote));
        } catch (RuntimeException | LinkageError error) {
            fail(event, event.getPlayer(), error);
        }
    }

    /** Records a claim or takeover as the new owner's payer row, drops it on a clear, and refunds failed claims. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChanged(VehicleOwnershipChangedEvent event) {
        if (event.getReason().isSystem() || !remember(event.getOperationId())) return;
        String scope = charges.remove(event.getOperationId());
        try {
            VehiclePayments payments = VehiclePayments.current();
            if (!event.isSuccess()) {
                if (scope != null) refund(payments, scope, event);
                return;
            }
            ActiveVehicle vehicle = event.getVehicle().orElseThrow();
            switch (event.getReason()) {
                case CLAIM, TAKEOVER -> recordOwner(event, vehicle, scope, payments);
                case CLEAR -> forgetOwner(event, vehicle, payments);
                default -> {
                    // Whitelist and ticket changes do not touch the registry.
                }
            }
        } catch (RuntimeException | LinkageError error) {
            logger.log(Level.SEVERE, "Could not record ownership change " + event.getOperationId()
                    + "; the upkeep reconcile lists the vehicle", error);
        }
    }

    private void recordOwner(VehicleOwnershipChangedEvent event, ActiveVehicle vehicle, String scope,
            VehiclePayments payments) {
        CharacterRef owner = event.getTarget().or(event::getActor).orElse(null);
        if (owner == null || payments == null
                || !payments.personal(vehicle.getUUID(), vehicle.getId(), owner.characterId(), owner.ownerId())) {
            logger.warning("Vehicle " + vehicle.getUUID() + " changed owner but its payer row could not be"
                    + " written; the upkeep reconcile adds it");
            return;
        }
        String member = Members.key(owner);
        if (scope != null) {
            payments.complete(scope, member);
        } else {
            store.setLastOwner(vehicle.getUUID(), member);
            saver.run();
        }
        audit(event, vehicle);
    }

    private void forgetOwner(VehicleOwnershipChangedEvent event, ActiveVehicle vehicle, VehiclePayments payments) {
        if (row(vehicle) != null && (payments == null || !payments.replaceRow(vehicle.getUUID(), null))) {
            logger.warning("Vehicle " + vehicle.getUUID() + " was cleared but its payer row could not be removed");
        }
        // The last owner stays, so a friend claiming the released vehicle pays the transfer fee.
        audit(event, vehicle);
    }

    /** The guild bank may hold less than the fee by now; a short refund is logged and the player told. */
    private void refund(VehiclePayments payments, String scope, VehicleOwnershipChangedEvent event) {
        var receipt = payments == null ? null : payments.receipt(scope);
        var refund = receipt == null ? null : payments.refund(scope);
        if (refund == null || refund.result() != Result.APPLIED) {
            logger.severe("Claim fee " + scope + " was charged but the claim failed, and the refund did not go"
                    + " through; staff must recover it");
            return;
        }
        double refunded = refund.amount();
        double shortfall = Formatter.formatDouble(Money.denars(receipt.cents()) - refunded);
        if (shortfall > 0.0) {
            logger.warning("Transfer fee refund short by " + shortfall + " for " + receipt.payer().characterId()
                    + " from faction " + receipt.factionId());
        }
        event.getPlayer().ifPresent(player -> {
            if (shortfall <= 0.0) {
                player.sendMessage(VehicleFeeMessages.claimRefunded());
                return;
            }
            if (refunded > 0.0) player.sendMessage(VehicleFeeMessages.claimRefundedPart(refunded));
            player.sendMessage(VehicleFeeMessages.claimRefundShort(shortfall));
        });
    }

    private void audit(VehicleOwnershipChangedEvent event, ActiveVehicle vehicle) {
        if (!event.isStaff()) return;
        logger.info("[vehicle audit] " + event.getPlayer().map(Player::getName).orElse("console") + " "
                + event.getReason() + " " + vehicle.getUUID() + " (" + vehicle.getId() + "): "
                + event.getPrevious().owner() + " -> " + event.getCurrent().owner());
    }

    /** Edit check: a faction vehicle's leader may open its menu but use no control, and a stale stamp hides it. */
    @EventHandler
    public void onEditCheck(VehicleOwnershipEditCheckEvent event) {
        try {
            if (event.isStaff()) return;
            PlayerVehicleRecord record = row(event.getVehicle());
            if (!LeaderStamps.isFactionRow(record)) return;
            if (LeaderStamps.isStale(record, event.getVehicle().getOwnerData().getOwner())) {
                event.deny();
                LeaderStamps.scheduleRestamp(record);
            } else if (!event.isMenu()) {
                event.deny();
            }
        } catch (RuntimeException | LinkageError error) {
            event.deny();
        }
    }

    /** Backstop before every other vehicle click handler: a stale faction stamp is refused and fixed. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreInteract(VehiclePreInteractEvent event) {
        try {
            ActiveVehicle vehicle = event.getVehicle();
            if (vehicle == null || vehicle.getOwnerData() == null) return;
            PlayerVehicleRecord record = row(vehicle);
            if (LeaderStamps.isStale(record, vehicle.getOwnerData().getOwner())) {
                event.setCancelled(true);
                LeaderStamps.scheduleRestamp(record);
            }
        } catch (RuntimeException | LinkageError error) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeaderChanged(FactionLeaderChangedEvent event) {
        LeaderStamps.onLeaderChanged(event.getFaction(), registry);
    }

    private PlayerVehicleRecord row(ActiveVehicle vehicle) {
        return registry.getByVehicleUuid(vehicle.getUUID()).orElse(null);
    }

    private boolean remember(UUID operation) {
        if (!recorded.add(operation)) return false;
        if (recorded.size() > REMEMBERED_OPERATIONS) {
            Iterator<UUID> oldest = recorded.iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }

    private static void refuse(Cancellable event, Player player, String message) {
        event.setCancelled(true);
        if (player != null && message != null) player.sendMessage(message);
    }

    private void fail(Cancellable event, Player player, Throwable error) {
        event.setCancelled(true);
        logger.log(Level.SEVERE, "Vehicle ownership check failed; the change was refused", error);
        if (player != null) player.sendMessage(FAILED);
    }
}
