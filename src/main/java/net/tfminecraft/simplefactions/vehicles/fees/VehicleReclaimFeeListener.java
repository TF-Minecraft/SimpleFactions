package net.tfminecraft.simplefactions.vehicles.fees;

import org.bukkit.entity.Player;
import java.util.UUID;
import net.tfminecraft.denareconomy.api.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.vehicles.VehicleRemovals;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService.Quote;
import net.tfminecraft.vehicleframework.events.VehicleOwnerClaimedEvent;
import net.tfminecraft.vehicleframework.events.VehicleRemoveEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

/**
 * Releasing a vehicle and letting someone else claim it is a transfer, so the claimant pays
 * the transfer fee the last owner's faction would have charged the last owner. Reclaiming
 * your own vehicle, such as after staff respawn it, is free, and so are admin takeovers.
 */
public final class VehicleReclaimFeeListener implements Listener {
    private final VehicleFeeStore store;
    private final VehicleFeeConfirmations confirmations;
    private final Runnable saver;

    public VehicleReclaimFeeListener(VehicleFeeStore store, VehicleFeeConfirmations confirmations, Runnable saver) {
        this.store = store;
        this.confirmations = confirmations;
        this.saver = saver;
    }

    // After the slot-limit check (HIGH), so nobody pays for a claim that is then refused.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleOwnerClaimed(VehicleOwnerClaimedEvent event) {
        Player player = event.getPlayer();
        ActiveVehicle vehicle = event.getVehicle();
        if (player == null || vehicle == null || vehicle.getUUID() == null) {
            return;
        }
        var session = Members.session(player).orElse(null);
        var payments = VehiclePayments.current();
        String scope = "claim:" + vehicle.getUUID();
        if (session == null || payments == null || !payments.ready() || payments.blockedVehicle(vehicle.getUUID())) {
            event.setCancelled(true); player.sendMessage("§cVehicle payment needs a living character or staff recovery."); return;
        }
        String lastOwner = store.getLastOwner(vehicle.getUUID());
        if (lastOwner == null || Members.is(player, lastOwner) || Permissions.isAdmin(player)) {
            return;
        }
        Quote quote = VehicleFeeService.quote(FeeKind.TRANSFER_FEE, lastOwner, vehicle.getId());
        if (quote == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String target = scope + ":" + session.ref().characterId() + ":" + quote.faction().getOrCreateMainGuild().getIncarnationId();
        if (!confirmations.confirm(player.getUniqueId(), target, quote.amount(), now)) {
            event.setCancelled(true);
            confirmations.ask(player.getUniqueId(), target, quote.amount(), now);
            player.sendMessage(VehicleFeeMessages.claimConfirm(quote, Members.display(lastOwner)));
            return;
        }
        if (payments.charge(scope, "sf:vehicle:claim:" + UUID.randomUUID(), session.ref(), quote) != Result.APPLIED) {
            event.setCancelled(true);
            player.sendMessage(VehicleFeeMessages.claimUnaffordable(quote));
            return;
        }
        player.sendMessage(VehicleFeeMessages.claimPaid(quote));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClaimed(VehicleOwnerClaimedEvent event) {
        if (event.getPlayer() == null || event.getVehicle() == null) {
            return;
        }
        String scope = "claim:" + event.getVehicle().getUUID();
        var payments = VehiclePayments.current();
        var receipt = payments == null ? null : payments.receipt(scope);
        String owner = receipt == null ? Members.session(event.getPlayer()).map(session -> session.ref().characterId().toString()).orElse(null) : receipt.payer().characterId().toString();
        if (owner == null || payments == null || !payments.personal(event.getVehicle().getUUID(), event.getVehicle().getId(), UUID.fromString(owner))) return;
        String member = Members.key(UUID.fromString(owner));
        if (receipt != null) payments.complete(scope, member);
        else { store.setLastOwner(event.getVehicle().getUUID(), member); saver.run(); }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleRemove(VehicleRemoveEvent event) {
        if (event.getVehicle() == null || !VehicleRemovals.isGoneForGood(event.getPayload())) {
            return;
        }
        if (store.getLastOwner(event.getVehicle().getUUID()) != null) {
            store.forgetVehicle(event.getVehicle().getUUID());
            saver.run();
        }
    }
}
