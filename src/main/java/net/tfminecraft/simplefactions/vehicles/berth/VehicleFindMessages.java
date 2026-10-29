package net.tfminecraft.simplefactions.vehicles.berth;

import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.vehicleframework.data.StoredVehicleMeta;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

public final class VehicleFindMessages {
    private VehicleFindMessages() {}

    public static String notLeader() {
        return "§cYou need to be a faction leader to find faction vehicles.";
    }

    public static String usage() {
        return "§cUsage: §e/faction findvehicles <pool|personal|installation id>";
    }

    public static String unknownInstallation() {
        return VehicleTransferMessages.unknownInstallation();
    }

    public static String header(Installation installation) {
        return "§bVehicles at " + installation.getName() + ":";
    }

    public static String noneAtInstallation(Installation installation) {
        return "§7No vehicles berthed at " + installation.getName() + ".";
    }

    public static String vehicleLine(String displayName, Optional<Location> location) {
        return "§e" + displayName + " §7- " + formatLocation(location);
    }

    public static String formatLocation(Optional<Location> location) {
        if (location == null || location.isEmpty()) {
            return "§7location unknown (stored)";
        }
        Location loc = location.get();
        if (loc.getWorld() == null) {
            return "§7location unknown (stored)";
        }
        return String.format(
                "§f%s %.0f, %.0f, %.0f",
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ());
    }

    public static Installation resolveInstallation(InstallationHandler handler, String query) {
        if (handler == null || query == null || query.isBlank()) {
            return null;
        }
        Installation byId = handler.getById(query);
        if (byId != null) {
            return byId;
        }
        for (Installation installation : handler.getAll()) {
            if (installation.getName().equalsIgnoreCase(query)) {
                return installation;
            }
        }
        return null;
    }

    public static String resolveVehicleName(String vehicleUuid) {
        ActiveVehicle live = VehicleFramework.getVehicleManager().get(vehicleUuid);
        if (live != null) {
            return live.getName();
        }
        Optional<StoredVehicleMeta> stored =
                VehicleFramework.getVehicleManager().readStoredVehicle(vehicleUuid);
        if (stored.isPresent()) {
            String name = stored.get().getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
            return stored.get().getTypeId();
        }
        return vehicleUuid;
    }

    public static void sendPersonalVehicles(Player player) {
        var character = VehicleOwnershipQueries.characterId(player);
        var records = SimpleFactions.getVehicleRegistry().getAll().stream()
                .filter(record -> record.getMode() == OwnershipMode.PERSONAL)
                .filter(record -> record.getPlayerUuid() != null && record.getPlayerUuid().equals(character))
                .toList();
        player.sendMessage("§bYour personal vehicles:");
        sendVehicles(player, records, "§7You have no personal vehicles.");
    }

    public static void sendPoolVehicles(Player player, String factionId) {
        player.sendMessage("§bVehicles in the faction pool:");
        sendVehicles(player, SimpleFactions.getVehicleRegistry().getPoolVehicles(factionId),
                "§7No vehicles in the faction pool.");
    }

    private static void sendVehicles(Player player, List<PlayerVehicleRecord> records, String emptyMessage) {
        if (records.isEmpty()) {
            player.sendMessage(emptyMessage);
            return;
        }
        for (var record : records) {
            String name = resolveVehicleName(record.getVehicleUuid());
            Optional<Location> location =
                    VehicleFramework.getVehicleManager().getOfflineLocation(record.getVehicleUuid());
            player.sendMessage(vehicleLine(name, location));
        }
    }

    public static void sendInstallationVehicles(Player player, Installation installation) {
        player.sendMessage(header(installation));
        var records =
                net.tfminecraft.simplefactions.SimpleFactions.getVehicleRegistry()
                        .getByInstallation(
                                net.tfminecraft.simplefactions.installation.InstallationOwners.ownerIdOf(installation),
                                installation.getId());
        sendVehicles(player, records, noneAtInstallation(installation));
    }
}
