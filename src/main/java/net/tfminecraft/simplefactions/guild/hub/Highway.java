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
 * Trade and production along the shared graph. A hop is one edge, in one direction, and it repeats
 * until nothing larger arrives. Sea and rail then leave a share in the provinces along each edge.
 * That does not start another hop.
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
    public static double corridorShare() {
        double value = Cache.supplyHubCorridorShare;
        if (!Double.isFinite(value)) {
            return 0.5;
        }
        return Math.max(0, Math.min(1, value));
    }

    /** Seeds trade from each node that can send, highest raw trade first, until a pass moves nothing. */
    public static void deliver(
            ProvinceManager provinces, Guild guild, TradeGraph graph, Map<String, Double> accessByOwner) {
        if (provinces == null || guild == null || guild.getId() == null || graph == null || graph.nodes().isEmpty()) {
            return;
        }
        List<Node> nodes = graph.nodes();
        Map<Node, List<Hop>> hops = new HashMap<>();
        for (Node from : nodes) {
            List<Hop> outgoing = new ArrayList<>();
            for (Map.Entry<Node, Link> entry : bestByDestination(graph, from).entrySet()) {
                Node to = entry.getKey();
                if (!provinces.contains(to.provinceId())) {
                    continue;
                }
                double factor = hopFactor(
                        entry.getValue(), access(accessByOwner, from), access(accessByOwner, to));
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

    /** Seeds production by the same fixed-point walk as trade. */
    public static void deliverProduction(
            ProvinceManager provinces, Guild guild, TradeGraph graph, Map<String, Double> accessByOwner) {
        if (provinces == null || guild == null || guild.getId() == null || graph == null || graph.nodes().isEmpty()) {
            return;
        }
        List<Node> nodes = graph.nodes();
        Map<Node, List<Hop>> hops = new HashMap<>();
        for (Node from : nodes) {
            List<Hop> outgoing = new ArrayList<>();
            for (Map.Entry<Node, Link> entry : bestByDestination(graph, from, true).entrySet()) {
                Node to = entry.getKey();
                if (!provinces.contains(to.provinceId())) {
                    continue;
                }
                double factor = productionFactor(
                        entry.getValue(), access(accessByOwner, from), access(accessByOwner, to));
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
            if (!oneProductionPass(provinces, guild, nodes, hops)) {
                return;
            }
        }
    }

    /**
     * Seeds sea and rail provinces along each edge, in both directions. Call this only after
     * {@link #deliver} has settled. Each sending node's raw trade is read before any seed, so one
     * deposit's spread cannot raise the next deposit's source. Air leaves nothing.
     */
    public static void deposit(
            ProvinceManager provinces, Guild guild, TradeGraph graph, Map<String, Double> accessByOwner) {
        if (provinces == null || guild == null || guild.getId() == null || graph == null || graph.edges().isEmpty()) {
            return;
        }
        double corridor = corridorShare();
        if (corridor <= 0) {
            return;
        }
        Map<Node, Double> rawAt = new HashMap<>();
        for (Node node : graph.nodes()) {
            rawAt.put(node, raw(provinces, guild, node));
        }
        for (Edge edge : graph.edges()) {
            if (edge.mode() == Mode.AIR || edge.provinces().isEmpty()) {
                continue;
            }
            double edgeAccess = Math.min(
                    access(accessByOwner, edge.first()), access(accessByOwner, edge.second()));
            if (edgeAccess <= 0) {
                continue;
            }
            depositDirection(provinces, guild, edge, edge.first(), edgeAccess, corridor, rawAt);
            depositDirection(provinces, guild, edge, edge.second(), edgeAccess, corridor, rawAt);
        }
    }

    private static void depositDirection(
            ProvinceManager provinces, Guild guild, Edge edge, Node from,
            double edgeAccess, double corridor, Map<Node, Double> rawAt) {
        List<Integer> along = edge.provincesFrom(from);
        int count = along.size();
        if (count == 0) {
            return;
        }
        double rawFrom = rawAt.getOrDefault(from, 0.0);
        if (rawFrom < 0.5) {
            return;
        }
        Rates rates = HubTransport.rates(edge.mode());
        double share = tradeShare(rates.trade());
        // Kept-per-1000 is at most 0.95, so distance only shrinks the deposit.
        if (rawFrom * edgeAccess * share * corridor < 0.5) {
            return;
        }
        double length = Math.max(0, edge.length());
        for (int index = 0; index < count; index++) {
            int provinceId = along.get(index);
            if (!provinces.contains(provinceId)) {
                continue;
            }
            double distance = length * (index + 1.0) / (count + 1.0);
            double amount = rawFrom * edgeAccess
                    * HubTransport.delivered(share, rates.keptPer1000(), distance) * corridor;
            provinces.get(provinceId).seedTrade(provinces, guild, amount);
        }
    }

    private static double tradeShare(double trade) {
        if (!Double.isFinite(trade) || trade <= 0) {
            return 0;
        }
        return Math.min(HubTransport.MAX_SHARE, trade);
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

    /** One trade hop's multiplier. A line is only as open as its less accessible end. */
    public static double hopFactor(Link link, double accessFrom, double accessTo) {
        if (link == null) {
            return 0;
        }
        return link.tradeFactor() * Math.min(clampAccess(accessFrom), clampAccess(accessTo));
    }

    /**
     * Trade that would arrive at {@code node}. Retained for the supply-hub menu until that menu is removed.
     */
    public static double wouldArrive(
            ProvinceManager provinces, Guild guild, TradeGraph graph,
            Set<HubSite> hubbed, Node node, double hubTradeBonus) {
        if (provinces == null || guild == null || guild.getId() == null || graph == null || node == null) {
            return 0;
        }
        double best = 0;
        Set<Node> seen = new HashSet<>();
        for (Edge edge : graph.edgesAt(node)) {
            Node other = edge.other(node);
            if (!seen.add(other)) {
                continue;
            }
            Link link = bestLink(graph, other, node);
            if (link == null) {
                continue;
            }
            double delivered = raw(provinces, guild, other)
                    * hopFactor(link, 1, 1);
            if (delivered > best) {
                best = delivered;
            }
        }
        return best;
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

    private static boolean oneProductionPass(
            ProvinceManager provinces, Guild guild, List<Node> nodes, Map<Node, List<Hop>> hops) {
        List<Node> senders = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            if (hops.containsKey(node) && provinces.contains(node.provinceId())) {
                senders.add(node);
            }
        }
        senders.sort(Comparator.comparingDouble(
                (Node node) -> production(provinces, guild, node)).reversed()
                .thenComparingInt(Node::provinceId));
        boolean moved = false;
        for (Node from : senders) {
            double fromProduction = production(provinces, guild, from);
            if (fromProduction < 0.1) {
                continue;
            }
            for (Hop hop : hops.get(from)) {
                double delivered = fromProduction * hop.factor;
                if (delivered < 0.1 || delivered <= production(provinces, guild, hop.to)) {
                    continue;
                }
                provinces.get(hop.to.provinceId()).seedProduction(provinces, guild, delivered);
                moved = true;
            }
        }
        return moved;
    }

    private static Map<Node, Link> bestByDestination(TradeGraph graph, Node from) {
        return bestByDestination(graph, from, false);
    }

    private static Map<Node, Link> bestByDestination(TradeGraph graph, Node from, boolean production) {
        Map<Node, Link> best = new HashMap<>();
        for (Edge edge : graph.edgesAt(from)) {
            Node to = edge.other(from);
            Link candidate = toLink(from, to, edge);
            if (prefer(candidate, best.get(to), production)) {
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
    private static boolean prefer(Link candidate, Link incumbent, boolean production) {
        if (candidate == null) {
            return false;
        }
        if (incumbent == null) {
            return true;
        }
        double candidateFactor = production ? candidate.productionFactor() : candidate.tradeFactor();
        double incumbentFactor = production ? incumbent.productionFactor() : incumbent.tradeFactor();
        int compare = Double.compare(candidateFactor, incumbentFactor);
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

    private static double production(ProvinceManager provinces, Guild guild, Node node) {
        Province province = provinces.get(node.provinceId());
        return province == null ? 0 : province.getGuildProduction(guild);
    }

    private static double productionFactor(Link link, double accessFrom, double accessTo) {
        if (link == null) {
            return 0;
        }
        return link.productionFactor() * Math.min(clampAccess(accessFrom), clampAccess(accessTo));
    }

    private static double access(Map<String, Double> accessByOwner, Node node) {
        if (accessByOwner == null || node == null || node.ownerFactionId() == null) {
            return 0;
        }
        return accessByOwner.getOrDefault(node.ownerFactionId().toLowerCase(Locale.ROOT), 0.0);
    }

    private static double clampAccess(double access) {
        return Double.isFinite(access) ? Math.max(0, Math.min(1, access)) : 0;
    }
}
