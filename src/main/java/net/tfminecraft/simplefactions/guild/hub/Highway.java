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

/** Trade and production along the shared graph. Sea and rail edges include their corridor stops. */
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

    private record Stop(int provinceId, double position, double strength) {
    }

    private record Line(
            int[] provinceIds, double[] positions, double[] strengths, Rates rates, double access) {
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

    /** Seeds trade from every line stop until a pass moves nothing. */
    public static void deliver(
            ProvinceManager provinces, Guild guild, TradeGraph graph, Map<String, Double> accessByOwner) {
        if (provinces == null || guild == null || guild.getId() == null || graph == null || graph.nodes().isEmpty()) {
            return;
        }
        List<Line> lines = lines(provinces, graph, accessByOwner);
        if (lines.isEmpty()) {
            return;
        }
        int stopCount = distinctStopCount(lines);
        int guard = stopCount * stopCount + 1;
        for (int pass = 0; pass < guard; pass++) {
            if (!onePass(provinces, guild, lines, false)) {
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
        List<Line> lines = lines(provinces, graph, accessByOwner);
        if (lines.isEmpty()) {
            return;
        }
        int stopCount = distinctStopCount(lines);
        int guard = stopCount * stopCount + 1;
        for (int pass = 0; pass < guard; pass++) {
            if (!onePass(provinces, guild, lines, true)) {
                return;
            }
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

    private static List<Line> lines(
            ProvinceManager provinces, TradeGraph graph, Map<String, Double> accessByOwner) {
        List<Line> lines = new ArrayList<>();
        double corridor = corridorShare();
        for (Edge edge : graph.edges()) {
            double edgeAccess = Math.min(
                    access(accessByOwner, edge.first()), access(accessByOwner, edge.second()));
            if (edgeAccess <= 0) {
                continue;
            }
            double length = Math.max(0, edge.length());
            List<Stop> stops = new ArrayList<>();
            if (provinces.contains(edge.first().provinceId())) {
                stops.add(new Stop(edge.first().provinceId(), 0, 1));
            }
            if (edge.mode() != Mode.AIR && corridor > 0) {
                int count = edge.provinces().size();
                for (int index = 0; index < count; index++) {
                    int provinceId = edge.provinces().get(index);
                    if (provinces.contains(provinceId)) {
                        stops.add(new Stop(
                                provinceId, length * (index + 1.0) / (count + 1.0), corridor));
                    }
                }
            }
            if (provinces.contains(edge.second().provinceId())) {
                stops.add(new Stop(edge.second().provinceId(), length, 1));
            }
            if (stops.size() > 1) {
                int[] provinceIds = new int[stops.size()];
                double[] positions = new double[stops.size()];
                double[] strengths = new double[stops.size()];
                for (int index = 0; index < stops.size(); index++) {
                    Stop stop = stops.get(index);
                    provinceIds[index] = stop.provinceId();
                    positions[index] = stop.position();
                    strengths[index] = stop.strength();
                }
                lines.add(new Line(
                        provinceIds, positions, strengths, HubTransport.rates(edge.mode()), edgeAccess));
            }
        }
        return List.copyOf(lines);
    }

    private static int distinctStopCount(List<Line> lines) {
        Set<Integer> stops = new HashSet<>();
        for (Line line : lines) {
            for (int provinceId : line.provinceIds()) {
                stops.add(provinceId);
            }
        }
        return stops.size();
    }

    private static boolean onePass(
            ProvinceManager provinces, Guild guild, List<Line> lines, boolean production) {
        Map<Integer, Double> rawAt = new HashMap<>();
        for (Line line : lines) {
            for (int provinceId : line.provinceIds()) {
                rawAt.computeIfAbsent(provinceId, id -> production
                        ? production(provinces, guild, id) : raw(provinces, guild, id));
            }
        }
        Map<Integer, Double> offered = new HashMap<>();
        for (Line line : lines) {
            int size = line.provinceIds().length;
            double[] values = new double[size];
            for (int index = 0; index < size; index++) {
                values[index] = rawAt.getOrDefault(line.provinceIds()[index], 0.0);
            }
            double share = production ? line.rates().production() : line.rates().trade();
            double[] deliveries = lineDeliveries(
                    line.positions(), values, line.strengths(),
                    share, line.rates().keptPer1000(), line.access());
            for (int index = 0; index < size; index++) {
                offered.merge(line.provinceIds()[index], deliveries[index], Math::max);
            }
        }
        double threshold = production ? 0.1 : 0.5;
        boolean moved = false;
        List<Integer> destinations = new ArrayList<>(offered.keySet());
        destinations.sort(Integer::compareTo);
        for (int provinceId : destinations) {
            double amount = offered.get(provinceId);
            double current = production
                    ? production(provinces, guild, provinceId) : raw(provinces, guild, provinceId);
            if (amount < threshold || amount <= current) {
                continue;
            }
            if (production) {
                provinces.get(provinceId).seedProduction(provinces, guild, amount);
            } else {
                provinces.get(provinceId).seedTrade(provinces, guild, amount);
            }
            moved = true;
        }
        return moved;
    }

    /**
     * Best delivery to every stop from all other stops. Two sweeps are equivalent to checking
     * every ordered pair because distance loss multiplies along the line.
     */
    static double[] lineDeliveries(
            double[] positions, double[] raw, double[] stops,
            double share, double keptPer1000, double access) {
        if (positions.length != raw.length || raw.length != stops.length) {
            throw new IllegalArgumentException("Line arrays must have the same length");
        }
        double[] delivered = new double[raw.length];
        double modeShare = tradeShare(share);
        double lineAccess = clampAccess(access);
        double kept = tradeShare(keptPer1000);
        if (modeShare <= 0 || lineAccess <= 0) {
            return delivered;
        }
        sweep(positions, raw, stops, modeShare * lineAccess, kept, delivered, 0, raw.length, 1);
        sweep(positions, raw, stops, modeShare * lineAccess, kept, delivered, raw.length - 1, -1, -1);
        return delivered;
    }

    private static void sweep(
            double[] positions, double[] raw, double[] stops, double factor, double kept,
            double[] delivered, int start, int end, int step) {
        double carry = 0;
        int previous = start;
        for (int index = start; index != end; index += step) {
            if (index != start) {
                double distance = Math.abs(positions[index] - positions[previous]);
                carry *= Math.pow(kept, Math.max(0, distance) / 1000.0);
            }
            double strength = clampAccess(stops[index]);
            double boarding = finitePositive(raw[index]) * strength * factor;
            carry = Math.max(carry, boarding);
            delivered[index] = Math.max(delivered[index], carry * strength);
            previous = index;
        }
    }

    private static double finitePositive(double value) {
        return Double.isFinite(value) && value > 0 ? value : 0;
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
        return raw(provinces, guild, node.provinceId());
    }

    private static double raw(ProvinceManager provinces, Guild guild, int provinceId) {
        Province province = provinces.get(provinceId);
        return province == null ? 0 : province.getRawGuildTrade(guild);
    }

    private static double production(ProvinceManager provinces, Guild guild, int provinceId) {
        Province province = provinces.get(provinceId);
        return province == null ? 0 : province.getGuildProduction(guild);
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
