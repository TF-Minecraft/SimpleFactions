package net.tfminecraft.simplefactions.guild.network;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.ProvinceManager;

/** Immutable connections shared by every guild. Safe to read on any thread. */
public final class TradeGraph {
    public record Node(
            String ownerFactionId, String installationId, String name, InstallationKind kind,
            int provinceId, int centerX, int centerZ, int level, int networkIndex) {
    }

    /** Provinces run from first to second; neither endpoint province is included. */
    public record Edge(Node first, Node second, Mode mode, double length, List<Integer> provinces) {
        public Edge {
            provinces = List.copyOf(provinces);
        }

        public Node other(Node node) {
            if (first.equals(node)) return second;
            if (second.equals(node)) return first;
            throw new IllegalArgumentException("Node is not on this edge");
        }

        public List<Integer> provincesFrom(Node node) {
            if (first.equals(node)) return provinces;
            if (second.equals(node)) return provinces.reversed();
            throw new IllegalArgumentException("Node is not on this edge");
        }
    }

    public record Network(List<Node> nodes, boolean global) {
        public Network {
            nodes = List.copyOf(nodes);
        }

        public int size() {
            return nodes.size();
        }
    }

    /** Minimum along-track distance from one train station into each other province. */
    public record OpenTrack(String ownerFactionId, String installationId, Map<Integer, Double> distances) {
        public OpenTrack {
            distances = distances == null ? Map.of() : Map.copyOf(distances);
        }
    }

    private record Key(String owner, String installation) { }

    private static final TradeGraph EMPTY = new TradeGraph(List.of(), List.of(), List.of());
    private static volatile TradeGraph live = EMPTY;

    private final List<Node> nodes;
    private final List<Edge> edges;
    private final List<Network> networks;
    private final Map<Key, Node> byInstallation;
    private final Map<Node, List<Edge>> atNode;
    private final Map<Key, Map<Integer, Double>> openTracks;

    TradeGraph(List<Node> nodes, List<Edge> edges, List<Network> networks) {
        this(nodes, edges, networks, Map.of());
    }

    private TradeGraph(
            List<Node> nodes, List<Edge> edges, List<Network> networks, Map<Key, Map<Integer, Double>> openTracks) {
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.networks = List.copyOf(networks);
        this.openTracks = openTracks == null ? Map.of() : Map.copyOf(openTracks);
        Map<Key, Node> found = new HashMap<>();
        Map<Node, List<Edge>> adjacent = new HashMap<>();
        for (Node node : nodes) {
            found.put(key(node.ownerFactionId(), node.installationId()), node);
            adjacent.put(node, new java.util.ArrayList<>());
        }
        for (Edge edge : edges) {
            adjacent.get(edge.first()).add(edge);
            adjacent.get(edge.second()).add(edge);
        }
        adjacent.replaceAll((node, links) -> List.copyOf(links));
        byInstallation = Map.copyOf(found);
        atNode = Map.copyOf(adjacent);
    }

    /** Distances measured when the graph was built. Empty when this station pushes nothing. */
    public Map<Integer, Double> openTrack(String ownerFactionId, String installationId) {
        if (ownerFactionId == null || installationId == null) return Map.of();
        return openTracks.getOrDefault(key(ownerFactionId, installationId), Map.of());
    }

    public TradeGraph withOpenTracks(List<OpenTrack> tracks) {
        Map<Key, Map<Integer, Double>> found = new HashMap<>();
        if (tracks != null) {
            for (OpenTrack track : tracks) {
                if (track == null || track.ownerFactionId() == null || track.installationId() == null) continue;
                Map<Integer, Double> distances = new HashMap<>();
                for (Map.Entry<Integer, Double> entry : track.distances().entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) continue;
                    double distance = entry.getValue();
                    if (!Double.isFinite(distance) || distance < 0) continue;
                    distances.merge(entry.getKey(), distance, Math::min);
                }
                found.merge(key(track.ownerFactionId(), track.installationId()), Map.copyOf(distances), (left, right) -> {
                    Map<Integer, Double> merged = new HashMap<>(left);
                    right.forEach((province, distance) -> merged.merge(province, distance, Math::min));
                    return Map.copyOf(merged);
                });
            }
        }
        return new TradeGraph(nodes, edges, networks, Map.copyOf(found));
    }

    public static TradeGraph live() {
        return live;
    }

    /** Rebuilds from installations and track. Server thread only. */
    public static void refresh(ProvinceManager provinces) {
        live = LiveTradeGraph.build(provinces);
    }

    /** Refreshes when called for the live province data on the server thread; otherwise no-op. */
    public static void refreshIfLive(ProvinceManager provinces) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin == null || plugin.getProvinceManager() != provinces) {
            return;
        }
        try {
            if (!Bukkit.isPrimaryThread()) {
                return;
            }
        } catch (RuntimeException | LinkageError e) {
            return;
        }
        refresh(provinces);
    }

    public static void forgetRoutes() {
        LiveTradeGraph.forgetRoutes();
    }

    /** Replaces the snapshot tests read. Null clears it. */
    public static void setLiveForTests(TradeGraph replacement) {
        live = replacement == null ? EMPTY : replacement;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    /** Largest first; a node's networkIndex refers to this list. */
    public List<Network> networks() {
        return networks;
    }

    /** Returns null when this installation is not a trade node. */
    public Node node(String ownerFactionId, String installationId) {
        if (ownerFactionId == null || installationId == null) return null;
        return byInstallation.get(key(ownerFactionId, installationId));
    }

    public List<Edge> edgesAt(Node node) {
        return atNode.getOrDefault(node, List.of());
    }

    /** Returns null for a node outside this snapshot. */
    public Network networkOf(Node node) {
        return atNode.containsKey(node) ? networks.get(node.networkIndex()) : null;
    }

    private static Key key(String owner, String installation) {
        return new Key(owner.toLowerCase(Locale.ROOT), installation.toLowerCase(Locale.ROOT));
    }
}
