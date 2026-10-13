package net.tfminecraft.simplefactions.vehicles;


import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleConstructionMessages;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.vfbuilders.core.Blueprint;
import net.tfminecraft.vfbuilders.events.BeginVehicleConstructionEvent;
import net.tfminecraft.vehicleframework.data.VehicleRemovePayload;
import net.tfminecraft.vehicleframework.events.VehicleRemoveEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;
import net.tfminecraft.vehicleframework.vehicles.Vehicle;

public final class VehicleIntegrationListener implements Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBeginVehicleConstruction(BeginVehicleConstructionEvent event) {
        Player constructor = event.getConstructor();
        if (constructor == null) {
            return;
        }

        Blueprint blueprint = event.getBlueprint();
        String vehicleTypeId = blueprint == null ? null : resolveVehicleTypeId(blueprint);
        CanBuildResult result = VehicleSlotGuard.checkCanBuild(constructor, vehicleTypeId);
        if (result != CanBuildResult.OK) {
            event.setCancelled(true);
            String message = VehicleConstructionMessages.forResult(result, vehicleTypeId);
            if (message != null) {
                constructor.sendMessage(message);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleRemove(VehicleRemoveEvent event) {
        ActiveVehicle vehicle = event.getVehicle();
        if (vehicle == null || vehicle.getUUID() == null) {
            return;
        }
        VehicleRemovePayload payload = event.getPayload();
        if (dropRecord(SimpleFactions.getVehicleRegistry(), vehicle.getUUID(), payload)) {
            SimpleFactions.getInstance().saveVehicleRegistry();
            if (payload != null && payload.isDeath()) {
                payload.getDeathCause().ifPresent(cause ->
                    SimpleFactions.getInstance().getLogger().info(
                        "Vehicle removed by death: "
                        + vehicle.getUUID()
                        + " cause="
                        + cause
                    )
                );
            }
        }
    }

    /**
     * Drops a removed vehicle's faction record. A berthed or pool vehicle whose chunk unloads is
     * still the faction's, so it only loses its record when it is destroyed.
     */
    static boolean dropRecord(PlayerVehicleRegistry registry, String vehicleUuid, VehicleRemovePayload payload) {
        if (!VehicleRemovals.isGoneForGood(payload)) {
            return false;
        }
        return registry.unregister(vehicleUuid);
    }

    public static String resolveVehicleTypeId(Blueprint blueprint) {
        Vehicle vehicle = blueprint.getVehicle();
        if (vehicle != null && vehicle.getId() != null && !vehicle.getId().isEmpty()) {
            return vehicle.getId();
        }
        return blueprint.getId();
    }
}
