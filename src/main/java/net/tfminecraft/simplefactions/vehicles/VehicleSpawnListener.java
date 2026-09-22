package net.tfminecraft.simplefactions.vehicles;


import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleOwnerSync;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import net.tfminecraft.vehicleframework.events.VehicleSpawnEvent;

public final class VehicleSpawnListener implements Listener {
    private final InstallationVehicleOwnerSync ownerSync;

    public VehicleSpawnListener(InstallationVehicleOwnerSync ownerSync) {
        this.ownerSync = ownerSync;
    }

    @EventHandler
    public void onVehicleSpawn(VehicleSpawnEvent event) {
        if (event.getVehicle() == null) {
            return;
        }
        ownerSync.syncIfBerthed(event.getVehicle());
    }
}
