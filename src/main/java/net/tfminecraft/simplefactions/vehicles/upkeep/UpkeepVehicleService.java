package net.tfminecraft.simplefactions.vehicles.upkeep;

import java.util.UUID;
import java.util.function.BooleanSupplier;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.PersonalOwner;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Reason;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.tlibs.character.CharacterRef;

/**
 * Upkeep-only vehicles: kept by a faction or guild that pays their upkeep and decides what
 * happens to them, but never used for war. The registry row goes first and is put back if
 * VehicleFramework refuses the new owner, as for the pool and installation berths.
 */
public final class UpkeepVehicleService {
    public enum AddResult {
        OK,
        NO_HOLDER,
        NOT_FOUND,
        NOT_OWNED,
        NOT_MEMBER,
        ALREADY_UPKEEP,
        ALREADY_IN_POOL,
        ALREADY_BERTHED,
        UNKNOWN_TYPE,
        AT_CAP
    }

    public enum GiveStatus {
        OK,
        NOT_LEADER,
        NOT_UPKEEP_VEHICLE,
        NOT_MEMBER,
        NO_PERSONAL_ROOM,
        UNKNOWN_TYPE,
        OWNERSHIP_UNAVAILABLE,
        SAVE_FAILED
    }

    public record GiveOutcome(GiveStatus status, CanBuildResult slotFailure, String vehicleTypeId) {}

    private final PlayerVehicleRegistry registry;
    private final BooleanSupplier saver;
    private final PersonalOwner personalOwner;

    public UpkeepVehicleService(PlayerVehicleRegistry registry) {
        this(registry, UpkeepVehicleService::saveRegistry, FactionVehicleReleaseService::assignFrameworkOwner);
    }

    UpkeepVehicleService(PlayerVehicleRegistry registry, BooleanSupplier saver, PersonalOwner personalOwner) {
        this.registry = registry;
        this.saver = saver;
        this.personalOwner = personalOwner;
    }

    private static boolean saveRegistry() {
        SimpleFactions plugin = SimpleFactions.getInstance();
        return plugin == null || plugin.saveVehicleRegistry();
    }

    public PlayerVehicleRegistry registry() {
        return registry;
    }

    /** Whether the vehicle may become one of the holder's upkeep-only vehicles. */
    public AddResult canAdd(UpkeepHolder holder, UpkeepTarget vehicle) {
        if (holder == null || !holder.exists()) return AddResult.NO_HOLDER;
        if (vehicle == null || vehicle.vehicle() == null) return AddResult.NOT_FOUND;
        PlayerVehicleRecord existing = registry.getByVehicleUuid(vehicle.vehicle()).orElse(null);
        if (existing != null && existing.getMode() == OwnershipMode.UPKEEP) return AddResult.ALREADY_UPKEEP;
        if (existing != null && existing.getMode() == OwnershipMode.POOL) return AddResult.ALREADY_IN_POOL;
        if (existing != null && existing.getMode() == OwnershipMode.INSTALLATION) return AddResult.ALREADY_BERTHED;
        UUID owner = OwnerTags.characterId(vehicle.owner()).orElse(null);
        if (owner == null) return AddResult.NOT_OWNED;
        if (!holder.isMember(Members.key(owner))) return AddResult.NOT_MEMBER;
        if (!VehiclesConfigLoader.isKnownType(vehicle.typeId())) return AddResult.UNKNOWN_TYPE;
        if (holder.vehicles(registry).size() >= holder.cap()) return AddResult.AT_CAP;
        return AddResult.OK;
    }

