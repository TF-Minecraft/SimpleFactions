package me.Plugins.SimpleFactions.vehicles.berth;


import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.registry.OwnershipMode;
import me.Plugins.SimpleFactions.vehicles.registry.VehicleOwnershipQueries;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;

import me.Plugins.SimpleFactions.Loaders.InstallationConfigLoader;
import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationBounds;
import net.tfminecraft.VehicleFramework.Data.OwnerData;
import net.tfminecraft.VehicleFramework.Vehicles.ActiveVehicle;

public final class InstallationVehicleService {
    private final PlayerVehicleRegistry registry;
    private final InstallationVehicleOwnerSync ownerSync;

    public InstallationVehicleService(
            PlayerVehicleRegistry registry,
            InstallationVehicleOwnerSync ownerSync) {
        this.registry = registry;
        this.ownerSync = ownerSync;
    }

    public CanRegisterResult canRegister(Installation installation, ActiveVehicle vehicle) {
        return canRegister(installation, adapt(vehicle));
    }

    CanRegisterResult canRegister(Installation installation, VehicleBerthTarget vehicle) {
        if (vehicle == null || vehicle.getVehicleUuid() == null) {
            return CanRegisterResult.NOT_IN_REGISTRY;
        }

        if (registry.isBerthed(vehicle.getVehicleUuid())) {
            return CanRegisterResult.ALREADY_BERTHED;
        }

        OwnerData ownerData = vehicle.getOwnerData();
        String owner = ownerData == null ? null : ownerData.getOwner();
        if (!VehicleOwnershipQueries.isPlayerOwner(owner)) {
            return CanRegisterResult.NOT_IN_REGISTRY;
        }

        if (VehicleInstallationLockService.isVehicleLocked(installation.getId(), Instant.now())) {
            return CanRegisterResult.REPAIR_LOCKED;
        }

        String vehicleTypeId = vehicle.getVehicleTypeId();
        Optional<String> categoryId = VehiclesConfigLoader.getCategoryId(vehicleTypeId);
        if (categoryId.isEmpty()) {
            return CanRegisterResult.UNKNOWN_TYPE;
        }

        int capacity = InstallationConfigLoader.getCategorySlotCapacity(
                installation.getKind(),
                categoryId.get());
        if (capacity <= 0) {
            return CanRegisterResult.UNSUPPORTED_CATEGORY;
        }

        int vehicleSize = VehiclesConfigLoader.getSize(vehicleTypeId);
        int used = registry.usedCategorySize(installation.getId(), categoryId.get());
        if (used + vehicleSize > capacity) {
            return CanRegisterResult.NO_CAPACITY;
        }

        Location vehicleLocation = vehicle.getLocation();
        if (!InstallationBounds.isWithinRadius(installation, vehicleLocation)) {
            return CanRegisterResult.OUT_OF_RADIUS;
        }

        if (!InstallationBounds.isCorrectProvince(installation, vehicleLocation)) {
            return CanRegisterResult.WRONG_PROVINCE;
        }

        return CanRegisterResult.OK;
    }

    public void register(
            Installation installation,
            ActiveVehicle vehicle,
            Faction faction,
            UUID originalOwnerUuid) {
        register(installation, adapt(vehicle), faction, originalOwnerUuid);
    }

    void register(
            Installation installation,
            VehicleBerthTarget vehicle,
            Faction faction,
            UUID originalOwnerUuid) {
        registry.register(new PlayerVehicleRecord(
                originalOwnerUuid,
                vehicle.getVehicleUuid(),
                vehicle.getVehicleTypeId(),
                OwnershipMode.INSTALLATION,
                installation.getId()));
        ownerSync.applyLeaderOwner(vehicle.getOwnerData(), faction);
        SimpleFactions.getInstance().saveVehicleRegistry();
    }

    private static VehicleBerthTarget adapt(ActiveVehicle vehicle) {
        if (vehicle == null) {
            return null;
        }
        return new VehicleBerthTarget() {
            @Override
            public String getVehicleUuid() {
                return vehicle.getUUID();
            }

            @Override
            public String getVehicleTypeId() {
                return vehicle.getId();
            }

            @Override
            public Location getLocation() {
                return vehicle.getLocation();
            }

            @Override
            public OwnerData getOwnerData() {
                return vehicle.getOwnerData();
            }
        };
    }

    interface VehicleBerthTarget {
        String getVehicleUuid();

        String getVehicleTypeId();

        Location getLocation();

        OwnerData getOwnerData();
    }

    public enum CanRegisterResult {
        OK,
        NOT_IN_REGISTRY,
        ALREADY_BERTHED,
        UNKNOWN_TYPE,
        UNSUPPORTED_CATEGORY,
        NO_CAPACITY,
        OUT_OF_RADIUS,
        WRONG_PROVINCE,
        REPAIR_LOCKED
    }

    public enum TryRegisterResult {
        SKIP,
        ALLOWED,
        FAIL_UNKNOWN_TYPE,
        FAIL_SLOT_LIMIT
    }
}
