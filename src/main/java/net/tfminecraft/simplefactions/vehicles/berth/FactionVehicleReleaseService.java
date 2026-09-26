package net.tfminecraft.simplefactions.vehicles.berth;

import java.time.Instant;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.data.StoredVehicleMeta;
import net.tfminecraft.vehicleframework.database.VehiclePersistence;
import net.tfminecraft.vehicleframework.database.VehicleSnapshot;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

public final class FactionVehicleReleaseService {
    private final PlayerVehicleRegistry registry;
    private final PersonalOwner personalOwner;
    private final BooleanSupplier registrySaver;

    public FactionVehicleReleaseService(PlayerVehicleRegistry registry) {
        this(registry, FactionVehicleReleaseService::assignFrameworkOwner);
    }

    FactionVehicleReleaseService(PlayerVehicleRegistry registry, PersonalOwner personalOwner) {
        this(registry, personalOwner, FactionVehicleReleaseService::saveRegistry);
    }

    FactionVehicleReleaseService(
            PlayerVehicleRegistry registry,
            PersonalOwner personalOwner,
            BooleanSupplier registrySaver) {
        this.registry = registry;
        this.personalOwner = personalOwner;
        this.registrySaver = registrySaver;
    }

    private static boolean saveRegistry() {
        SimpleFactions plugin = SimpleFactions.getInstance();
        return plugin == null || plugin.saveVehicleRegistry();
    }

    public interface PersonalOwner {
        boolean assign(String vehicleUuid, String playerName);
    }

    public enum Status {
        OK,
        NOT_LEADER,
        NOT_FACTION_VEHICLE,
        INSTALLATION_LOCKED,
        IN_BATTLE,
        NO_PERSONAL_ROOM,
        UNKNOWN_TYPE,
        OWNERSHIP_UNAVAILABLE,
        SAVE_FAILED
    }

    public record Outcome(Status status, CanBuildResult slotFailure, String vehicleTypeId) {}

    public Outcome take(Faction faction, String leaderName, String vehicleUuid) {
        return release(faction, leaderName, leaderName, vehicleUuid, null, true);
    }

    public Outcome takeFromInstallation(
            Faction faction,
            String leaderName,
            String installationId,
            String vehicleUuid) {
        return release(faction, leaderName, leaderName, vehicleUuid, installationId, true);
    }

    public Outcome give(Faction faction, String leaderName, String recipientName, String vehicleUuid) {
        return release(faction, leaderName, recipientName, vehicleUuid, null, true);
    }

    public Outcome evaluateGive(
            Faction faction,
            String leaderName,
            String recipientName,
            String vehicleUuid) {
        return release(faction, leaderName, recipientName, vehicleUuid, null, false);
    }

    private Outcome release(
            Faction faction,
            String leaderName,
            String recipientName,
            String vehicleUuid,
            String requiredInstallationId,
            boolean apply) {
        if (faction == null
                || leaderName == null
                || leaderName.isBlank()
                || vehicleUuid == null
                || vehicleUuid.isBlank()) {
            return outcome(Status.NOT_FACTION_VEHICLE, null, null);
        }
        if (faction.getLeader() == null || !faction.getLeader().equalsIgnoreCase(leaderName)) {
            return outcome(Status.NOT_LEADER, null, null);
        }
        if (recipientName == null || recipientName.isBlank()) {
            return outcome(Status.NOT_FACTION_VEHICLE, null, null);
        }

        Optional<PlayerVehicleRecord> recordOpt = registry.getByVehicleUuid(vehicleUuid);
        if (recordOpt.isEmpty()) {
            return outcome(Status.NOT_FACTION_VEHICLE, null, null);
        }
        PlayerVehicleRecord record = recordOpt.get();
        String typeId = record.getVehicleTypeId();
        if (!belongsToFaction(faction, record, requiredInstallationId)) {
            return outcome(Status.NOT_FACTION_VEHICLE, null, typeId);
        }

        if (FactionCampaignBattleLock.blocks(faction)) {
            return outcome(Status.IN_BATTLE, null, typeId);
        }
        if (record.getMode() == OwnershipMode.INSTALLATION
                && VehicleInstallationLockService.isVehicleLocked(record.getInstallationId(), Instant.now())) {
            return outcome(Status.INSTALLATION_LOCKED, null, typeId);
        }

        CanBuildResult slot = VehicleSlotGuard.checkCanBuild(recipientName, typeId, registry);
        if (slot == CanBuildResult.UNKNOWN_TYPE) {
            return outcome(Status.UNKNOWN_TYPE, slot, typeId);
        }
        if (slot != CanBuildResult.OK) {
            return outcome(Status.NO_PERSONAL_ROOM, slot, typeId);
        }

        if (!apply) {
            return outcome(Status.OK, null, typeId);
        }
        // Drop the faction record and save it before the owner changes, so a failed save can
        // never leave a personal vehicle that still loads as a faction vehicle.
        registry.unregister(vehicleUuid);
        if (!registrySaver.getAsBoolean()) {
            registry.register(record);
            registrySaver.getAsBoolean();
            return outcome(Status.SAVE_FAILED, null, typeId);
        }
        if (!personalOwner.assign(vehicleUuid, recipientName)) {
            registry.register(record);
            registrySaver.getAsBoolean();
            return outcome(Status.OWNERSHIP_UNAVAILABLE, null, typeId);
        }
        return outcome(Status.OK, null, typeId);
    }

