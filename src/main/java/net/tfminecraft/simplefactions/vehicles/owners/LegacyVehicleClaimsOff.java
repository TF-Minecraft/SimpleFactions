package net.tfminecraft.simplefactions.vehicles.owners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.vehicleframework.events.VehicleOwnerClaimedEvent;

/** Compatibility bridge, loaded only when the provider lacks the modern event. */
public final class LegacyVehicleClaimsOff implements Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClaim(VehicleOwnerClaimedEvent event) {
        VehicleClaimGuards.refuse(event.getPlayer(), event);
    }
}
