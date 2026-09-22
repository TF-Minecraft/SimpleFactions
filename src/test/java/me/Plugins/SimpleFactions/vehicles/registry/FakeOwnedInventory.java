package me.Plugins.SimpleFactions.vehicles.registry;


import me.Plugins.SimpleFactions.vehicles.registry.VehicleOwnershipQueries;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.tfminecraft.VehicleFramework.Data.OwnedVehicleSummary;

public final class FakeOwnedInventory implements VehicleOwnershipQueries.OwnedInventory {
    private final List<OwnedVehicleSummary> vehicles = new ArrayList<>();

    public FakeOwnedInventory add(String uuid, String typeId, String owner) {
        vehicles.add(new OwnedVehicleSummary(uuid, typeId, typeId, Optional.empty(), false, owner));
        return this;
    }

    @Override
    public List<OwnedVehicleSummary> listByOwner(String ownerEntry) {
        List<OwnedVehicleSummary> out = new ArrayList<>();
        for (OwnedVehicleSummary vehicle : vehicles) {
            if (ownerEntry != null && ownerEntry.equalsIgnoreCase(vehicle.getOwner())) {
                out.add(vehicle);
            }
        }
        return out;
    }

    @Override
    public List<OwnedVehicleSummary> listAllPlayerOwned() {
        return new ArrayList<>(vehicles);
    }
}
