package net.tfminecraft.simplefactions.vehicles.pool;

import java.util.Optional;
import java.util.UUID;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleOwnerSync;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleCategoryRules;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.vehicleframework.data.OwnerData;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

public final class FactionVehiclePoolService {
    public static final String ARTILLERY_CATEGORY = "artillery";
    public static final String ARTILLERY_REGIMENT_ID = "artillery";
    public static final String POOL_TARGET = "pool";

    private final PlayerVehicleRegistry registry;
    private final InstallationVehicleOwnerSync ownerSync;

    public FactionVehiclePoolService(
            PlayerVehicleRegistry registry,
            InstallationVehicleOwnerSync ownerSync) {
        this.registry = registry;
        this.ownerSync = ownerSync;
    }

    public static boolean isPoolTarget(String installationId) {
        return installationId != null && installationId.equalsIgnoreCase(POOL_TARGET);
    }

    public static int artillerySlots(Faction faction) {
        if (faction == null) {
            return 0;
        }
        Military military = faction.getMilitary();
        if (military == null) {
            return 0;
        }
        Regiment regiment = military.getRegiment(ARTILLERY_REGIMENT_ID);
        if (regiment == null) {
            return 0;
        }
        return Math.max(0, regiment.getCurrentSlots());
    }

    /** Upkeep the faction bank pays each day for its pool and installation vehicles. */
    public static double dailyUpkeep(PlayerVehicleRegistry registry, String factionId) {
        if (registry == null || factionId == null || factionId.isBlank()) {
            return 0.0;
        }
        double total = 0.0;
        for (PlayerVehicleRecord record : registry.getAll()) {
            if (paidBy(record, factionId)) {
                total += VehiclesConfigLoader.getUpkeep(record.getVehicleTypeId());
            }
        }
        return total;
    }

    private static boolean paidBy(PlayerVehicleRecord record, String factionId) {
        if (record.getMode() == OwnershipMode.POOL) {
            return factionId.equalsIgnoreCase(record.getFactionId());
        }
        Faction payer = record.getMode() == OwnershipMode.INSTALLATION ? payingFaction(record) : null;
        return payer != null && factionId.equalsIgnoreCase(payer.getId());
    }

    /** The faction that pays a registered vehicle's upkeep: its pool's faction or its installation's owner. */
    public static Faction payingFaction(PlayerVehicleRecord record) {
        if (record == null || FactionManager.factions == null) {
            return null;
        }
        if (record.getMode() == OwnershipMode.POOL) {
            return record.getFactionId() == null ? null : FactionManager.getByString(record.getFactionId());
        }
        if (record.getMode() == OwnershipMode.INSTALLATION && record.getInstallationId() != null) {
            for (Faction faction : FactionManager.factions) {
                if (faction != null
                        && faction.getInstallationHandler() != null
                        && faction.getInstallationHandler().getById(record.getInstallationId()) != null) {
                    return faction;
                }
            }
        }
        return null;
    }

    /** Live lookup for the faction ledger. Zero when the plugin is not running. */
    public static double dailyUpkeepOf(String factionId) {
        if (SimpleFactions.plugin == null) {
            return 0.0;
        }
        return dailyUpkeep(SimpleFactions.getVehicleRegistry(), factionId);
    }

    public CanAddResult canAdd(Faction faction, ActiveVehicle vehicle) {
        return canAdd(faction, adapt(vehicle));
    }

    public CanAddResult canAdd(Faction faction, PoolTarget vehicle) {
        if (faction == null || vehicle == null || vehicle.getVehicleUuid() == null) {
            return CanAddResult.NOT_OWNED;
        }

        Optional<PlayerVehicleRecord> existing = registry.getByVehicleUuid(vehicle.getVehicleUuid());
        if (existing.isPresent() && existing.get().getMode() == OwnershipMode.POOL) {
            return CanAddResult.ALREADY_IN_POOL;
        }
        if (existing.isPresent() && existing.get().getMode() == OwnershipMode.INSTALLATION) {
            return CanAddResult.ALREADY_BERTHED;
        }

        OwnerData ownerData = vehicle.getOwnerData();
        String owner = ownerData == null ? null : ownerData.getOwner();
        if (!VehicleOwnershipQueries.isPlayerOwner(owner)) {
            return CanAddResult.NOT_OWNED;
        }

        String vehicleTypeId = vehicle.getVehicleTypeId();
        if (!VehiclesConfigLoader.isKnownType(vehicleTypeId)) {
            return CanAddResult.UNKNOWN_TYPE;
        }

        if (VehicleCategoryRules.isBerthableType(vehicleTypeId)) {
            return CanAddResult.MUST_BERTH;
        }

        if (isArtillery(vehicleTypeId)
                && registry.countPoolCategory(faction.getId(), ARTILLERY_CATEGORY) >= artillerySlots(faction)) {
            return CanAddResult.NO_ARTILLERY_CAPACITY;
        }

        return CanAddResult.OK;
    }

    public void register(Faction faction, ActiveVehicle vehicle, UUID originalOwnerUuid) {
        register(faction, adapt(vehicle), originalOwnerUuid);
    }

    public void register(Faction faction, PoolTarget vehicle, UUID originalOwnerUuid) {
        registry.register(new PlayerVehicleRecord(
                originalOwnerUuid,
                vehicle.getVehicleUuid(),
                vehicle.getVehicleTypeId(),
                OwnershipMode.POOL,
                null,
                faction.getId()));
        ownerSync.applyLeaderOwner(vehicle.getOwnerData(), faction);
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin != null) {
            plugin.saveVehicleRegistry();
        }
    }

    public static boolean isArtillery(String vehicleTypeId) {
        return VehiclesConfigLoader.getCategoryId(vehicleTypeId)
                .map(category -> category.equalsIgnoreCase(ARTILLERY_CATEGORY))
                .orElse(false);
    }

    private static PoolTarget adapt(ActiveVehicle vehicle) {
        if (vehicle == null) {
            return null;
        }
        return new PoolTarget() {
            @Override
            public String getVehicleUuid() {
                return vehicle.getUUID();
            }

            @Override
            public String getVehicleTypeId() {
                return vehicle.getId();
            }

            @Override
            public OwnerData getOwnerData() {
                return vehicle.getOwnerData();
            }
        };
    }

    public interface PoolTarget {
        String getVehicleUuid();

        String getVehicleTypeId();

        OwnerData getOwnerData();
    }

    public enum CanAddResult {
        OK,
        NOT_OWNED,
        ALREADY_IN_POOL,
        ALREADY_BERTHED,
        UNKNOWN_TYPE,
        MUST_BERTH,
        NO_ARTILLERY_CAPACITY
    }
}
