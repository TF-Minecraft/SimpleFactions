package net.tfminecraft.simplefactions.guild.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.guild.network.RailRoutes.Route;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Edge;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Network;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.ProvinceData;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.Site;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceLookup.Point;

class TradeGraphTest {
    private static final RailRoutes NO_TRACK = (from, to) -> Optional.empty();

    @AfterEach
    void clearLiveSnapshot() {
        TradeGraph.setLiveForTests(null);
    }

    @Test
    void aSeaEdgeRunsThroughWater() {
        TradeGraph graph = sea(Terrain.WATER);

        Edge edge = between(graph, "a", "b", Mode.SEA);
        assertNotNull(edge);
        assertEquals(List.of(2, 3), edge.provinces());
        assertEquals(500, edge.length(), 1e-9);
        assertContiguous(edge.provinces(), coast(Terrain.WATER));
        assertEquals(Mode.SEA, edge.mode());
    }

    @Test
    void aSeaEdgeRunsThroughSea() {
        TradeGraph graph = sea(Terrain.SEA);

        Edge edge = between(graph, "a", "b", Mode.SEA);
        assertNotNull(edge);
        assertEquals(List.of(2, 3), edge.provinces());
        assertEquals(500, edge.length(), 1e-9);
        assertContiguous(edge.provinces(), coast(Terrain.SEA));
    }

    @Test
    void portsOnDifferentWaterBodiesHaveNoEdge() {
        Map<Integer, ProvinceData> provinces = new HashMap<>();
        provinces.put(1, land(2, 9));
        provinces.put(2, water(Terrain.SEA, 1));
        provinces.put(9, land(1, 3));
        provinces.put(3, land(9, 4));
        provinces.put(4, water(Terrain.WATER, 3));
        provinces.put(7, land(8));
        provinces.put(8, land(7));

        TradeGraph graph = build(List.of(
                site("alpha", "sea", InstallationKind.PORT, 1, 0, 0),
                site("beta", "lake", InstallationKind.PORT, 3, 10, 0),
                site("gamma", "inland", InstallationKind.PORT, 7, 20, 0)), provinces, NO_TRACK);

        assertEquals(3, graph.nodes().size());
        assertTrue(graph.edges().isEmpty());
    }

    @Test
    void theStoredSeaPathIsTheShortestOrderedContiguousRoute() {
        Map<Integer, ProvinceData> provinces = new HashMap<>();
        provinces.put(1, land(10));
        provinces.put(10, water(Terrain.SEA, 1, 11, 20));
        provinces.put(11, water(Terrain.SEA, 10, 12));
        provinces.put(12, water(Terrain.WATER, 11, 2));
        provinces.put(20, water(Terrain.SEA, 10, 2));
        provinces.put(2, land(12, 20));

        TradeGraph graph = build(List.of(
                site("alpha", "a", InstallationKind.PORT, 1, 0, 0, 2),
                site("alpha", "b", InstallationKind.PORT, 2, 300, 400, 1)), provinces, NO_TRACK);

        Edge edge = between(graph, "a", "b", Mode.SEA);
        assertNotNull(edge);
        assertEquals(List.of(10, 20), edge.provinces());
        assertEquals(500, edge.length(), 1e-9);
        assertContiguous(edge.provinces(), provinces);
        assertTrue(provinces.get(1).neighbours().contains(edge.provinces().getFirst()));
        assertTrue(provinces.get(2).neighbours().contains(edge.provinces().getLast()));
        assertFalse(edge.provinces().contains(1));
        assertFalse(edge.provinces().contains(2));

        Node first = graph.node("alpha", "a");
        Node second = graph.node("ALPHA", "B");
        assertEquals(first, edge.first());
        assertEquals(second, edge.second());
        assertEquals(List.of(20, 10), edge.provincesFrom(second));
        assertSame(edge, graph.edgesAt(first).getFirst());
        assertSame(edge, graph.edgesAt(second).getFirst());
        assertEquals(1, first.level());
        assertEquals(2, first.hubSlots());
        assertEquals(0, first.centerX());
        assertEquals(400, second.centerZ());
        assertNull(graph.networkOf(new Node("no", "no", InstallationKind.PORT, 1, 0, 0, 1, 1, 0)));
        assertTrue(graph.edgesAt(new Node("no", "no", InstallationKind.PORT, 1, 0, 0, 1, 1, 0)).isEmpty());
    }

