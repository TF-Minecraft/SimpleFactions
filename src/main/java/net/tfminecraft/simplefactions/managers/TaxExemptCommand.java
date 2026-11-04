package net.tfminecraft.simplefactions.managers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.economy.TaxExemptions;
import net.tfminecraft.simplefactions.identity.Members;

/**
 * {@code /taxexempt [on|off|list]}: helpers and staff stop paying citizen tax on the character they
 * are playing, for NPC and lore characters that are handed denars in roleplay.
 */
public final class TaxExemptCommand implements CommandExecutor, TabCompleter {
    public static final String CMD = "taxexempt";
    static final List<String> SUBCOMMANDS = List.of("on", "off", "list");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(TaxExemptions.PERMISSION)) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (args.length > 1 || !(sub.isEmpty() || SUBCOMMANDS.contains(sub))) {
            sender.sendMessage("§cUsage: /" + label + " [on|off|list]");
            return true;
        }
        if (sub.equals("list")) {
            list(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can change their own character's tax. Use /" + label + " list.");
            return true;
        }
        String key = Members.key(player);
        if (key == null) {
            sender.sendMessage("§cYou need an active character first.");
            return true;
        }
        boolean on = sub.isEmpty() ? !TaxExemptions.isSet(key) : sub.equals("on");
        String character = Members.display(key);
        try {
            if (!TaxExemptions.set(key, on)) {
                sender.sendMessage(on
                        ? "§e" + character + " already pays no citizen tax."
                        : "§e" + character + " already pays citizen tax.");
                return true;
            }
        } catch (IOException error) {
            SimpleFactions.getInstance().getLogger().log(Level.SEVERE,
                    "[TaxExempt] Could not save " + player.getName() + "'s change for " + character + " (" + key + ")", error);
            sender.sendMessage("§cCould not save the change; nothing was changed. Tell a developer.");
            return true;
        }
        SimpleFactions.getInstance().getLogger().info("[TaxExempt] " + player.getName() + " turned citizen tax "
                + (on ? "OFF" : "ON") + " for " + character + " (" + key + ")");
        sender.sendMessage(on
                ? "§a" + character + " now pays no citizen tax on denars it picks up or earns. "
                        + "Your other characters are still taxed. Use /" + label + " off to undo."
                : "§a" + character + " pays citizen tax again.");
        return true;
    }

    private static void list(CommandSender sender) {
        List<String> keys = TaxExemptions.keys();
        if (keys.isEmpty()) {
            sender.sendMessage("§eNo character is exempt from citizen tax.");
            return;
        }
        sender.sendMessage("§eCharacters exempt from citizen tax (" + keys.size() + "):");
        for (String key : keys) {
            String account = Members.accountName(key);
            sender.sendMessage("§7- §f" + Members.display(key) + (account == null ? "" : " §7(" + account + ")"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !sender.hasPermission(TaxExemptions.PERMISSION)) return List.of();
        List<String> matches = new ArrayList<>();
        String typed = args[0].toLowerCase(Locale.ROOT);
        for (String sub : SUBCOMMANDS) if (sub.startsWith(typed)) matches.add(sub);
        return matches;
    }
}
