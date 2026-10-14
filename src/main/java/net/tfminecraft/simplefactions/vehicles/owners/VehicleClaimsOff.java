package net.tfminecraft.simplefactions.vehicles.owners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.vehicleframework.api.OwnershipReason;
import net.tfminecraft.vehicleframework.events.VehicleOwnershipChangeEvent;

/**
 * Without VehicleFramework 2.13 in characters mode, SimpleFactions cannot record a
 * character payer for a claim, so non-staff claims are refused.
 * Load this listener only after probing the modern ownership event.
 */
public final class VehicleClaimsOff implements Listener {
    public static final String MESSAGE = VehicleClaimGuards.MESSAGE;

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClaim(VehicleOwnershipChangeEvent event) {
        if (event.getReason() == OwnershipReason.CLAIM) {
            VehicleClaimGuards.refuse(event.getPlayer(), event);
        }
    }
}
