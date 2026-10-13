package net.tfminecraft.simplefactions.vehicles.berth;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.owners.LeaderStamps;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Reason;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

/** Keeps faction vehicles stamped for their faction's leader character. */
public final class InstallationVehicleOwnerSync {
    private final PlayerVehicleRegistry registry;

    public InstallationVehicleOwnerSync(PlayerVehicleRegistry registry) {
        this.registry = registry;
    }

    /**
     * A vehicle joining a faction: stamped for the faction's leader, with the old owner's
     * whitelist entries dropped and the whitelist switch left as it was.
     */
    public static Result stampForFaction(String vehicleUuid, Faction faction) {
        return VehicleOwnerWrites.write(vehicleUuid, LeaderStamps.expectedStamp(faction), Reason.TRANSFER);
    }

    /** On spawn, a faction vehicle with a stale stamp is restamped. */
    public void syncIfBerthed(ActiveVehicle vehicle) {
        if (vehicle == null || vehicle.getUUID() == null || !LeaderStamps.enabled()) {
            return;
        }
        PlayerVehicleRecord record = registry.getByVehicleUuid(vehicle.getUUID()).orElse(null);
        String current = vehicle.getOwnerData() == null ? null : vehicle.getOwnerData().getOwner();
        if (!LeaderStamps.isStale(record, current)) {
            return;
        }
        Result result = VehicleOwnerWrites.write(vehicle.getUUID(), LeaderStamps.expected(record), Reason.RESTAMP);
        if (result != Result.OK && SimpleFactions.getInstance() != null) {
            SimpleFactions.getInstance().getLogger().warning(
                    "Could not restamp faction vehicle " + vehicle.getUUID() + " on spawn: " + result);
        }
    }
}
