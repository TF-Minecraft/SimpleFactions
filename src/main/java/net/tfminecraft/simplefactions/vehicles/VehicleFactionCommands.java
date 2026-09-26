package net.tfminecraft.simplefactions.vehicles;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.PaymentSource;

import net.tfminecraft.simplefactions.vehicles.berth.VehicleReleaseSessionManager.Kind;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleReleaseSessionManager.VehicleReleaseSession;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferSessionManager.VehicleTransferSession;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseMessages;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleInstallationLockService;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferMessages;
import org.bukkit.Bukkit;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePaySessionManager.VehicleMaintenancePaySession;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceMessages;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.installation.Installation;

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
        if (FactionVehiclePoolService.isPoolTarget(installationId)) {
            armPoolTransfer(player);
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
        clearOtherVehicleSessions(plugin, player);
        plugin.getVehicleTransferSessionManager().put(
                player.getUniqueId(),
                new VehicleTransferSession(
                        installation.getId(),
                        System.currentTimeMillis() + timeoutMillis));
        player.sendMessage(VehicleTransferMessages.commandArmed(installation));
    }

    public static void armTake(Player player) {
        Faction faction = FactionManager.getByLeader(player.getName());
        if (faction == null) {
            player.sendMessage(FactionVehicleReleaseMessages.notLeader());
            return;
        }
        SimpleFactions plugin = SimpleFactions.getInstance();
        long timeoutMillis = InstallationConfigLoader.getTransferRequestTimeoutSeconds() * 1000L;
        plugin.getVehicleTransferSessionManager().clear(player.getUniqueId());
        plugin.getVehicleMaintenancePaySessionManager().clear(player.getUniqueId());
        plugin.getVehicleReleaseSessionManager().put(
                player.getUniqueId(),
                new VehicleReleaseSession(Kind.TAKE, System.currentTimeMillis() + timeoutMillis));
        player.sendMessage(FactionVehicleReleaseMessages.takeArmed());
    }

    public static void armGive(Player player, String targetName) {
        Faction faction = FactionManager.getByLeader(player.getName());
        if (faction == null) {
            player.sendMessage(FactionVehicleReleaseMessages.notLeader());
            return;
        }
        if (targetName == null || targetName.isBlank()) {
            player.sendMessage(FactionVehicleReleaseMessages.giveUsage());
            return;
        }
        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(FactionVehicleReleaseMessages.giveSelf());
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage(FactionVehicleReleaseMessages.recipientOffline(targetName));
            return;
        }
        SimpleFactions plugin = SimpleFactions.getInstance();
        long timeoutMillis = InstallationConfigLoader.getTransferRequestTimeoutSeconds() * 1000L;
        plugin.getVehicleTransferSessionManager().clear(player.getUniqueId());
        plugin.getVehicleMaintenancePaySessionManager().clear(player.getUniqueId());
        plugin.getVehicleReleaseSessionManager().put(
                player.getUniqueId(),
                new VehicleReleaseSession(
                        Kind.GIVE,
                        target.getName(),
                        target.getUniqueId(),
                        System.currentTimeMillis() + timeoutMillis));
        player.sendMessage(FactionVehicleReleaseMessages.giveArmed(target.getName()));
    }

    private static void clearOtherVehicleSessions(SimpleFactions plugin, Player player) {
        plugin.getVehicleMaintenancePaySessionManager().clear(player.getUniqueId());
        plugin.getVehicleReleaseSessionManager().clear(player.getUniqueId());
    }

    private static void armPoolTransfer(Player player) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        long timeoutMillis = InstallationConfigLoader.getTransferRequestTimeoutSeconds() * 1000L;
        clearOtherVehicleSessions(plugin, player);
        plugin.getVehicleTransferSessionManager().put(
                player.getUniqueId(),
                new VehicleTransferSession(null, System.currentTimeMillis() + timeoutMillis, true));
        player.sendMessage(VehicleTransferMessages.poolCommandArmed());
    }

    public static void armMaintenancePay(Player player) {
        armMaintenancePay(player, PaymentSource.POUCH);
    }

    public static void armMaintenancePay(Player player, PaymentSource source) {
        if (source == PaymentSource.POUCH && FactionManager.getByLeader(player.getName()) == null) {
            player.sendMessage(VehicleMaintenanceMessages.notLeader());
            return;
        }
        SimpleFactions plugin = SimpleFactions.getInstance();
        long timeoutMillis = InstallationConfigLoader.getTransferRequestTimeoutSeconds() * 1000L;
        plugin.getVehicleTransferSessionManager().clear(player.getUniqueId());
        plugin.getVehicleReleaseSessionManager().clear(player.getUniqueId());
        plugin.getVehicleMaintenancePaySessionManager().put(
                player.getUniqueId(),
                new VehicleMaintenancePaySession(System.currentTimeMillis() + timeoutMillis, source));
        player.sendMessage(VehicleMaintenanceMessages.payArmed(source));
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

        /**
         * Players often add an amount or their name ("pay bank 100"). The amount is always one
         * day of the vehicle's upkeep, so extra words are ignored once "bank" appears anywhere
         * after "pay". Without "bank", only a lone amount is accepted, so a misspelt source
         * like "bnak" never falls back to the pouch.
         */
        public static boolean isMaintenancePay(String[] args) {
            return args != null
                    && args.length >= 3
                    && args[0].equalsIgnoreCase("vehicle")
                    && args[1].equalsIgnoreCase("maintenance")
                    && args[2].equalsIgnoreCase("pay")
                    && (args.length == 3
                            || mentionsBank(args)
                            || (args.length == 4 && isAmount(args[3])));
        }

        public static PaymentSource maintenancePaymentSource(String[] args) {
            return isMaintenancePay(args) && mentionsBank(args) ? PaymentSource.BANK : PaymentSource.POUCH;
        }

        private static boolean mentionsBank(String[] args) {
            for (int i = 3; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("bank")) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isAmount(String arg) {
            try {
                Double.parseDouble(arg);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        public static boolean isVehicleRoot(String[] args) {
            return args != null && args.length >= 1 && args[0].equalsIgnoreCase("vehicle");
        }

        public static boolean isTake(String[] args) {
            return args != null
                    && args.length >= 2
                    && args[0].equalsIgnoreCase("vehicle")
                    && args[1].equalsIgnoreCase("take");
        }

        /**
         * Target name for a give command, empty string if the name is missing,
         * or null if args are not a give command.
         */
        public static String giveTarget(String[] args) {
            if (args == null
                    || args.length < 2
                    || !args[0].equalsIgnoreCase("vehicle")
                    || !args[1].equalsIgnoreCase("give")) {
                return null;
            }
            return args.length >= 3 ? args[2] : "";
        }
    }

    public static final class VehicleTabCompletions {
        private VehicleTabCompletions() {}

        public static List<String> subcommands(String prefix) {
            return filter(List.of("transfer", "take", "give", "maintenance"), prefix);
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
