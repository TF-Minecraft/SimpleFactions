package net.tfminecraft.simplefactions.guild.network;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.ProvinceManager;

/** Immutable connections shared by every guild. Safe to read on any thread. */
public final class TradeGraph {
    public record Node(
            String ownerFactionId, String installationId, InstallationKind kind, int provinceId,
            int centerX, int centerZ, int level, int hubSlots, int networkIndex) {
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

    private record Key(String owner, String installation) { }

    private static final TradeGraph EMPTY = new TradeGraph(List.of(), List.of(), List.of());
    private static volatile TradeGraph live = EMPTY;

    private final List<Node> nodes;
    private final List<Edge> edges;
    private final List<Network> networks;
    private final Map<Key, Node> byInstallation;
    private final Map<Node, List<Edge>> atNode;

    TradeGraph(List<Node> nodes, List<Edge> edges, List<Network> networks) {
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.networks = List.copyOf(networks);
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

    public static TradeGraph live() {
        return live;
    }

    /** Rebuilds from installations and track. Server thread only. */
    public static void refresh(ProvinceManager provinces) {
        live = LiveTradeGraph.build(provinces);
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
