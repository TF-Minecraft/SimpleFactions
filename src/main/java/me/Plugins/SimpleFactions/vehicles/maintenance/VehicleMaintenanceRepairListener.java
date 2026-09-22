package me.Plugins.SimpleFactions.vehicles.maintenance;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import net.tfminecraft.VehicleFramework.Events.VehicleRepairStartEvent;
import net.tfminecraft.VehicleFramework.Vehicles.ActiveVehicle;

public final class VehicleMaintenanceRepairListener implements Listener {
    private final VehicleMaintenanceStore store;

    public VehicleMaintenanceRepairListener(VehicleMaintenanceStore store) {
        this.store = store;
    }

    @EventHandler
    public void onRepairStart(VehicleRepairStartEvent event) {
        if (event == null) {
            return;
        }
        ActiveVehicle vehicle = event.getVehicle();
        if (vehicle == null || vehicle.getUUID() == null) {
            return;
        }
        if (!shouldCancelRepair(store, vehicle.getUUID())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player != null) {
            player.sendMessage(VehicleMaintenanceMessages.repairBlocked());
        }
    }

    static boolean shouldCancelRepair(VehicleMaintenanceStore store, String vehicleUuid) {
        return store != null && store.isUnpaid(vehicleUuid);
    }
}
