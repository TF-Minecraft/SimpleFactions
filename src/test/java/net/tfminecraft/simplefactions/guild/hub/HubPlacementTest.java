package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.BuildFailure;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Network;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;

class HubPlacementTest {
    @Test
    void ownLandOrAHubAlreadyInTheNetwork() {
        Network home = network("home", "port", 1);
        Network abroad = network("other", "station", 2);
        assertTrue(HubPlacement.mayPlace(abroad, Set.of(), true));
        assertTrue(HubPlacement.mayPlace(abroad, Set.of(home), true));
        assertTrue(HubPlacement.mayPlace(home, Set.of(home), false));
        assertFalse(HubPlacement.mayPlace(abroad, Set.of(home), false));
        assertFalse(HubPlacement.mayPlace(null, Set.of(home), false));
        assertTrue(HubPlacement.mayPlace(null, Set.of(), true));
    }

    @Test
    void aSavedHubJoinsItsNetworkWhetherOrNotItIsActive() {
        Installation port = new Installation("port", "Port", InstallationKind.PORT, 1, 0, 0, 1L);
        Installation station = new Installation("station", "Station", InstallationKind.TRAIN_STATION, 2, 10, 0, 1L);
        TradeGraph graph = TestGraphs.linked("home", 0, port, station);
        Network network = graph.networkOf(graph.node("home", "port"));
        Set<Network> joined = HubPlacement.joinedNetworks(graph, List.of(new SupplyHub("home", "port", 1)));
        assertEquals(Set.of(network), joined);
        assertTrue(HubPlacement.mayPlace(network, joined, false));
        assertTrue(HubPlacement.joinedNetworks(graph, List.of()).isEmpty());
    }

    @Test
    void emptyStateWhenThereIsNoInfrastructure() {
        assertEquals(HubPlacement.NO_INFRASTRUCTURE, state(false, false, false, false, 0, 4, 2));
    }

    @Test
    void emptyStateAtTheHubLimitKeepsTheLimitWording() {
        String limit = SupplyHubService.buildFailureMessage(BuildFailure.HUB_LIMIT, null, 2);
        assertEquals(limit, state(true, true, true, true, 3, 8, 2));
        assertTrue(limit.contains("supply hub limit"));
    }

    @Test
    void emptyStateWhenTheJoinedNetworkIsSmallerThanTheLargest() {
        assertEquals(HubPlacement.NOT_GLOBAL, state(true, true, false, false, 3, 8, 2));
        assertEquals(HubPlacement.NOT_GLOBAL, state(true, true, true, false, 3, 8, 2));
        assertEquals(HubPlacement.NOT_GLOBAL, state(false, true, false, false, 0, 8, 2));
    }

    @Test
    void emptyStateWhenTheJoinedNetworkIsALargestOne() {
        assertEquals(HubPlacement.NETWORK_FULL, state(true, true, false, false, 8, 8, 2));
        assertEquals(HubPlacement.NETWORK_FULL, state(true, true, true, false, 8, 8, 2));
    }

    @Test
    void emptyStateIsQuietWhenAHubCanStillBePlaced() {
        assertNull(state(false, true, false, true, 0, 8, 2));
        assertNull(state(true, true, false, true, 3, 8, 2));
        assertNull(state(true, false, false, false, 0, 0, 2));
    }

    private static String state(
            boolean anyHub, boolean territory, boolean atLimit, boolean eligible,
            int joined, int largest, int limit) {
        return HubPlacement.emptyState(anyHub, territory, atLimit, eligible, joined, largest, limit);
    }

    private static Network network(String owner, String installation, int province) {
        return new Network(List.of(new TradeGraph.Node(
                owner, installation, InstallationKind.PORT, province, 0, 0, 1, 2, 0)), false);
    }
}
