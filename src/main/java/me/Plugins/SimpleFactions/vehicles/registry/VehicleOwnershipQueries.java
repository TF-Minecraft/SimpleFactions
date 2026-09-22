package me.Plugins.SimpleFactions.vehicles.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import net.tfminecraft.VehicleFramework.Data.OwnedVehicleSummary;
import net.tfminecraft.VehicleFramework.VehicleFramework;

public final class VehicleOwnershipQueries {
    public interface OwnedInventory {
        List<OwnedVehicleSummary> listByOwner(String ownerEntry);

        List<OwnedVehicleSummary> listAllPlayerOwned();
    }

    private static OwnedInventory source = new VfOwnedInventory();

    private VehicleOwnershipQueries() {}

    public static void setSourceForTests(OwnedInventory inventory) {
        source = inventory == null ? new VfOwnedInventory() : inventory;
    }

    public static String ownerEntry(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return "player_none";
        }
        return "player_" + playerName;
    }

    public static boolean isPlayerOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            return false;
        }
        if (!owner.regionMatches(true, 0, "player_", 0, 7)) {
            return false;
        }
        return !owner.equalsIgnoreCase("player_none") && owner.length() > 7;
    }

    public static String playerNameFromOwner(String owner) {
        if (!isPlayerOwner(owner)) {
            return null;
        }
        return owner.substring(7);
    }

    public static List<OwnedVehicleSummary> personalVehicles(
            String playerName, PlayerVehicleRegistry registry) {
        if (playerName == null || playerName.isBlank()) {
            return List.of();
        }
        return excludeBerthed(source.listByOwner(ownerEntry(playerName)), registry);
    }

    public static List<OwnedVehicleSummary> allPersonalVehicles(PlayerVehicleRegistry registry) {
        return excludeBerthed(source.listAllPlayerOwned(), registry);
    }

    public static int countOfType(List<OwnedVehicleSummary> personal, String vehicleTypeId) {
        if (personal == null || vehicleTypeId == null || vehicleTypeId.isEmpty()) {
            return 0;
        }
        String normalized = vehicleTypeId.toLowerCase(Locale.ROOT);
        int count = 0;
        for (OwnedVehicleSummary vehicle : personal) {
            if (vehicle.getTypeId() != null && normalized.equalsIgnoreCase(vehicle.getTypeId())) {
                count++;
            }
        }
        return count;
    }

    public static int countExcludingIgnoreLimit(List<OwnedVehicleSummary> personal) {
        if (personal == null) {
            return 0;
        }
        int count = 0;
        for (OwnedVehicleSummary vehicle : personal) {
            if (!VehiclesConfigLoader.ignoresPersonalSlotLimit(vehicle.getTypeId())) {
                count++;
            }
        }
        return count;
    }

    public static UUID resolvePlayerUuid(String playerName) {
        if (playerName == null || playerName.isBlank() || Bukkit.getServer() == null) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        return offline.getUniqueId();
    }

    public static String resolvePlayerName(UUID playerUuid) {
        if (playerUuid == null || Bukkit.getServer() == null) {
            return null;
        }
        Player online = Bukkit.getPlayer(playerUuid);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerUuid);
        return offline.getName();
    }

    private static List<OwnedVehicleSummary> excludeBerthed(
            List<OwnedVehicleSummary> owned, PlayerVehicleRegistry registry) {
        if (owned == null || owned.isEmpty()) {
            return List.of();
        }
        List<OwnedVehicleSummary> out = new ArrayList<>();
        for (OwnedVehicleSummary vehicle : owned) {
            if (vehicle == null || vehicle.getUuid() == null) {
                continue;
            }
            if (registry != null && registry.isBerthed(vehicle.getUuid())) {
                continue;
            }
            out.add(vehicle);
        }
        return out;
    }

    private static final class VfOwnedInventory implements OwnedInventory {
        @Override
        public List<OwnedVehicleSummary> listByOwner(String ownerEntry) {
            try {
                return VehicleFramework.getVehicleManager().listOwnedVehicles(ownerEntry);
            } catch (Throwable ignored) {
                return List.of();
            }
        }

        @Override
        public List<OwnedVehicleSummary> listAllPlayerOwned() {
            try {
                return VehicleFramework.getVehicleManager().listAllPlayerOwnedVehicles();
            } catch (Throwable ignored) {
                return List.of();
            }
        }
    }
}
