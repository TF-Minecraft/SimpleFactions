package net.tfminecraft.simplefactions.vehicles.owners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.vehicleframework.events.VehicleOwnerClaimedEvent;

/**
 * Without VehicleFramework 2.13 in characters mode, SimpleFactions cannot record a
 * character payer for a claim, so non-staff claims are refused. The claim event exists
 * in every VehicleFramework version, so this listener loads on older ones too.
 */
public final class VehicleClaimsOff implements Listener {
    public static final String MESSAGE = "§cClaiming vehicles is off until VehicleFramework is updated.";

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClaim(VehicleOwnerClaimedEvent event) {
        if (event.getPlayer() == null || Permissions.isAdmin(event.getPlayer())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(MESSAGE);
    }
}