    private static boolean belongsToFaction(
            Faction faction,
            PlayerVehicleRecord record,
            String requiredInstallationId) {
        if (requiredInstallationId != null) {
            return record.getMode() == OwnershipMode.INSTALLATION
                    && requiredInstallationId.equals(record.getInstallationId());
        }
        if (record.getMode() == OwnershipMode.POOL) {
            return faction.getId() != null && faction.getId().equalsIgnoreCase(record.getFactionId());
        }
        if (record.getMode() == OwnershipMode.INSTALLATION) {
            if (faction.getInstallationHandler() == null || record.getInstallationId() == null) {
                return false;
            }
            return faction.getInstallationHandler().getById(record.getInstallationId()) != null;
        }
        return false;
    }

    private static Outcome outcome(Status status, CanBuildResult slotFailure, String vehicleTypeId) {
        return new Outcome(status, slotFailure, vehicleTypeId);
    }

    static boolean assignFrameworkOwner(String vehicleUuid, String playerName) {
        if (vehicleUuid == null || vehicleUuid.isBlank() || playerName == null || playerName.isBlank()) {
            return false;
        }
        String entry = VehicleOwnershipQueries.ownerEntry(playerName);
        try {
            if (assignLoaded(vehicleUuid, entry)) {
                return true;
            }
        } catch (Throwable ignored) {
            // VehicleFramework is not running in unit tests.
        }
        try {
            if (assignStored(vehicleUuid, entry)) {
                return true;
            }
            return storedOwnerMatches(vehicleUuid, entry);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean assignLoaded(String vehicleUuid, String ownerEntry) {
        ActiveVehicle vehicle = VehicleFramework.getVehicleManager().get(vehicleUuid);
        if (vehicle == null || vehicle.getOwnerData() == null) {
            return false;
        }
        vehicle.getOwnerData().setOwner(ownerEntry);
        VehiclePersistence persistence = VehiclePersistence.current();
        if (persistence != null) {
            persistence.saveLive(vehicle);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean assignStored(String vehicleUuid, String ownerEntry) {
        VehiclePersistence persistence = VehiclePersistence.current();
        if (persistence == null) {
            return false;
        }
        Optional<VehicleSnapshot> found = persistence.findLive(vehicleUuid);
        if (found.isEmpty()) {
            return false;
        }
        VehicleSnapshot snapshot = found.get();
        String payloadJson = snapshot.getPayloadJson();
        if (payloadJson == null || payloadJson.isBlank()) {
            return false;
        }
        JSONObject payload;
        try {
            payload = (JSONObject) new JSONParser().parse(payloadJson);
        } catch (Exception e) {
            return false;
        }
        payload.put("owner", ownerEntry);
        VehicleSnapshot updated = snapshot.withPayload(
                payload.toJSONString(),
                snapshot.getName(),
                ownerEntry,
                System.currentTimeMillis());
        return persistence.repository().saveLive(updated);
    }

    private static boolean storedOwnerMatches(String vehicleUuid, String ownerEntry) {
        VehiclePersistence persistence = VehiclePersistence.current();
        if (persistence == null) {
            return false;
        }
        Optional<StoredVehicleMeta> meta = persistence.readMeta(vehicleUuid);
        return meta.isPresent() && ownerEntry.equalsIgnoreCase(meta.get().getOwner());
    }
}
