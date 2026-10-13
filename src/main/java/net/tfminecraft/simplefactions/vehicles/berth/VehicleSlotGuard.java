package net.tfminecraft.simplefactions.vehicles.berth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

/**
 * Personal vehicle limits are per account, counted from personal registry rows. A row
 * whose account cannot be worked out counts against the account being checked, and a
 * lookup error refuses.
 */
public final class VehicleSlotGuard {
    private VehicleSlotGuard() {}

    public static CanBuildResult checkCanBuild(Player player, String vehicleTypeId) {
        if (player == null) {
            return CanBuildResult.UNKNOWN_TYPE;
        }
        return checkCanBuild(
                player.getUniqueId(),
                vehicleTypeId,
                net.tfminecraft.simplefactions.SimpleFactions.getVehicleRegistry());
    }

    public static CanBuildResult checkCanBuild(UUID account, String vehicleTypeId, PlayerVehicleRegistry registry) {
        if (account == null || registry == null) {
            return CanBuildResult.UNKNOWN_TYPE;
        }
        if (vehicleTypeId == null || vehicleTypeId.isEmpty()) {
            return CanBuildResult.UNKNOWN_TYPE;
        }
        if (!VehiclesConfigLoader.isKnownType(vehicleTypeId)) {
            return CanBuildResult.UNKNOWN_TYPE;
        }

        List<String> personal;
        try {
            personal = personalTypes(account, registry);
        } catch (RuntimeException | LinkageError error) {
            return CanBuildResult.UNAVAILABLE;
        }

        long ofType = personal.stream().filter(vehicleTypeId::equalsIgnoreCase).count();
        if (ofType >= VehiclesConfigLoader.getPerPersonLimit(vehicleTypeId)) {
            return CanBuildResult.PER_TYPE_LIMIT;
        }

        if (VehiclesConfigLoader.ignoresPersonalSlotLimit(vehicleTypeId)) {
            return CanBuildResult.OK;
        }

        int totalLimit = VehiclesConfigLoader.getPersonalSlotLimit();
        if (totalLimit <= 0) {
            return CanBuildResult.OK;
        }

        long counted = personal.stream().filter(type -> !VehiclesConfigLoader.ignoresPersonalSlotLimit(type)).count();
        if (counted >= totalLimit) {
            return CanBuildResult.TOTAL_LIMIT;
        }

        return CanBuildResult.OK;
    }

    /** Types of the personal vehicles counted against this account. */
    static List<String> personalTypes(UUID account, PlayerVehicleRegistry registry) {
        List<String> types = new ArrayList<>();
        for (PlayerVehicleRecord record : registry.getAll()) {
            if (record.getMode() != OwnershipMode.PERSONAL) continue;
            UUID owner = accountOf(record);
            if (owner == null || owner.equals(account)) types.add(record.getVehicleTypeId());
        }
        return types;
    }

    /** The row's cached account, else the account that plays its character; null when unknown. */
    public static UUID accountOf(PlayerVehicleRecord record) {
        if (record.getAccountUuid() != null) return record.getAccountUuid();
        if (record.getPlayerUuid() == null) return null;
        return Members.accountId(Members.key(record.getPlayerUuid())).orElse(null);
    }

    public enum CanBuildResult {
        OK,
        TOTAL_LIMIT,
        PER_TYPE_LIMIT,
        UNKNOWN_TYPE,
        /** The count could not be read, so nothing new is allowed. */
        UNAVAILABLE
    }
}
