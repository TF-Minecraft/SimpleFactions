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
import net.tfminecraft.simplefactions.guild.network.InstallationAccess;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Edge;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;

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

    /** {@code station} is set only for a train-station endpoint. Corridor stops leave it null. */
    private record Stop(int provinceId, double position, double strength, Node station) {
    }

    private record Line(
            int[] provinceIds, double[] positions, double[] strengths, Node[] stations,
            Rates rates, double access) {
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
        Map<String, Double> owners = new HashMap<>();
        List<Line> lines = lines(provinces, guild, graph, accessByOwner, owners);
        boolean pushing = openTrackActive(graph);
        if (lines.isEmpty() && !pushing) {
            return;
        }
        int stopCount = offerCount(lines, graph, pushing);
        int guard = stopCount * stopCount + 1;
        for (int pass = 0; pass < guard; pass++) {
            if (!onePass(provinces, guild, graph, lines, accessByOwner, owners, false)) {
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
        Map<String, Double> owners = new HashMap<>();
        List<Line> lines = lines(provinces, guild, graph, accessByOwner, owners);
        if (lines.isEmpty()) {
            return;
        }
        int stopCount = offerCount(lines, graph, false);
        int guard = stopCount * stopCount + 1;
        for (int pass = 0; pass < guard; pass++) {
            if (!onePass(provinces, guild, graph, lines, accessByOwner, owners, true)) {
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
            ProvinceManager provinces, Guild guild, TradeGraph graph, Map<String, Double> accessByOwner,
            Map<String, Double> owners) {
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
                stops.add(endpoint(edge.first(), 0));
            }
            if (edge.mode() != Mode.AIR && corridor > 0) {
                int count = edge.provinces().size();
                for (int index = 0; index < count; index++) {
                    int provinceId = edge.provinces().get(index);
                    if (provinces.contains(provinceId)) {
                        double strength = corridor * ownerAccess(guild, provinces.get(provinceId), owners);
                        stops.add(new Stop(
                                provinceId, length * (index + 1.0) / (count + 1.0), strength, null));
                    }
                }
            }
            if (provinces.contains(edge.second().provinceId())) {
                stops.add(endpoint(edge.second(), length));
            }
            if (stops.size() > 1) {
                int[] provinceIds = new int[stops.size()];
                double[] positions = new double[stops.size()];
                double[] strengths = new double[stops.size()];
                Node[] stations = new Node[stops.size()];
                for (int index = 0; index < stops.size(); index++) {
                    Stop stop = stops.get(index);
                    provinceIds[index] = stop.provinceId();
                    positions[index] = stop.position();
                    strengths[index] = stop.strength();
                    stations[index] = stop.station();
                }
                lines.add(new Line(
                        provinceIds, positions, strengths, stations,
                        HubTransport.rates(edge.mode()), edgeAccess));
            }
        }
        return List.copyOf(lines);
    }

    private static Stop endpoint(Node node, double position) {
        Node station = node.kind() == InstallationKind.TRAIN_STATION ? node : null;
        return new Stop(node.provinceId(), position, 1, station);
    }

    private static int offerCount(List<Line> lines, TradeGraph graph, boolean pushing) {
        Set<Integer> stops = new HashSet<>();
        for (Line line : lines) {
            for (int provinceId : line.provinceIds()) {
                stops.add(provinceId);
            }
        }
        if (pushing) {
            for (Node node : graph.nodes()) {
                if (node.kind() != InstallationKind.TRAIN_STATION) continue;
                stops.addAll(graph.openTrack(node.ownerFactionId(), node.installationId()).keySet());
            }
        }
        return stops.size();
    }

    private static boolean openTrackActive(TradeGraph graph) {
        if (!OpenTrackSettings.enabled()) return false;
        for (Node node : graph.nodes()) {
            if (node.kind() == InstallationKind.TRAIN_STATION
                    && !graph.openTrack(node.ownerFactionId(), node.installationId()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean onePass(
            ProvinceManager provinces, Guild guild, TradeGraph graph, List<Line> lines,
            Map<String, Double> accessByOwner, Map<String, Double> owners, boolean production) {
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
            double[] stored = new double[size];
            double[] boarding = new double[size];
            boolean stationBoarding = false;
            for (int index = 0; index < size; index++) {
                stored[index] = rawAt.getOrDefault(line.provinceIds()[index], 0.0);
                Node station = line.stations()[index];
                if (!production && station != null) {
                    boarding[index] = stationInput(provinces, guild, station);
                    stationBoarding = true;
                } else {
                    boarding[index] = stored[index];
                }
            }
            double share = production ? line.rates().production() : line.rates().trade();
            double kept = line.rates().keptPer1000();
            double[] deliveries = stationBoarding
                    ? lineDeliveries(line.positions(), boarding, stored, line.strengths(),
                            share, kept, line.access())
                    : lineDeliveries(line.positions(), boarding, line.strengths(),
                            share, kept, line.access());
            if (stationBoarding) {
                // A station boards a neighbour's trade for everyone else. What returns to that
                // station ignores trade the neighbour boarding itself accounts for, so the
                // station province is not seeded with its own outbound goods.
                for (int index = 0; index < size; index++) {
                    if (line.stations()[index] != null) {
                        deliveries[index] = withheldArrival(
                                line, stored, boarding, index, share, kept);
                    }
                }
            }
            for (int index = 0; index < size; index++) {
                offered.merge(line.provinceIds()[index], deliveries[index], Math::max);
            }
        }
        if (!production) {
            pushOpenTrack(provinces, guild, graph, accessByOwner, owners, offered);
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
        return lineDeliveries(positions, raw, raw, stops, share, keptPer1000, access);
    }

    /**
     * {@code boardingRaw} is what gets on at the stop. {@code selfRaw} is what that stop may
     * keep from its own boarding. A train station boards neighbouring trade without writing
     * that higher figure back onto the station province.
     */
    static double[] lineDeliveries(
            double[] positions, double[] boardingRaw, double[] selfRaw, double[] stops,
            double share, double keptPer1000, double access) {
        if (positions.length != boardingRaw.length || boardingRaw.length != stops.length
                || selfRaw.length != boardingRaw.length) {
            throw new IllegalArgumentException("Line arrays must have the same length");
        }
        double[] delivered = new double[boardingRaw.length];
        double modeShare = tradeShare(share);
        double lineAccess = clampAccess(access);
        double kept = tradeShare(keptPer1000);
        if (modeShare <= 0 || lineAccess <= 0) {
            return delivered;
        }
        sweep(positions, boardingRaw, selfRaw, stops, modeShare * lineAccess, kept, delivered, 0, boardingRaw.length, 1);
        sweep(positions, boardingRaw, selfRaw, stops, modeShare * lineAccess, kept, delivered, boardingRaw.length - 1, -1, -1);
        return delivered;
    }

    /**
     * Arrival at one train station when the rest of the line boards, excluding trade that this
     * station's neighbour boarding accounts for. Compared again on a later recalculation, where
     * that trade is already stored and would otherwise travel back.
     */
    private static double withheldArrival(
            Line line, double[] stored, double[] boarding, int station, double share, double kept) {
        double[] fromInput = isolatedArrival(line, station, boarding[station], share, kept);
        double[] fromOwn = isolatedArrival(line, station, stored[station], share, kept);
        double[] board = new double[stored.length];
        for (int index = 0; index < stored.length; index++) {
            if (index == station) {
                board[index] = stored[index];
                continue;
            }
            if (line.stations()[index] != null && boarding[index] > stored[index]) {
                board[index] = boarding[index];
                continue;
            }
            double explained = fromInput[index];
            if (stored[index] <= explained + 1e-6) {
                board[index] = Math.min(stored[index], Math.max(0, fromOwn[index]));
            } else {
                board[index] = stored[index];
            }
        }
        return lineDeliveries(line.positions(), board, stored, line.strengths(),
                share, kept, line.access())[station];
    }

    /** What {@code amount} from one stop delivers to the others when nobody else boards. */
    private static double[] isolatedArrival(
            Line line, int station, double amount, double share, double kept) {
        double[] raw = new double[line.positions().length];
        raw[station] = amount;
        return lineDeliveries(line.positions(), raw, raw, line.strengths(), share, kept, line.access());
    }

    private static void sweep(
            double[] positions, double[] boardingRaw, double[] selfRaw, double[] stops,
            double factor, double kept, double[] delivered, int start, int end, int step) {
        double carry = 0;
        int previous = start;
        for (int index = start; index != end; index += step) {
            double incoming = carry;
            if (index != start) {
                double distance = Math.abs(positions[index] - positions[previous]);
                incoming = carry * Math.pow(kept, Math.max(0, distance) / 1000.0);
            }
            double strength = clampAccess(stops[index]);
            double boarding = finitePositive(boardingRaw[index]) * strength * factor;
            double self = finitePositive(selfRaw[index]) * strength * factor;
            delivered[index] = Math.max(delivered[index], Math.max(incoming, self) * strength);
            carry = Math.max(incoming, boarding);
            previous = index;
        }
    }

    private static void pushOpenTrack(
            ProvinceManager provinces, Guild guild, TradeGraph graph, Map<String, Double> accessByOwner,
            Map<String, Double> owners, Map<Integer, Double> offered) {
        if (!OpenTrackSettings.enabled()) return;
        Rates rail = HubTransport.rates(Mode.RAIL);
        double modeShare = tradeShare(rail.trade() * OpenTrackSettings.share());
        double kept = tradeShare(OpenTrackSettings.keptPer1000());
        if (modeShare <= 0 || kept <= 0) return;
        for (Node node : graph.nodes()) {
            if (node.kind() != InstallationKind.TRAIN_STATION) continue;
            Map<Integer, Double> reach = graph.openTrack(node.ownerFactionId(), node.installationId());
            if (reach.isEmpty()) continue;
            double stationAccess = clampAccess(access(accessByOwner, node));
            if (stationAccess <= 0) continue;
            double input = stationInput(provinces, guild, node);
            if (input <= 0) continue;
            double base = input * modeShare * stationAccess;
            for (Map.Entry<Integer, Double> entry : reach.entrySet()) {
                int provinceId = entry.getKey();
                if (provinceId == node.provinceId() || !provinces.contains(provinceId)) continue;
                double distance = entry.getValue();
                if (!Double.isFinite(distance) || distance < 0) continue;
                double owner = ownerAccess(guild, provinces.get(provinceId), owners);
                if (owner <= 0) continue;
                double amount = base * Math.pow(kept, distance / 1000.0) * owner;
                offered.merge(provinceId, amount, Math::max);
            }
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

    /**
     * Trade a train station may board: its own province, or a neighbouring land province
     * in the same top realm, whichever is higher. The station's stored trade is unchanged.
     */
    private static double stationInput(ProvinceManager provinces, Guild guild, Node station) {
        double best = raw(provinces, guild, station.provinceId());
        Province home = provinces.get(station.provinceId());
        if (home == null) return best;
        Faction stationOwner = home.getOwner();
        if (stationOwner == null) return best;
        Faction realm = InstallationAccess.topRealm(stationOwner);
        if (realm == null || realm.getId() == null) return best;
        for (int neighbourId : home.getNeighbours()) {
            Province neighbour = provinces.get(neighbourId);
            if (neighbour == null || neighbour.isSea()) continue;
            Faction owner = neighbour.getOwner();
            if (owner == null) continue;
            Faction neighbourRealm = InstallationAccess.topRealm(owner);
            if (neighbourRealm == null || neighbourRealm.getId() == null) continue;
            if (realm != neighbourRealm && !realm.getId().equalsIgnoreCase(neighbourRealm.getId())) continue;
            best = Math.max(best, raw(provinces, guild, neighbourId));
        }
        return best;
    }

    /** Access to the province owner, cached per owner for this guild. Unowned land is fully open. */
    private static double ownerAccess(Guild guild, Province province, Map<String, Double> cache) {
        if (province == null) return 1;
        Faction owner = province.getOwner();
        if (owner == null || owner.getId() == null) return 1;
        String id = owner.getId().toLowerCase(Locale.ROOT);
        Double known = cache.get(id);
        if (known != null) return known;
        double value = InstallationAccess.of(guild == null ? null : guild.getFaction(), owner);
        if (!Double.isFinite(value)) value = 0;
        value = Math.max(0, Math.min(1, value));
        cache.put(id, value);
        return value;
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
