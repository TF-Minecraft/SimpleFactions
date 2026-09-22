package me.Plugins.SimpleFactions.vehicles;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.Plugins.SimpleFactions.vehicles.berth.VehicleTransferSessionManager.VehicleTransferSession;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleInstallationLockService;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleTransferMessages;
import me.Plugins.SimpleFactions.vehicles.maintenance.VehicleMaintenancePaySessionManager.VehicleMaintenancePaySession;
import me.Plugins.SimpleFactions.vehicles.maintenance.VehicleMaintenanceMessages;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Loaders.InstallationConfigLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Utils.Permissions;
import me.Plugins.SimpleFactions.installation.Installation;

public final class VehicleFactionCommands {
    private VehicleFactionCommands() {}

    public static void armTransfer(Player player, String installationId) {
        Faction faction = FactionManager.getByLeader(player.getName());
        if (faction == null) {
            player.sendMessage(VehicleTransferMessages.notLeader());
            return;
        }
        if (installationId == null || installationId.isBlank()) {
            player.sendMessage(VehicleMaintenanceMessages.transferUsage());
            return;
        }
        Installation installation = faction.getInstallationHandler().getById(installationId);
        if (installation == null) {
            player.sendMessage(VehicleTransferMessages.unknownInstallation());
            return;
        }
        if (!Permissions.isAdmin(player)
                && VehicleInstallationLockService.isVehicleLocked(
                        installation.getId(), java.time.Instant.now())) {
            player.sendMessage(VehicleInstallationLockService.BERTH_BLOCKED);
            return;
        }
        SimpleFactions plugin = SimpleFactions.getInstance();
        long timeoutMillis = InstallationConfigLoader.getTransferRequestTimeoutSeconds() * 1000L;
        plugin.getVehicleMaintenancePaySessionManager().clear(player.getUniqueId());
        plugin.getVehicleTransferSessionManager().put(
                player.getUniqueId(),
                new VehicleTransferSession(
                        installation.getId(),
                        System.currentTimeMillis() + timeoutMillis));
        player.sendMessage(VehicleTransferMessages.commandArmed(installation));
    }

    public static void armMaintenancePay(Player player) {
        Faction faction = FactionManager.getByLeader(player.getName());
        if (faction == null) {
            player.sendMessage(VehicleMaintenanceMessages.notLeader());
            return;
        }
        SimpleFactions plugin = SimpleFactions.getInstance();
        long timeoutMillis = InstallationConfigLoader.getTransferRequestTimeoutSeconds() * 1000L;
        plugin.getVehicleTransferSessionManager().clear(player.getUniqueId());
        plugin.getVehicleMaintenancePaySessionManager().put(
                player.getUniqueId(),
                new VehicleMaintenancePaySession(System.currentTimeMillis() + timeoutMillis));
        player.sendMessage(VehicleMaintenanceMessages.payArmed());
    }

    public static final class VehicleCommandRoute {
        private VehicleCommandRoute() {}

        /**
         * Installation id for a transfer command, empty string if the command matches
         * but the id is missing, or null if args are not a transfer command.
         */
        public static String transferInstallationId(String[] args) {
            if (args == null || args.length == 0) {
                return null;
            }
            if (args[0].equalsIgnoreCase("transfervehicle")) {
                return args.length >= 2 ? args[1] : "";
            }
            if (args[0].equalsIgnoreCase("vehicle")
                    && args.length >= 2
                    && args[1].equalsIgnoreCase("transfer")) {
                return args.length >= 3 ? args[2] : "";
            }
            return null;
        }

        public static boolean isMaintenancePay(String[] args) {
            return args != null
                    && args.length >= 3
                    && args[0].equalsIgnoreCase("vehicle")
                    && args[1].equalsIgnoreCase("maintenance")
                    && args[2].equalsIgnoreCase("pay");
        }

        public static boolean isVehicleRoot(String[] args) {
            return args != null && args.length >= 1 && args[0].equalsIgnoreCase("vehicle");
        }
    }

    public static final class VehicleTabCompletions {
        private VehicleTabCompletions() {}

        public static List<String> subcommands(String prefix) {
            return filter(List.of("transfer", "maintenance"), prefix);
        }

        public static List<String> maintenanceActions(String prefix) {
            return filter(List.of("pay"), prefix);
        }

        public static List<String> filter(List<String> options, String prefix) {
            List<String> completions = new ArrayList<>(options);
            if (prefix == null || prefix.isEmpty()) {
                return completions;
            }
            String normalized = prefix.toLowerCase(Locale.ROOT);
            completions.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(normalized));
            return completions;
        }
    }
}
