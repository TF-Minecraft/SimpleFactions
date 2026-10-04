package net.tfminecraft.simplefactions.guild.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.CommandSender;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Edge;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.utils.Permissions;

/** Staff inspection of the current trade graph, also available from the console. */
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
        int connections = graph.edges().size();
        sender.sendMessage("§6Trade graph. §e" + graph.nodes().size() + "§7 "
                + plural(graph.nodes().size(), "installation") + ", §e" + connections + "§7 "
                + plural(connections, "connection") + ".");
        List<Node> nodes = new ArrayList<>(graph.nodes());
        nodes.sort(Comparator.comparing((Node node) -> shown(node.name()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Node::ownerFactionId, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Node::installationId, String.CASE_INSENSITIVE_ORDER));
        for (Node node : nodes) {
            sender.sendMessage("§e" + shown(node.name()) + " §7" + node.ownerFactionId() + ", "
                    + node.kind().getDisplayName());
            List<Edge> edges = new ArrayList<>(graph.edgesAt(node));
            edges.sort(Comparator.comparing(
                    (Edge edge) -> shown(edge.other(node).name()), String.CASE_INSENSITIVE_ORDER));
            for (Edge edge : edges) {
                Node other = edge.other(node);
                sender.sendMessage("§7  " + shown(other.name()) + ", " + edge.mode().getKey() + ", "
                        + blocks(edge.length()) + ", " + edge.provinces().size() + " "
                        + plural(edge.provinces().size(), "province"));
            }
        }
        return true;
    }

    private static String shown(String name) {
        return name == null || name.isBlank() ? "unnamed" : name;
    }

    private static String plural(int amount, String singular) {
        return singular + (amount == 1 ? "" : "s");
    }

    private static String blocks(double length) {
        double distance = Double.isFinite(length) ? Math.max(0, length) : 0;
        long whole = Math.round(distance);
        if (Math.abs(distance - whole) < 0.05) {
            return whole + " blocks";
        }
        return String.format(Locale.ROOT, "%.1f blocks", distance);
    }
}