    @Test
    void everyPairOfAirportsIsAnEdge() {
        TradeGraph graph = build(List.of(
                site("alpha", "a", InstallationKind.AIRPORT, 1, 0, 0),
                site("beta", "b", InstallationKind.AIRPORT, 2, 300, 400),
                site("gamma", "c", InstallationKind.AIRPORT, 3, 0, 600),
                site("alpha", "station", InstallationKind.TRAIN_STATION, 4, 10, 10)), Map.of(
                1, land(), 2, land(), 3, land(), 4, land()), NO_TRACK);

        assertEquals(3, graph.edges().size());
        assertAir(graph, "a", "b", 500);
        assertAir(graph, "a", "c", 600);
        assertAir(graph, "b", "c", Math.hypot(300, 200));
        assertTrue(graph.edgesAt(graph.node("alpha", "station")).isEmpty());
        assertEquals(3, graph.networkOf(graph.node("gamma", "c")).size());
        assertEquals(1, graph.networkOf(graph.node("alpha", "station")).size());
    }

    @Test
    void aRailEdgeKeepsRouteOrderAndDropsTheEnds() {
        Map<Integer, ProvinceData> provinces = new HashMap<>();
        provinces.put(1, land());
        provinces.put(2, land());
        provinces.put(4, land());
        provinces.put(5, land());
        provinces.put(8, land());

        TradeGraph graph = build(List.of(
                site("alpha", "a", InstallationKind.TRAIN_STATION, 1, 0, 0),
                site("alpha", "b", InstallationKind.PORT, 5, 10, 0),
                site("alpha", "c", InstallationKind.TRAIN_STATION, 8, 20, 0)), provinces, (from, to) -> {
            String left = from.getId();
            String right = to.getId();
            if (left.equals("a") && right.equals("b")) {
                return Optional.of(new Route(1500, List.of(
                        new Point(1, 64, 0),
                        new Point(2, 64, 0),
                        new Point(2, 70, 0),
                        new Point(0, 64, 0),
                        new Point(99, 64, 0),
                        new Point(4, 64, 0),
                        new Point(5, 64, 0))));
            }
            if (left.equals("a") && right.equals("c")) return Optional.of(new Route(Double.NaN, List.of()));
            if (left.equals("b") && right.equals("c")) return Optional.of(new Route(-5, List.of()));
            return Optional.empty();
        });

        Edge edge = between(graph, "a", "b", Mode.RAIL);
        assertNotNull(edge);
        assertEquals(1500, edge.length(), 1e-9);
        assertEquals(List.of(2, 4), edge.provinces());
        assertEquals(List.of(4, 2), edge.provincesFrom(edge.second()));
        assertNull(between(graph, "a", "c", Mode.RAIL));
        assertNull(between(graph, "b", "c", Mode.RAIL));
        assertNotNull(graph.node("alpha", "c"));
    }

    @Test
    void railPastAFortProvinceStillJoinsTheStations() {
        TradeGraph graph = build(List.of(
                site("alpha", "east", InstallationKind.TRAIN_STATION, 1, 0, 0),
                site("alpha", "west", InstallationKind.TRAIN_STATION, 5, 100, 0),
                site("alpha", "keep", InstallationKind.FORT, 3, 50, 0)), Map.of(
                1, land(), 3, land(), 5, land()), (from, to) -> {
            if (from.getId().equals("east") && to.getId().equals("west")) {
                return Optional.of(new Route(400, List.of(
                        new Point(1, 64, 0),
                        new Point(3, 64, 0),
                        new Point(5, 64, 0))));
            }
            return Optional.empty();
        });

        Edge edge = between(graph, "east", "west", Mode.RAIL);
        assertNotNull(edge);
        assertEquals(400, edge.length(), 1e-9);
        assertEquals(List.of(3), edge.provinces());
        assertNull(graph.node("alpha", "keep"));
        assertSame(graph.networkOf(graph.node("alpha", "east")), graph.networkOf(graph.node("alpha", "west")));
        assertEquals(2, graph.nodes().size());
    }

    @Test
    void seaAndRailBothRemainWhenAPairHasBoth() {
        Map<Integer, ProvinceData> provinces = coast(Terrain.SEA);
        TradeGraph graph = build(List.of(
                site("alpha", "a", InstallationKind.PORT, 1, 0, 0),
                site("beta", "b", InstallationKind.PORT, 4, 300, 400)), provinces,
                (from, to) -> Optional.of(new Route(800, List.of(
                        new Point(1, 64, 0), new Point(9, 64, 0), new Point(4, 64, 0)))));

        assertNotNull(between(graph, "a", "b", Mode.SEA));
        Edge rail = between(graph, "a", "b", Mode.RAIL);
        assertNotNull(rail);
        assertEquals(800, rail.length(), 1e-9);
        assertEquals(2, graph.edges().size());
    }

