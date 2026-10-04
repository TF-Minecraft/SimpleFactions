package net.tfminecraft.simplefactions.guild.network;

import java.util.Locale;

import org.bukkit.command.CommandSender;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Edge;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Network;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.utils.Permissions;

/** Staff inspection of the current snapshot, also available from the console. */
public final class TradeGraphCommand {
    private TradeGraphCommand() { }

    public static boolean handle(CommandSender sender, String[] args) {
        if (!Permissions.isAdmin(sender)) {
            sender.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
            return true;
        }
        if (!Cache.requireProvinces(sender)) return true;
        if (args.length != 1) {
            sender.sendMessage("§cUsage: §e/faction tradegraph");
            return true;
        }
        TradeGraph graph = TradeGraph.live();
        if (graph.networks().isEmpty()) sender.sendMessage("§7There are no trade networks.");
        for (int i = 0; i < graph.networks().size(); i++) {
            Network network = graph.networks().get(i);
            sender.sendMessage("§6Network " + (i + 1) + ". §7" + count(network.size(), "node")
                    + ", " + (network.global() ? "global" : "not global") + ".");
            for (Node node : network.nodes()) {
                sender.sendMessage("§e" + label(node) + " §7" + node.kind().getDisplayName()
                        + ", province " + node.provinceId() + ", level " + node.level()
                        + ", " + count(node.hubSlots(), "hub slot") + ".");
                for (Edge edge : graph.edgesAt(node)) {
                    if (!edge.first().equals(node)) continue;
                    sender.sendMessage("§7" + label(edge.first()) + " to " + label(edge.second())
                            + ". " + edge.mode().getKey() + ", "
                            + String.format(Locale.ROOT, "%.1f", edge.length()) + " blocks, "
                            + count(edge.provinces().size(), "province") + ".");
                }
            }
        }
        return true;
    }

    private static String count(int amount, String singular) {
        return amount + " " + singular + (amount == 1 ? "" : "s");
    }

    private static String label(Node node) {
        return node.ownerFactionId() + "/" + node.installationId();
    }
}
