package net.tfminecraft.simplefactions.vehicles.battle;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import net.tfminecraft.vehicleframework.events.ServerVehicleUseEvent;

/** Loaded only after the optional VF event has been found. */
public final class ServerVehicleUseListener implements Listener {
    @EventHandler
    public void onUse(ServerVehicleUseEvent event) {
        if (BattleVehicleEligibilityService.mayUseServer(event.getPlayer(), event.getVehicle())) {
            event.setAllowed(true);
        }
    }
}
