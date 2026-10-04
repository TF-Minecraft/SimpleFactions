package net.tfminecraft.simplefactions.guild.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.hub.Highway.HubSite;
import net.tfminecraft.simplefactions.guild.network.NetworkSummary.GuildLabel;
import net.tfminecraft.simplefactions.guild.network.NetworkSummary.Summary;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Network;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.installation.InstallationKind;

class NetworkSummaryTest {
    @Test
    void theGlobalNetworkUsesTheMapName() {
        Summary summary = only(graph(network(true, node("alpha", "a", 1, 2, 0), node("beta", "b", 2, 2, 0))),
                List.of(guild("chisels", "The Chisels")),
                Map.of("alpha", "Alpha", "beta", "Beta"),
                "Vardera",
                (guild, province) -> 10);

        assertEquals("The Vardera Network", summary.name());
        assertTrue(summary.global());
        assertEquals(2, summary.nodes());
    }

    @Test
    void aBlankMapNameUsesUnknown() {
        Summary summary = only(graph(network(true, node("alpha", "a", 1, 1, 0), node("beta", "b", 2, 1, 0))),
                List.of(), Map.of(), "  ", (guild, province) -> 0);

        assertEquals("The Unknown Network", summary.name());
    }

    @Test
    void aGuildNameThatStartsWithTheIsNotPrefixedAgain() {
        assertEquals("The Chisels Network", nameOf("The Chisels"));
        assertEquals("the Chisels Network", nameOf("the Chisels"));
        assertEquals("THE Chisels Network", nameOf("THE Chisels"));
        assertEquals("The Yevakeepers Network", nameOf("Yevakeepers"));
        assertEquals("The Bog Network", nameOf("§aThe Bog"));
    }

    @Test
    void plainRemovesGradientLegacyAndTrailingSectionSigns() {
        assertEquals("The", NetworkSummary.plain(
                "§x§a§3§a§1§8§4§§x§3§9§6§E§4§7T§§x§3§9§6§F§4§Dh§§x§3§9§6§F§5§2e"));
        assertEquals("Gold", NetworkSummary.plain("§6Gold"));
        assertEquals("Gold", NetworkSummary.plain("Gold§"));
    }

    @Test
    void gradientGuildNameStartingWithTheIsNotPrefixedAgain() {
        assertEquals("The Bog Freehold Network", nameOf(
                "§x§a§3§a§1§8§4§§x§3§9§6§E§4§7T§§x§3§9§6§F§4§Dh§§x§3§9§6§F§5§2e "
                        + "§x§3§9§6§F§5§8B§§x§3§9§6§F§5§eog Freehold"));
    }

    @Test
    void noTradeUsesTheFactionWithTheMostStops() {
        Node bigA = node("big", "a", 1, 1, 0);
        Node bigB = node("big", "b", 2, 1, 0);
        Node small = node("small", "c", 3, 1, 0);
        Summary summary = only(graph(network(false, bigA, bigB, small)),
                List.of(guild("quiet", "Quiet")),
                Map.of("big", "Big", "small", "Small"),
                "Vardera",
                (guild, province) -> 0);

        assertEquals("The Big Network", summary.name());
        assertTrue(summary.members().isEmpty());
        assertEquals(0, summary.otherPercent());
    }

    @Test
    void aTieForMostStopsUsesTheNameThatComesFirst() {
        Summary summary = only(graph(network(false,
                node("morrow", "a", 1, 1, 0),
                node("alden", "b", 2, 1, 0))),
                List.of(),
                Map.of("morrow", "Morrow", "alden", "Alden"),
                "Vardera",
                (guild, province) -> 0);

        assertEquals("The Alden Network", summary.name());
    }

    @Test
    void equalFactionNamesBreakTheTieById() {
        Summary summary = only(graph(network(false,
                node("b", "a", 1, 1, 0),
                node("a", "b", 2, 1, 0))),
                List.of(),
                Map.of("a", "Same", "b", "Same"),
                "Vardera",
                (guild, province) -> 0);

        assertEquals("The Same Network", summary.name());
    }

    @Test
    void duplicateNamesKeepThePlainNameOnTheLargerNetwork() {
        Network large = network(false, node("alpha", "a", 10, 1, 0), node("alpha", "b", 11, 1, 0),
                node("alpha", "c", 12, 1, 0));
        Network earlier = network(false, node("alpha", "d", 4, 1, 1));
        Network later = network(false, node("alpha", "e", 8, 1, 2));

        List<Summary> summaries = NetworkSummary.summarize(
                graph(large, earlier, later),
                Map.of(),
                List.of(),
                Map.of("alpha", "Alpha"),
                "Vardera",
                (guild, province) -> 0);

        assertEquals(List.of(
                "The Alpha Network",
                "The Alpha Network II",
                "The Alpha Network III"),
                summaries.stream().map(Summary::name).toList());
        assertEquals(3, summaries.get(0).nodes());
        assertEquals(4, lowest(summaries.get(1)));
        assertEquals(8, lowest(summaries.get(2)));
    }

    @Test
    void fiveMembersThenOther() {
        List<GuildLabel> guilds = List.of(
                guild("a", "A"), guild("b", "B"), guild("c", "C"),
                guild("d", "D"), guild("e", "E"), guild("f", "F"));
        Summary summary = only(graph(network(false, node("alpha", "port", 1, 1, 0))),
                guilds,
                Map.of("alpha", "Alpha"),
                "Vardera",
                (guild, province) -> switch (guild) {
                    case "a" -> 40;
                    case "b" -> 25;
                    case "c" -> 15;
                    case "d" -> 10;
                    case "e" -> 6;
                    case "f" -> 4;
                    default -> 0;
                });

        assertEquals(List.of(
                "- A (40%)",
                "- B (25%)",
                "- C (15%)",
                "- D (10%)",
                "- E (6%)",
                "- Other (4%)"), summary.memberLines());
        assertEquals(5, summary.members().size());
        assertEquals(4, summary.otherPercent());
    }

