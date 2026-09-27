package net.tfminecraft.simplefactions.vehicles.fees;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService.Quote;
import net.tfminecraft.vehicleframework.data.VehicleRemovePayload;
import net.tfminecraft.vehicleframework.enums.VehicleRemoveReason;
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
        String lastOwner = store.getLastOwner(vehicle.getUUID());
        if (lastOwner == null || lastOwner.equalsIgnoreCase(player.getName()) || Permissions.isAdmin(player)) {
            return;
        }
        Quote quote = VehicleFeeService.quote(FeeKind.TRANSFER_FEE, lastOwner, vehicle.getId());
        if (quote == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String target = "claim:" + vehicle.getUUID();
        if (!confirmations.confirm(player.getUniqueId(), target, quote.amount(), now)) {
            event.setCancelled(true);
            confirmations.ask(player.getUniqueId(), target, quote.amount(), now);
            player.sendMessage(VehicleFeeMessages.claimConfirm(quote, lastOwner));
            return;
        }
        if (!VehicleFeeService.collect(VehicleRegistrationFeeListener.payerUuid(player), quote)) {
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
        store.setLastOwner(event.getVehicle().getUUID(), event.getPlayer().getName());
        saver.run();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleRemove(VehicleRemoveEvent event) {
        if (event.getVehicle() == null || !isDestroyed(event.getPayload())) {
            return;
        }
        if (store.getLastOwner(event.getVehicle().getUUID()) != null) {
            store.forgetVehicle(event.getVehicle().getUUID());
            saver.run();
        }
    }

    /** Chunk unloads also fire VehicleRemoveEvent; only a vehicle that is gone for good is forgotten. */
    static boolean isDestroyed(VehicleRemovePayload payload) {
        if (payload == null) {
            return false;
        }
        if (payload.isDeath()) {
            return true;
        }
        VehicleRemoveReason reason = payload.getRemoveReason().orElse(null);
        return reason == VehicleRemoveReason.PLAYER_DESTROY || reason == VehicleRemoveReason.ADMIN_KILL;
    }
}