    @Test
    void componentsFollowSeaAirAndRail() {
        TradeGraph graph = connectedWorld();

        assertEquals(List.of(3, 2, 1), graph.networks().stream().map(Network::size).toList());
        assertEquals(Set.of("pa", "sa", "pb"), ids(graph.networks().get(0)));
        assertEquals(Set.of("ab", "ag"), ids(graph.networks().get(1)));
        assertEquals(Set.of("sg"), ids(graph.networks().get(2)));
        Node port = graph.node("alpha", "pa");
        assertEquals(0, port.networkIndex());
        assertSame(graph.networks().get(0), graph.networkOf(port));
        assertEquals(2, graph.edgesAt(port).size());
        assertEquals(List.of(2, 3), between(graph, "pa", "pb", Mode.SEA).provinces());
    }

    @Test
    void theSingleLargestNetworkWithTwoOwnersIsGlobal() {
        TradeGraph graph = connectedWorld();

        assertEquals(1, globals(graph));
        assertTrue(graph.networks().get(0).global());
        assertFalse(graph.networks().get(1).global());
        assertFalse(graph.networks().get(2).global());
    }

    @Test
    void aTieForTheLargestNetworkIsNotGlobal() {
        Map<Integer, ProvinceData> provinces = new HashMap<>();
        provinces.put(1, land());
        provinces.put(2, land());
        provinces.put(3, land(5));
        provinces.put(5, water(Terrain.SEA, 3, 4));
        provinces.put(4, land(5));
        TradeGraph graph = build(List.of(
                site("alpha", "p1", InstallationKind.PORT, 3, 0, 0),
                site("alpha", "s1", InstallationKind.TRAIN_STATION, 1, 0, 0),
                site("beta", "s2", InstallationKind.TRAIN_STATION, 2, 10, 0),
                site("gamma", "p2", InstallationKind.PORT, 4, 20, 0)), provinces, (from, to) -> {
            if (from.getId().equals("s1") && to.getId().equals("s2")) {
                return Optional.of(new Route(10, List.of()));
            }
            return Optional.empty();
        });

        assertEquals(2, graph.networks().size());
        assertEquals(2, graph.networks().get(0).size());
        assertEquals(2, graph.networks().get(1).size());
        assertEquals(0, globals(graph));
    }

    @Test
    void aNetworkOwnedByOneFactionIsNotGlobal() {
        TradeGraph graph = build(List.of(
                site("alpha", "s1", InstallationKind.TRAIN_STATION, 1, 0, 0),
                site("alpha", "s2", InstallationKind.TRAIN_STATION, 2, 10, 0),
                site("alpha", "s3", InstallationKind.TRAIN_STATION, 3, 20, 0),
                site("beta", "p", InstallationKind.PORT, 9, 30, 0)), Map.of(
                1, land(), 2, land(), 3, land(), 9, land()), (from, to) -> {
            String left = from.getId();
            String right = to.getId();
            if (left.equals("s1") && right.equals("s2") || left.equals("s2") && right.equals("s3")) {
                return Optional.of(new Route(10, List.of()));
            }
            return Optional.empty();
        });

        assertEquals(List.of(3, 1), graph.networks().stream().map(Network::size).toList());
        assertEquals(Set.of("alpha"), owners(graph.networks().get(0)));
        assertEquals(0, globals(graph));
    }

    @Test
    void anUnfinishedInstallationIsNotANode() {
        Map<Integer, ProvinceData> provinces = coast(Terrain.SEA);
        TradeGraph graph = build(List.of(
                site("alpha", "ready", InstallationKind.PORT, 1, 0, 0, 4),
                new Site("alpha", installation("building", InstallationKind.PORT, 4, 30, 40), 2, false),
                site("alpha", "keep", InstallationKind.FORT, 6, 0, 0)), provinces, NO_TRACK);

        assertEquals(List.of("ready"), graph.nodes().stream().map(Node::installationId).toList());
        Node ready = graph.node("Alpha", "Ready");
        assertNotNull(ready);
        assertEquals(1, ready.provinceId());
        assertEquals(1, ready.level());
        assertEquals(4, ready.hubSlots());
        assertEquals(InstallationKind.PORT, ready.kind());
        assertNull(graph.node("alpha", "building"));
        assertNull(graph.node("alpha", "keep"));
        assertTrue(graph.edges().isEmpty());
        assertFalse(graph.networkOf(ready).global());
    }

    @Test
    void testsCanReplaceTheLiveSnapshot() {
        assertTrue(TradeGraph.live().nodes().isEmpty());
        TradeGraph graph = sea(Terrain.SEA);
        TradeGraph.setLiveForTests(graph);
        assertSame(graph, TradeGraph.live());
        TradeGraph.setLiveForTests(null);
        assertTrue(TradeGraph.live().edges().isEmpty());
    }

