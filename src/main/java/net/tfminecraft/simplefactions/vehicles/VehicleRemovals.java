package net.tfminecraft.simplefactions.vehicles;

import net.tfminecraft.vehicleframework.data.VehicleRemovePayload;
import net.tfminecraft.vehicleframework.enums.VehicleRemoveReason;

/**
 * VehicleFramework fires VehicleRemoveEvent when a vehicle's chunk unloads as well as when
 * it is destroyed. An unloaded vehicle comes back when its chunk loads, so state kept about
 * it must survive the unload.
 */
public final class VehicleRemovals {
    private VehicleRemovals() {}

    /** False only for an unload; a missing payload counts as gone, as before payloads existed. */
    public static boolean isGoneForGood(VehicleRemovePayload payload) {
        if (payload == null || payload.isDeath()) {
            return true;
        }
        return payload.getRemoveReason().orElse(null) != VehicleRemoveReason.UNLOAD;
    }
}
