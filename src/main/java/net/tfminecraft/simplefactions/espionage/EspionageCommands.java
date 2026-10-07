package net.tfminecraft.simplefactions.espionage;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.inventory.EspionageView;

public final class EspionageCommands {
    private EspionageCommands() {}

    public static boolean reload(org.bukkit.command.CommandSender sender, String[] args) {
        if (!sender.hasPermission(EspionageConfig.reloadPermission())) {
            sender.sendMessage("\u00a7cYou do not have permission to refresh intelligence reports.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("\u00a77/faction reloadespionage");
            return true;
        }
        try {
            SpecialPositionsConfigFile.load(net.tfminecraft.simplefactions.SimpleFactions.plugin);
            int count = EspionageService.regenerateReports();
            sender.sendMessage("\u00a7aEspionage settings reloaded and " + count + " intelligence reports regenerated.");
        } catch (IllegalStateException exception) {
            sender.sendMessage("\u00a7cCould not reload espionage settings. Check the server log.");
            net.tfminecraft.simplefactions.SimpleFactions.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Espionage reload failed", exception);
        }
        return true;
    }

    public static boolean matches(String subcommand) {
        return subcommand.equalsIgnoreCase("espionage") || subcommand.equalsIgnoreCase("positions")
                || subcommand.equalsIgnoreCase("spymaster");
    }

    public static boolean handle(Player player, String[] args) {
        var faction = FactionManager.getByMember(player.getName());
        if (faction == null) {
            player.sendMessage("§cYou need to belong to a faction.");
            return true;
        }
        InventoryManager manager = new InventoryManager();
        if (args.length == 1) {
            var holder = EspionageService.spymaster(faction);
            if (!args[0].equalsIgnoreCase("positions") && holder != null && holder.isHolder(player.getUniqueId())) {
                EspionageView.settings(player, faction, manager);
            } else if (args[0].equalsIgnoreCase("positions")) EspionageView.positions(player, faction, manager);
            else EspionageView.spymasterOffice(player, faction, manager);
        } else if (args[0].equalsIgnoreCase("spymaster") && args.length >= 2 && !args[1].equalsIgnoreCase("sabotage")) {
            if (args[1].equalsIgnoreCase("remove")) {
                if (args.length == 2) EspionageService.remove(player, faction);
                else usage(player);
            }
            else {
                var candidate = CharacterNames.resolveOnline(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                if (candidate != null) EspionageService.appoint(player, faction, candidate);
            }
        } else if (args[0].equalsIgnoreCase("spymaster") && args.length == 4 && args[1].equalsIgnoreCase("sabotage")
                && (args[2].equalsIgnoreCase("offense") || args[2].equalsIgnoreCase("defense"))) {
            try {
                EspionageService.setSabotage(player, faction, args[2].equalsIgnoreCase("offense"), Integer.parseInt(args[3]));
            } catch (NumberFormatException exception) {
                usage(player);
            }
        } else usage(player);
        return true;
    }

    private static void usage(Player player) {
        player.sendMessage("§7/faction positions §8| §7/faction espionage §8| §7/faction spymaster <player|remove>");
        player.sendMessage("§7/faction spymaster sabotage <offense|defense> <0|25|50|75|100>");
    }

    public static List<String> complete(Player player, String[] args) {
        List<String> choices = new ArrayList<>();
        var faction = FactionManager.getByMember(player.getName());
        if (faction == null || !args[0].equalsIgnoreCase("spymaster")) return choices;
        var holder = EspionageService.spymaster(faction);
        boolean isHolder = holder != null && holder.isHolder(player.getUniqueId());
        if (args.length == 2) {
            if (faction.isLeader(player.getName())) {
                choices.add("remove");
                for (String name : faction.getMembers())
                    if (Bukkit.getPlayerExact(name) != null && EspionageService.eligible(faction, name)) choices.add(name);
            }
            if (isHolder) choices.add("sabotage");
        } else if (isHolder && args[1].equalsIgnoreCase("sabotage")) {
            if (args.length == 3) choices.addAll(List.of("offense", "defense"));
            if (args.length == 4) choices.addAll(List.of("0", "25", "50", "75", "100"));
        }
        String prefix = args[args.length - 1];
        choices.removeIf(choice -> !choice.regionMatches(true, 0, prefix, 0, prefix.length()));
        return choices;
    }
}
