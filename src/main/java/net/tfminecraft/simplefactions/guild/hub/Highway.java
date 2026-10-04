package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Rates;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Edge;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;

/**
 * Trade along the shared graph. A hop is one edge, in one direction, and it repeats until
 * nothing larger arrives. Production uses {@link #links}, which keeps only pairs hubbed at both ends.
 */
public final class Highway {
    /** A trade node where the guild has an active hub. Ids are compared ignoring case. */
    public record HubSite(String ownerFactionId, String installationId) {
        public HubSite {
            ownerFactionId = ownerFactionId == null ? "" : ownerFactionId.toLowerCase(Locale.ROOT);
            installationId = installationId == null ? "" : installationId.toLowerCase(Locale.ROOT);
        }

        public boolean matches(Node node) {
            return node != null && equals(new HubSite(node.ownerFactionId(), node.installationId()));
        }
    }

    private static final class Hop {
        private final Node to;
        private final double factor;

        private Hop(Node to, double factor) {
            this.to = to;
            this.factor = factor;
        }
    }

    private Highway() {
    }

    /** Clamped to 0..1. Missing or non-finite config uses the default half. */
    public static double noHubStrength() {
        double value = Cache.supplyHubNoHubStrength;
        if (!Double.isFinite(value)) {
            return 0.5;
        }
        return Math.max(0, Math.min(1, value));
    }

    /**
     * Seeds trade from each node that can send, highest raw trade first, until a pass moves nothing.
     * {@code hubbed} may be empty: both ends then use {@link #noHubStrength()}.
     */
    public static void deliver(
            ProvinceManager provinces, Guild guild, TradeGraph graph, Set<HubSite> hubbed, double hubTradeBonus) {
        if (provinces == null || guild == null || guild.getId() == null || graph == null || graph.nodes().isEmpty()) {
            return;
        }
        List<Node> nodes = graph.nodes();
        Set<Node> hubNodes = hubNodes(graph, hubbed);
        double strength = noHubStrength();
        double bonus = Math.max(0, hubTradeBonus);
        Map<Node, List<Hop>> hops = new HashMap<>();
        for (Node from : nodes) {
            boolean fromHub = hubNodes.contains(from);
            double endFrom = fromHub ? 1 : strength;
            List<Hop> outgoing = new ArrayList<>();
            for (Map.Entry<Node, Link> entry : bestByDestination(graph, from).entrySet()) {
                Node to = entry.getKey();
                if (!provinces.contains(to.provinceId())) {
                    continue;
                }
                double endTo = hubNodes.contains(to) ? 1 : strength;
                double factor = endFrom * entry.getValue().boostedTradeFactor(fromHub ? bonus : 0) * endTo;
                if (factor > 0) {
                    outgoing.add(new Hop(to, factor));
                }
            }
            if (!outgoing.isEmpty()) {
                hops.put(from, outgoing);
            }
        }
        if (hops.isEmpty()) {
            return;
        }
        int guard = nodes.size() * nodes.size() + 1;
        for (int pass = 0; pass < guard; pass++) {
            if (!onePass(provinces, guild, nodes, hops)) {
                return;
            }
        }
    }

    /**
     * Both directions of each pair the guild has hubbed at both ends. The mode is the one with the
     * higher trade factor; rail wins a tie. Safe on any thread.
     */
    public static List<Link> links(TradeGraph graph, Set<HubSite> hubbed) {
        if (graph == null || hubbed == null || hubbed.size() < 2) {
            return List.of();
        }
        Set<Node> hubs = hubNodes(graph, hubbed);
        if (hubs.size() < 2) {
            return List.of();
        }
        List<Link> found = new ArrayList<>();
        for (Node from : hubs) {
            for (Map.Entry<Node, Link> entry : bestByDestination(graph, from).entrySet()) {
                if (hubs.contains(entry.getKey())) {
                    found.add(entry.getValue());
                }
            }
        }
        found.sort(Comparator.comparingInt(Link::fromProvince)
                .thenComparingInt(Link::toProvince)
                .thenComparingInt(link -> link.mode().ordinal()));
        return List.copyOf(found);
    }

    /** The edge from {@code from} to {@code to} with the higher trade factor, or null. Rail wins a tie. */
    public static Link bestLink(TradeGraph graph, Node from, Node to) {
        if (graph == null || from == null || to == null) {
            return null;
        }
        return bestByDestination(graph, from).get(to);
    }

    private static boolean onePass(
            ProvinceManager provinces, Guild guild, List<Node> nodes, Map<Node, List<Hop>> hops) {
        List<Node> senders = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            if (hops.containsKey(node) && provinces.contains(node.provinceId())) {
                senders.add(node);
            }
        }
        senders.sort(Comparator.comparingDouble((Node node) -> raw(provinces, guild, node)).reversed()
                .thenComparingInt(Node::provinceId));
        boolean moved = false;
        for (Node from : senders) {
            double rawFrom = raw(provinces, guild, from);
            if (rawFrom < 0.5) {
                continue;
            }
            for (Hop hop : hops.get(from)) {
                double delivered = rawFrom * hop.factor;
                if (delivered < 0.5 || delivered <= raw(provinces, guild, hop.to)) {
                    continue;
                }
                provinces.get(hop.to.provinceId()).seedTrade(provinces, guild, delivered);
                moved = true;
            }
        }
        return moved;
    }

    private static Map<Node, Link> bestByDestination(TradeGraph graph, Node from) {
        Map<Node, Link> best = new HashMap<>();
        for (Edge edge : graph.edgesAt(from)) {
            Node to = edge.other(from);
            Link candidate = toLink(from, to, edge);
            if (prefer(candidate, best.get(to))) {
                best.put(to, candidate);
            }
        }
        return best;
    }

    private static Link toLink(Node from, Node to, Edge edge) {
        Rates rates = HubTransport.rates(edge.mode());
        return new Link(
                from.provinceId(),
                to.provinceId(),
                from.ownerFactionId(),
                from.installationId(),
                to.ownerFactionId(),
                to.installationId(),
                edge.mode(),
                edge.length(),
                HubTransport.delivered(rates.trade(), rates.keptPer1000(), edge.length()),
                HubTransport.delivered(rates.production(), rates.keptPer1000(), edge.length()));
    }

    /** Higher trade factor wins. Equal factors keep rail, which is what a measured link did. */
    private static boolean prefer(Link candidate, Link incumbent) {
        if (candidate == null) {
            return false;
        }
        if (incumbent == null) {
            return true;
        }
        int compare = Double.compare(candidate.tradeFactor(), incumbent.tradeFactor());
        if (compare != 0) {
            return compare > 0;
        }
        return candidate.mode() == Mode.RAIL && incumbent.mode() != Mode.RAIL;
    }

    private static Set<Node> hubNodes(TradeGraph graph, Set<HubSite> hubbed) {
        if (hubbed == null || hubbed.isEmpty()) {
            return Set.of();
        }
        Set<Node> nodes = new HashSet<>();
        for (Node node : graph.nodes()) {
            if (hubbed.contains(new HubSite(node.ownerFactionId(), node.installationId()))) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private static double raw(ProvinceManager provinces, Guild guild, Node node) {
        Province province = provinces.get(node.provinceId());
        return province == null ? 0 : province.getRawGuildTrade(guild);
    }
}
