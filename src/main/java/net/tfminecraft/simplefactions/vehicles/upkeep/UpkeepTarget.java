package net.tfminecraft.simplefactions.vehicles.upkeep;

import net.tfminecraft.simplefactions.vehicles.owners.OwnerMeta;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

/** A vehicle as the upkeep rules need it: its ID, type and VehicleFramework owner value. */
public record UpkeepTarget(String vehicle, String typeId, String owner) {
    public static UpkeepTarget of(ActiveVehicle vehicle) {
        if (vehicle == null || vehicle.getUUID() == null) return null;
        return new UpkeepTarget(vehicle.getUUID(), vehicle.getId(),
                vehicle.getOwnerData() == null ? null : vehicle.getOwnerData().getOwner());
    }

    /** The vehicle from VehicleFramework's store, loaded or not; null when it is gone or unreadable. */
    public static UpkeepTarget read(String vehicle) {
        OwnerMeta meta = VehicleOwnerWrites.read(vehicle).orElse(null);
        return meta == null ? null : new UpkeepTarget(vehicle, meta.typeId(), meta.currentOwner());
    }
}
