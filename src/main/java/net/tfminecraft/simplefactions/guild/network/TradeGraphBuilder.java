package net.tfminecraft.simplefactions.guild.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Edge;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Network;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceLookup.Point;

/** Graph decisions use only supplied data, with no live world or faction reads. */
public final class TradeGraphBuilder {
    /** Finished means registered as an installation, rather than pending construction. */
    public record Site(String ownerFactionId, Installation installation, boolean finished) { }

    public record ProvinceData(Terrain terrain, Set<Integer> neighbours) {
        public ProvinceData {
            neighbours = Set.copyOf(neighbours);
        }

        boolean water() {
            return terrain == Terrain.SEA || terrain == Terrain.WATER;
        }
    }

    private TradeGraphBuilder() { }

    public static TradeGraph build(
            Collection<Site> sites, Map<Integer, ProvinceData> provinces,
            RailRoutes rail, ToIntFunction<Point> provinceAt) {
        List<Site> finished = sites.stream()
                .filter(site -> site.finished() && site.installation() != null
                        && site.ownerFactionId() != null && site.installation().getId() != null
                        && tradeKind(site.installation().getKind()))
                .sorted(Comparator.comparing(Site::ownerFactionId)
                        .thenComparing(site -> site.installation().getId()))
                .toList();
        List<Node> nodes = new ArrayList<>();
        for (Site site : finished) {
            Installation installation = site.installation();
            nodes.add(new Node(site.ownerFactionId(), installation.getId(), installation.getName(),
                    installation.getKind(), installation.getProvince(), installation.getCenterX(),
                    installation.getCenterZ(), installation.getLevel(), -1));
        }
        List<Edge> edges = new ArrayList<>();
        Map<Node, Set<Integer>> access = new HashMap<>();
        for (Node node : nodes) {
            if (node.kind() == InstallationKind.PORT) {
                access.put(node, waterAccess(node.provinceId(), provinces));
            }
        }
        for (int i = 0; i < nodes.size(); i++) {
            Node first = nodes.get(i);
            Map<Integer, Integer> seaPaths = first.kind() == InstallationKind.PORT
                    ? flood(access.get(first), provinces) : Map.of();
            for (int j = i + 1; j < nodes.size(); j++) {
                Node second = nodes.get(j);
                double distance = Math.hypot(
                        (double) first.centerX() - second.centerX(),
                        (double) first.centerZ() - second.centerZ());
                if (first.kind() == InstallationKind.PORT && second.kind() == InstallationKind.PORT) {
                    List<Integer> path = seaPath(seaPaths, access.get(second));
                    if (!path.isEmpty()) {
                        edges.add(new Edge(first, second, Mode.SEA, distance,
                                withoutEnds(path, first, second)));
                    }
                }
                if (first.kind() == InstallationKind.AIRPORT && second.kind() == InstallationKind.AIRPORT) {
                    edges.add(new Edge(first, second, Mode.AIR, distance, List.of()));
                }
                rail.route(finished.get(i).installation(), finished.get(j).installation())
                        .filter(route -> Double.isFinite(route.length()) && route.length() >= 0)
                        .ifPresent(route -> {
                            // Consecutive repeats and the two endpoint provinces go. A fort along the way stays.
                            List<Integer> path = new ArrayList<>();
                            for (Point point : route.points()) {
                                int id = provinceAt.applyAsInt(point);
                                if (id == 0 || !provinces.containsKey(id)) continue;
                                if (path.isEmpty() || path.getLast() != id) path.add(id);
                            }
                            edges.add(new Edge(first, second, Mode.RAIL, route.length(),
                                    withoutEnds(path, first, second)));
                        });
            }
        }
        return components(nodes, edges);
    }

    private static boolean tradeKind(InstallationKind kind) {
        return kind == InstallationKind.PORT || kind == InstallationKind.AIRPORT
                || kind == InstallationKind.TRAIN_STATION;
    }