    /** Moves the vehicle in; false (and nothing changed) when it could not be saved or stamped. */
    public boolean register(UpkeepHolder holder, UpkeepTarget vehicle, UUID previousOwner) {
        PlayerVehicleRecord previous = registry.getByVehicleUuid(vehicle.vehicle()).orElse(null);
        registry.register(holder.row(previousOwner, vehicle.vehicle(), vehicle.typeId()));
        if (!saver.getAsBoolean()) {
            restore(vehicle.vehicle(), previous);
            return false;
        }
        // The leader's own vehicle keeps its owner and whitelist; anyone else's drops the old owner's entries.
        String stamp = holder.leaderStamp();
        if (!stamp.equals(vehicle.owner())
                && VehicleOwnerWrites.write(vehicle.vehicle(), stamp, Reason.TRANSFER) != Result.OK) {
            restore(vehicle.vehicle(), previous);
            if (!saver.getAsBoolean()) {
                logSevere("Vehicle " + vehicle.vehicle() + " was restored in memory after its upkeep-only stamp"
                        + " failed, but the registry could not be saved.");
            }
            return false;
        }
        return true;
    }

    /** The leader hands one of the holder's vehicles to a member, as that member's personal vehicle. */
    public GiveOutcome give(UpkeepHolder holder, String leaderKey, CharacterRef recipient, String vehicle) {
        if (holder == null || !holder.isLeader(leaderKey)) return outcome(GiveStatus.NOT_LEADER, null, null);
        PlayerVehicleRecord record = vehicle == null ? null : registry.getByVehicleUuid(vehicle).orElse(null);
        if (!holder.holds(record)) return outcome(GiveStatus.NOT_UPKEEP_VEHICLE, null, null);
        String type = record.getVehicleTypeId();
        if (recipient == null) return outcome(GiveStatus.OWNERSHIP_UNAVAILABLE, null, type);
        if (!holder.isMember(Members.key(recipient))) return outcome(GiveStatus.NOT_MEMBER, null, type);
        CanBuildResult slot = VehicleSlotGuard.checkCanBuild(recipient.ownerId(), type, registry);
        if (slot == CanBuildResult.UNKNOWN_TYPE) return outcome(GiveStatus.UNKNOWN_TYPE, slot, type);
        if (slot != CanBuildResult.OK) return outcome(GiveStatus.NO_PERSONAL_ROOM, slot, type);
        // The recipient becomes the payer before VehicleFramework's owner changes.
        registry.register(new PlayerVehicleRecord(recipient.characterId(), vehicle, type,
                OwnershipMode.PERSONAL, null, null, recipient.ownerId()));
        if (!saver.getAsBoolean()) {
            registry.register(record);
            return outcome(GiveStatus.SAVE_FAILED, null, type);
        }
        if (!personalOwner.assign(vehicle, recipient.characterId())) {
            registry.register(record);
            if (!saver.getAsBoolean()) {
                logSevere("Vehicle " + vehicle + " (" + type + ") was restored to " + holder.name()
                        + " in memory, but the registry could not be saved.");
                return outcome(GiveStatus.SAVE_FAILED, null, type);
            }
            return outcome(GiveStatus.OWNERSHIP_UNAVAILABLE, null, type);
        }
        return outcome(GiveStatus.OK, null, type);
    }

    private void restore(String vehicle, PlayerVehicleRecord previous) {
        registry.unregister(vehicle);
        registry.register(previous);
    }

    /** What the holder pays each day for its upkeep-only vehicles. */
    public static double dailyUpkeep(PlayerVehicleRegistry registry, UpkeepHolder holder) {
        double total = 0.0;
        if (registry == null || holder == null) return total;
        for (PlayerVehicleRecord record : holder.vehicles(registry)) {
            total += VehiclesConfigLoader.getUpkeep(record.getVehicleTypeId());
        }
        return total;
    }

    /** Live lookup for a player guild's ledger line. Zero when the plugin is not running. */
    public static double dailyUpkeepOfGuild(String guildId) {
        if (SimpleFactions.plugin == null || guildId == null) return 0.0;
        return dailyUpkeep(SimpleFactions.getVehicleRegistry(), new UpkeepHolder(UpkeepHolder.Kind.GUILD, guildId));
    }

    private static GiveOutcome outcome(GiveStatus status, CanBuildResult slot, String type) {
        return new GiveOutcome(status, slot, type);
    }

    private static void logSevere(String message) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin != null) plugin.getLogger().severe(message);
    }
}