    @Test
    void aGuildThatRoundsToZeroPercentIsOmitted() {
        Summary summary = only(graph(network(false, node("alpha", "port", 1, 1, 0))),
                List.of(guild("big", "Big"), guild("dust", "Dust"), guild("none", "None")),
                Map.of("alpha", "Alpha"),
                "Vardera",
                (guild, province) -> switch (guild) {
                    case "big" -> 200;
                    case "dust" -> 1;
                    default -> 0;
                });

        assertEquals(List.of("- Big (100%)"), summary.memberLines());
        assertEquals(0, summary.otherPercent());
    }

    @Test
    void aGuildWithTradeButNoHubIsListed() {
        Node port = node("alpha", "port", 1, 2, 0);
        Node station = node("beta", "station", 2, 4, 0);
        Node elsewhere = node("gamma", "other", 9, 3, 1);
        Map<String, Set<HubSite>> hubbed = Map.of(
                "chisels", Set.of(new HubSite("alpha", "port"), new HubSite("beta", "station")),
                "bog", Set.of(new HubSite("alpha", "port")),
                "foreign", Set.of(new HubSite("gamma", "other")));
        List<Summary> summaries = NetworkSummary.summarize(
                graph(network(false, port, station), network(false, elsewhere)),
                hubbed,
                List.of(guild("chisels", "The Chisels"), guild("bog", "Bog"), guild("walkers", "Walkers")),
                Map.of("alpha", "Alpha", "beta", "Beta", "gamma", "Gamma"),
                "Vardera",
                (guild, province) -> switch (guild + ":" + province) {
                    case "chisels:1" -> 50;
                    case "bog:1" -> 30;
                    case "walkers:2" -> 20;
                    default -> 0;
                });

        Summary home = summaries.getFirst();
        assertEquals("The Chisels Network", home.name());
        assertEquals(3, home.hubs());
        assertEquals(6, home.slots());
        assertEquals(2, home.hubsAt(port));
        assertEquals(1, home.hubsAt(station));
        assertEquals(0, home.hubsAt(elsewhere));
        assertEquals(List.of("- The Chisels (50%)", "- Bog (30%)", "- Walkers (20%)"), home.memberLines());
        assertEquals(1, summaries.get(1).hubs());
        assertEquals(3, summaries.get(1).slots());
    }

    @Test
    void tradeInOneProvinceIsCountedOnceWhenTwoStopsShareIt() {
        Summary summary = only(graph(network(false,
                node("alpha", "a", 5, 1, 0),
                node("alpha", "b", 5, 1, 0))),
                List.of(guild("chisels", "Chisels")),
                Map.of("alpha", "Alpha"),
                "Vardera",
                (guild, province) -> province == 5 ? 10 : 1000);

        assertEquals(10, summary.tradeTotal(), 1e-9);
        assertEquals(List.of("- Chisels (100%)"), summary.memberLines());
    }

    @Test
    void theLargestNetworkIsListedFirst() {
        List<Summary> summaries = NetworkSummary.summarize(
                graph(
                        network(true, node("alpha", "a", 1, 1, 0), node("beta", "b", 2, 1, 0)),
                        network(false, node("alpha", "c", 3, 1, 1))),
                Map.of(),
                List.of(guild("local", "Local")),
                Map.of("alpha", "Alpha", "beta", "Beta"),
                "Vardera",
                (guild, province) -> province == 3 ? 5 : 0);

        assertEquals(List.of("The Vardera Network", "The Local Network"),
                summaries.stream().map(Summary::name).toList());
        assertTrue(summaries.getFirst().global());
    }

    @Test
    void anEmptyGraphHasNoSummaries() {
        assertTrue(NetworkSummary.summarize(null, Map.of(), List.of(), Map.of(), "Vardera",
                (guild, province) -> 1).isEmpty());
        assertTrue(NetworkSummary.summarize(
                new TradeGraph(List.of(), List.of(), List.of()),
                null, null, null, null, null).isEmpty());
    }

    private static String nameOf(String guildName) {
        return only(graph(network(false, node("alpha", "port", 1, 1, 0))),
                List.of(guild("g", guildName)),
                Map.of("alpha", "Alpha"),
                "Vardera",
                (guild, province) -> 4).name();
    }

    private static Summary only(
            TradeGraph graph,
            List<GuildLabel> guilds,
            Map<String, String> factions,
            String mapName,
            NetworkSummary.RawTrade trade) {
        List<Summary> summaries = NetworkSummary.summarize(graph, Map.of(), guilds, factions, mapName, trade);
        assertEquals(1, summaries.size());
        return summaries.getFirst();
    }

    private static int lowest(Summary summary) {
        int lowest = Integer.MAX_VALUE;
        for (Node node : summary.network().nodes()) lowest = Math.min(lowest, node.provinceId());
        return lowest;
    }

    private static GuildLabel guild(String id, String name) {
        return new GuildLabel(id, name);
    }

    private static TradeGraph graph(Network... networks) {
        List<Node> nodes = new ArrayList<>();
        for (Network network : networks) nodes.addAll(network.nodes());
        return new TradeGraph(nodes, List.of(), List.of(networks));
    }

    private static Network network(boolean global, Node... nodes) {
        return new Network(List.of(nodes), global);
    }

    private static Node node(String faction, String installation, int province, int slots, int network) {
        return new Node(faction, installation, InstallationKind.PORT, province, 0, 0, 1, slots, network);
    }
}
