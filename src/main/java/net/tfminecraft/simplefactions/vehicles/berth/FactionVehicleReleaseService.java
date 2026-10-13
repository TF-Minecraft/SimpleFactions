package net.tfminecraft.simplefactions.vehicles.berth;

import net.tfminecraft.simplefactions.identity.Members;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;


import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Reason;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

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
        boolean assign(String vehicleUuid, UUID character);
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

    /** The leader takes the vehicle for the character they are playing now. */
    public Outcome take(Faction faction, CharacterRef leader, String vehicleUuid) {
        return release(faction, Members.key(leader), leader, vehicleUuid, null, true);
    }

    public Outcome takeFromInstallation(
            Faction faction,
            CharacterRef leader,
            String installationId,
            String vehicleUuid) {
        return release(faction, Members.key(leader), leader, vehicleUuid, installationId, true);
    }

    /** {@code leaderKey} is the leader's member key; {@code recipient} was captured when the gift was offered. */
    public Outcome give(Faction faction, String leaderKey, CharacterRef recipient, String vehicleUuid) {
        return release(faction, leaderKey, recipient, vehicleUuid, null, true);
    }

    public Outcome evaluateGive(
            Faction faction,
            String leaderKey,
            CharacterRef recipient,
            String vehicleUuid) {
        return release(faction, leaderKey, recipient, vehicleUuid, null, false);
    }

    private Outcome release(
            Faction faction,
            String leaderKey,
            CharacterRef recipient,
            String vehicleUuid,
            String requiredInstallationId,
            boolean apply) {
        if (faction == null
                || leaderKey == null
                || leaderKey.isBlank()
                || vehicleUuid == null
                || vehicleUuid.isBlank()) {
            return outcome(Status.NOT_FACTION_VEHICLE, null, null);
        }
        if (faction.getLeader() == null || !faction.getLeader().equals(leaderKey)) {
            return outcome(Status.NOT_LEADER, null, null);
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
                && VehicleInstallationLockService.isInstallationLocked(
                        faction.getInstallationHandler().getById(record.getInstallationId()), Instant.now())) {
            return outcome(Status.INSTALLATION_LOCKED, null, typeId);
        }
        if (recipient == null) {
            return outcome(Status.OWNERSHIP_UNAVAILABLE, null, typeId);
        }

        CanBuildResult slot = VehicleSlotGuard.checkCanBuild(recipient.ownerId(), typeId, registry);
        if (slot == CanBuildResult.UNKNOWN_TYPE) {
            return outcome(Status.UNKNOWN_TYPE, slot, typeId);
        }
        if (slot != CanBuildResult.OK) {
            return outcome(Status.NO_PERSONAL_ROOM, slot, typeId);
        }

        if (!apply) {
            return outcome(Status.OK, null, typeId);
        }
        // Record the recipient as payer before changing VehicleFramework's owner.
        registry.register(new PlayerVehicleRecord(recipient.characterId(), vehicleUuid, typeId,
                OwnershipMode.PERSONAL, null, null, recipient.ownerId()));
        if (!registrySaver.getAsBoolean()) {
            // The file still holds the record, so restoring it in memory is enough.
            registry.register(record);
            return outcome(Status.SAVE_FAILED, null, typeId);
        }
        if (!personalOwner.assign(vehicleUuid, recipient.characterId())) {
            registry.register(record);
            if (!registrySaver.getAsBoolean()) {
                logSevere("Vehicle " + vehicleUuid + " (" + typeId + ") was restored to faction "
                        + faction.getId() + " in memory, but the registry could not be saved."
                        + " Save the vehicle registry before the next restart or the faction loses it.");
                return outcome(Status.SAVE_FAILED, null, typeId);
            }
            return outcome(Status.OWNERSHIP_UNAVAILABLE, null, typeId);
        }
        return outcome(Status.OK, null, typeId);
    }

    private static boolean belongsToFaction(
            Faction faction,
            PlayerVehicleRecord record,
            String requiredInstallationId) {
        if (record.getMode() == OwnershipMode.POOL) {
            return requiredInstallationId == null
                    && faction.getId() != null
                    && faction.getId().equalsIgnoreCase(record.getFactionId());
        }
        if (record.getMode() != OwnershipMode.INSTALLATION) {
            return false;
        }
        if (requiredInstallationId != null && !requiredInstallationId.equals(record.getInstallationId())) {
            return false;
        }
        // Installation ids are only unique within a faction, so match the record's own faction.
        Faction holder = FactionVehiclePoolService.payingFaction(record);
        return holder != null && holder.getId() != null && holder.getId().equalsIgnoreCase(faction.getId());
    }

    private static Outcome outcome(Status status, CanBuildResult slotFailure, String vehicleTypeId) {
        return new Outcome(status, slotFailure, vehicleTypeId);
    }

    /** Gives a released faction vehicle to the character, emptying its crew list. */
    public static boolean assignFrameworkOwner(String vehicleUuid, UUID character) {
        return character != null
                && VehicleOwnerWrites.write(vehicleUuid, OwnerTags.character(character), Reason.RELEASE) == Result.OK;
    }

    private static void logSevere(String message) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin != null) {
            plugin.getLogger().severe(message);
        }
    }
}