    private static TradeGraph connectedWorld() {
        Map<Integer, ProvinceData> provinces = new HashMap<>();
        provinces.put(1, land(2));
        provinces.put(2, water(Terrain.SEA, 1, 3));
        provinces.put(3, water(Terrain.SEA, 2, 4));
        provinces.put(4, land(3));
        provinces.put(5, land());
        provinces.put(6, land());
        provinces.put(7, land());
        provinces.put(8, land());
        return build(List.of(
                site("alpha", "pa", InstallationKind.PORT, 1, 0, 0),
                site("alpha", "sa", InstallationKind.TRAIN_STATION, 5, 5, 0),
                site("beta", "ab", InstallationKind.AIRPORT, 6, 6, 0),
                site("beta", "pb", InstallationKind.PORT, 4, 30, 40),
                site("gamma", "ag", InstallationKind.AIRPORT, 7, 7, 0),
                site("gamma", "sg", InstallationKind.TRAIN_STATION, 8, 8, 0)), provinces, (from, to) -> {
            if (from.getId().equals("pa") && to.getId().equals("sa")) {
                return Optional.of(new Route(100, List.of()));
            }
            return Optional.empty();
        });
    }

    private static TradeGraph sea(Terrain water) {
        return build(List.of(
                site("alpha", "a", InstallationKind.PORT, 1, 0, 0),
                site("beta", "b", InstallationKind.PORT, 4, 300, 400)), coast(water), NO_TRACK);
    }

    /** Land, water, water, land. Ports sit on the land ends. */
    private static Map<Integer, ProvinceData> coast(Terrain water) {
        Map<Integer, ProvinceData> provinces = new HashMap<>();
        provinces.put(1, land(2));
        provinces.put(2, water(water, 1, 3));
        provinces.put(3, water(water, 2, 4));
        provinces.put(4, land(3));
        return provinces;
    }

    private static TradeGraph build(List<Site> sites, Map<Integer, ProvinceData> provinces, RailRoutes rail) {
        return TradeGraphBuilder.build(sites, provinces, rail, point -> (int) Math.floor(point.x()));
    }

    private static Site site(String faction, String id, InstallationKind kind, int province, int x, int z) {
        return site(faction, id, kind, province, x, z, 1);
    }

    private static Site site(
            String faction, String id, InstallationKind kind, int province, int x, int z, int hubSlots) {
        return new Site(faction, installation(id, kind, province, x, z), hubSlots, true);
    }

    private static Installation installation(String id, InstallationKind kind, int province, int x, int z) {
        return new Installation(id, id, kind, province, x, z, 1L);
    }

    private static ProvinceData land(int... neighbours) {
        return new ProvinceData(Terrain.PLAINS, ids(neighbours));
    }

    private static ProvinceData water(Terrain terrain, int... neighbours) {
        return new ProvinceData(terrain, ids(neighbours));
    }

    private static Set<Integer> ids(int... values) {
        Set<Integer> ids = new HashSet<>();
        for (int value : values) ids.add(value);
        return ids;
    }

    private static void assertAir(TradeGraph graph, String a, String b, double length) {
        Edge edge = between(graph, a, b, Mode.AIR);
        assertNotNull(edge);
        assertTrue(edge.provinces().isEmpty());
        assertEquals(length, edge.length(), 1e-9);
    }

    private static void assertContiguous(List<Integer> path, Map<Integer, ProvinceData> provinces) {
        assertFalse(path.isEmpty());
        for (int i = 1; i < path.size(); i++) {
            int previous = path.get(i - 1);
            int next = path.get(i);
            assertTrue(provinces.get(previous).neighbours().contains(next),
                    previous + " should neighbour " + next);
        }
    }

    private static Edge between(TradeGraph graph, String a, String b, Mode mode) {
        for (Edge edge : graph.edges()) {
            if (edge.mode() != mode) continue;
            String first = edge.first().installationId();
            String second = edge.second().installationId();
            if (first.equals(a) && second.equals(b) || first.equals(b) && second.equals(a)) return edge;
        }
        return null;
    }

    private static Set<String> ids(Network network) {
        Set<String> ids = new HashSet<>();
        for (Node node : network.nodes()) ids.add(node.installationId());
        return ids;
    }

    private static Set<String> owners(Network network) {
        Set<String> owners = new HashSet<>();
        for (Node node : network.nodes()) owners.add(node.ownerFactionId());
        return owners;
    }

    private static long globals(TradeGraph graph) {
        return graph.networks().stream().filter(Network::global).count();
    }
}