    private static Set<Integer> waterAccess(int provinceId, Map<Integer, ProvinceData> provinces) {
        ProvinceData province = provinces.get(provinceId);
        if (province == null) return Set.of();
        Set<Integer> access = new HashSet<>();
        for (int id : province.neighbours()) {
            ProvinceData neighbour = provinces.get(id);
            if (id != 0 && neighbour != null && neighbour.water()) access.add(id);
        }
        return access;
    }

    /** Insertion order is breadth-first, so the first reached goal is a shortest path. */
    private static Map<Integer, Integer> flood(Set<Integer> starts, Map<Integer, ProvinceData> provinces) {
        Map<Integer, Integer> parents = new LinkedHashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start : starts.stream().sorted().toList()) {
            parents.put(start, 0);
            queue.add(start);
        }
        while (!queue.isEmpty()) {
            int current = queue.remove();
            for (int id : provinces.get(current).neighbours().stream().sorted().toList()) {
                ProvinceData next = provinces.get(id);
                if (id == 0 || parents.containsKey(id) || next == null || !next.water()) continue;
                parents.put(id, current);
                queue.add(id);
            }
        }
        return parents;
    }

    private static List<Integer> seaPath(Map<Integer, Integer> parents, Set<Integer> goals) {
        for (int reached : parents.keySet()) {
            if (!goals.contains(reached)) continue;
            List<Integer> path = new ArrayList<>();
            for (int id = reached; id != 0; id = parents.get(id)) path.add(id);
            return path.reversed();
        }
        return List.of();
    }

    private static List<Integer> withoutEnds(List<Integer> path, Node first, Node second) {
        List<Integer> corridor = new ArrayList<>();
        for (int id : path) {
            if (id == first.provinceId() || id == second.provinceId()) continue;
            if (corridor.isEmpty() || corridor.getLast() != id) corridor.add(id);
        }
        return corridor;
    }

    private static TradeGraph components(List<Node> nodes, List<Edge> edges) {
        Map<Node, List<Node>> adjacent = new HashMap<>();
        for (Node node : nodes) adjacent.put(node, new ArrayList<>());
        for (Edge edge : edges) {
            adjacent.get(edge.first()).add(edge.second());
            adjacent.get(edge.second()).add(edge.first());
        }
        Set<Node> visited = new HashSet<>();
        List<List<Node>> groups = new ArrayList<>();
        for (Node start : nodes) {
            if (!visited.add(start)) continue;
            List<Node> group = new ArrayList<>();
            ArrayDeque<Node> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                Node node = queue.remove();
                group.add(node);
                for (Node next : adjacent.get(node)) {
                    if (visited.add(next)) queue.add(next);
                }
            }
            groups.add(group);
        }
        groups.sort(Comparator.comparingInt((List<Node> group) -> group.size()).reversed());
        boolean uniqueLargest = !groups.isEmpty()
                && (groups.size() == 1 || groups.get(0).size() > groups.get(1).size());
        Map<Node, Node> indexed = new HashMap<>();
        List<Network> networks = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            List<Node> group = new ArrayList<>();
            for (Node node : groups.get(i)) {
                Node replacement = new Node(node.ownerFactionId(), node.installationId(), node.name(),
                        node.kind(), node.provinceId(), node.centerX(), node.centerZ(), node.level(), i);
                indexed.put(node, replacement);
                group.add(replacement);
            }
            boolean global = i == 0 && uniqueLargest && group.stream()
                    .map(node -> node.ownerFactionId().toLowerCase(java.util.Locale.ROOT)).distinct().count() > 1;
            networks.add(new Network(group, global));
        }
        return new TradeGraph(nodes.stream().map(indexed::get).toList(),
                edges.stream().map(edge -> new Edge(indexed.get(edge.first()), indexed.get(edge.second()),
                        edge.mode(), edge.length(), edge.provinces())).toList(), networks);
    }
}
