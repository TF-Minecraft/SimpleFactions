package me.Plugins.SimpleFactions.vehicles.berth;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;
import net.tfminecraft.VehicleFramework.Data.StoredVehicleMeta;
import net.tfminecraft.VehicleFramework.VehicleFramework;
import net.tfminecraft.VehicleFramework.Vehicles.ActiveVehicle;

public final class VehicleFindMessages {
    private VehicleFindMessages() {}

    public static String notLeader() {
        return VehicleTransferMessages.notLeader();
    }

    public static String usage() {
        return "§cUsage: §e/faction findvehicles <installation id>";
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

    public static void sendInstallationVehicles(Player player, Installation installation) {
        player.sendMessage(header(installation));
        var records =
                me.Plugins.SimpleFactions.SimpleFactions.getVehicleRegistry()
                        .getByInstallationId(installation.getId());
        if (records.isEmpty()) {
            player.sendMessage(noneAtInstallation(installation));
            return;
        }
        for (var record : records) {
            String name = resolveVehicleName(record.getVehicleUuid());
            Optional<Location> location =
                    VehicleFramework.getVehicleManager().getOfflineLocation(record.getVehicleUuid());
            player.sendMessage(vehicleLine(name, location));
        }
    }
}
