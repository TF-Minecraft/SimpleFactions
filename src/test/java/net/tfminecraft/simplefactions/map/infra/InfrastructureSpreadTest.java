package net.tfminecraft.simplefactions.map.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.map.infra.InfrastructureSpread.Arrival;
import net.tfminecraft.simplefactions.map.infra.InfrastructureSpread.Node;

class InfrastructureSpreadTest {
    @Test
    void eachHopMultipliesByTheEnteredRawTerrain() {
        Map<Integer, Arrival> result = spread(Map.of(
                1, land(0.4, "realm", 2),
                2, land(0.75, "realm", 1, 3),
                3, land(0.75, "realm", 2)), Map.of(1, 10.0));

        assertEquals(7.5, result.get(2).amount(), 1e-9);
        assertEquals(5.625, result.get(3).amount(), 1e-9);
        assertEquals("realm", result.get(3).origin());
    }

    @Test
    void aWorsePathDoesNotReplaceTheBetterArrival() {
        Map<Integer, Arrival> result = spread(Map.of(
                1, land(0.75, "realm", 2, 3),
                2, land(0.75, "realm", 1, 4),
                3, land(0.4, "realm", 1, 4),
                4, land(0.75, "realm", 2, 3)), Map.of(1, 10.0));

        assertEquals(5.625, result.get(4).amount(), 1e-9);
    }

    @Test
    void waterCannotBeEnteredOrUsedAsASource() {
        Map<Integer, Arrival> result = spread(Map.of(
                1, land(0.75, "realm", 2),
                2, new Node(0.75, false, "realm", List.of(1, 3)),
                3, land(0.75, "realm", 2)), Map.of(1, 10.0, 2, 100.0));

        assertFalse(result.containsKey(2));
        assertFalse(result.containsKey(3));
    }

    @Test
    void anotherRealmBlocksSpread() {
        Map<Integer, Arrival> result = spread(Map.of(
                1, land(0.75, "home", 2),
                2, land(0.75, "foreign", 1, 3),
                3, land(0.75, "home", 2)), Map.of(1, 10.0));

        assertFalse(result.containsKey(2));
        assertFalse(result.containsKey(3));
    }

    @Test
    void everyUnownedProvinceEnteredGetsTheWildernessPenalty() {
        Map<Integer, Node> graph = Map.of(
                1, land(0.75, "home", 2),
                2, land(0.4, null, 1, 3),
                3, land(0.75, null, 2));
        Map<Integer, Arrival> result = InfrastructureSpread.spread(graph, Map.of(1, 10.0), 0.25, 0.1);

        assertEquals(10 * 0.40 * 0.25, result.get(2).amount(), 1e-9);
        assertEquals(10 * 0.40 * 0.25 * 0.75 * 0.25, result.get(3).amount(), 1e-9);
        assertFalse(spread(graph, Map.of(1, 10.0)).containsKey(3));
    }

    @Test
    void unownedSourcesStayInUnownedLand() {
        Map<Integer, Arrival> result = spread(Map.of(
                1, land(0.75, null, 2, 3),
                2, land(0.75, "realm", 1),
                3, land(0.75, null, 1)), Map.of(1, 10.0));

        assertFalse(result.containsKey(2));
        assertEquals(1.875, result.get(3).amount(), 1e-9);
        assertNull(result.get(3).origin());
    }

    @Test
    void ownedSourcesCanReenterTheirRealmFromWilderness() {
        Map<Integer, Arrival> result = spread(Map.of(
                1, land(0.75, "home", 2),
                2, land(0.4, null, 1, 3, 4),
                3, land(0.75, "home", 2),
                4, land(0.75, "foreign", 2)), Map.of(1, 10.0));

        assertEquals(0.75, result.get(3).amount(), 1e-9);
        assertEquals("home", result.get(3).origin());
        assertFalse(result.containsKey(4));
    }

    @Test
    void arrivalsBelowTheFloorStopButAnArrivalAtTheFloorContinues() {
        Map<Integer, Arrival> result = spread(Map.of(
                1, land(0.75, "realm", 2, 3),
                2, land(0.4, "realm", 1, 4),
                3, land(0.5, "realm", 1, 5),
                4, land(0.75, "realm", 2),
                5, land(1, "realm", 3)), Map.of(1, 1.0));

        assertFalse(result.containsKey(2));
        assertFalse(result.containsKey(4));
        assertEquals(0.5, result.get(3).amount(), 1e-9);
        assertEquals(0.5, result.get(5).amount(), 1e-9);
    }

    @Test
    void theWinningArrivalKeepsItsOriginAcrossWilderness() {
        Map<Integer, Arrival> result = spread(Map.of(
                1, land(0.75, "first", 3),
                2, land(0.75, "second", 3),
                3, land(0.75, null, 1, 2, 4, 5),
                4, land(0.75, "first", 3),
                5, land(0.75, "second", 3)), Map.of(1, 10.0, 2, 20.0));

        assertEquals("second", result.get(3).origin());
        assertFalse(result.containsKey(4));
        assertEquals(2.8125, result.get(5).amount(), 1e-9);
    }

    private static Node land(double terrain, String realm, Integer... neighbours) {
        return new Node(terrain, true, realm, List.of(neighbours));
    }

    private static Map<Integer, Arrival> spread(Map<Integer, Node> graph, Map<Integer, Double> sources) {
        return InfrastructureSpread.spread(graph, sources, 0.25, 0.5);
    }
}
