package net.tfminecraft.simplefactions.vehicles.fees;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.vehicles.VehicleRemovals;
import net.tfminecraft.vehicleframework.events.VehicleRemoveEvent;

/**
 * Forgets a vehicle's last owner once it is gone for good. Claim fees are charged by
 * {@link net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnershipGate}.
 */
public final class VehicleReclaimFeeListener implements Listener {
    private final VehicleFeeStore store;
    private final Runnable saver;

    public VehicleReclaimFeeListener(VehicleFeeStore store, Runnable saver) {
        this.store = store;
        this.saver = saver;
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
