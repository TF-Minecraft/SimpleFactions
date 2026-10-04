package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.Highway.HubSite;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.guild.network.RailRoutes;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.ProvinceData;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.Site;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Faction;

class HubNetworkTest {
    private ProvinceManager provinces;

    @BeforeEach
    void setUp() {
        HubTransport.resetConfig();
        provinces = new ProvinceManager();
        // 1 and 5 are coastal land joined by sea 2-3-4; 6 is inland beside 5; 7 sits on its own lake 8.
        Map<Integer, Province> map = new HashMap<>();
        map.put(1, province(1, Terrain.PLAINS));
        map.put(2, province(2, Terrain.SEA));
        map.put(3, province(3, Terrain.SEA));
        map.put(4, province(4, Terrain.SEA));
        map.put(5, province(5, Terrain.PLAINS));
        map.put(6, province(6, Terrain.PLAINS));
        map.put(7, province(7, Terrain.PLAINS));
        map.put(8, province(8, Terrain.SEA));
        join(map, 1, 2);
        join(map, 2, 3);
        join(map, 3, 4);
        join(map, 4, 5);
        join(map, 5, 6);
        join(map, 7, 8);
        provinces.start(map);
    }

    @AfterEach
    void restore() {
        HubNetwork.setHighwayForTests(null, null);
        HubNetwork.setLinksForTests(null);
        HubTransport.resetConfig();
    }

    @Test
    void stationsConnectOnlyWhenTheGraphHasAnEdge_andDistanceIsTheTrackLength() {
        Installation a = installation("a", InstallationKind.TRAIN_STATION, 1, 0, 0);
        Installation b = installation("b", InstallationKind.TRAIN_STATION, 5, 300, 400);

        use(TestGraphs.rail("owner", List.of(a, b), (from, to) -> false, 0));
        assertNull(HubNetwork.connect(a, b, provinces));

        use(TestGraphs.rail("owner", List.of(a, b), (from, to) -> true, 2000));
        Link link = HubNetwork.connect(a, b, provinces);

        assertNotNull(link);
        assertEquals(Mode.RAIL, link.mode());
        assertEquals(2000, link.distance());
        assertEquals(0.40 * 0.81, link.tradeFactor(), 1e-9);
        assertEquals(0.80 * 0.81, link.productionFactor(), 1e-9);
    }

    @Test
    void aPairWithNoEdgeStaysUnconnectedAfterRoutesAreForgotten() {
        Installation a = installation("a", InstallationKind.TRAIN_STATION, 1, 0, 0);
        Installation b = installation("b", InstallationKind.TRAIN_STATION, 5, 300, 400);
        use(TestGraphs.rail("owner", List.of(a, b), (from, to) -> false, 0));

        assertNull(HubNetwork.connect(a, b, provinces));
        HubNetwork.forgetRoutes();
        assertNull(HubNetwork.connect(a, b, provinces));
        assertNull(HubNetwork.connect(b, a, provinces));
    }

    @Test
    void portsConnectAcrossSharedSea_inAStraightLine() {
        Installation a = installation("a", InstallationKind.PORT, 1, 0, 0);
        Installation b = installation("b", InstallationKind.PORT, 5, 600, 800);
        Installation inland = installation("c", InstallationKind.PORT, 6, 700, 800);
        Installation lake = installation("d", InstallationKind.PORT, 7, 100, 100);
        use(sea(List.of(a, b, inland, lake)));

        Link link = HubNetwork.connect(a, b, provinces);

        assertNotNull(link);
        assertEquals(Mode.SEA, link.mode());
        assertEquals(1000, link.distance(), 1e-9);
        assertEquals(0.30 * 0.85, link.tradeFactor(), 1e-9);
        assertNull(HubNetwork.connect(a, inland, provinces));
        assertNull(HubNetwork.connect(a, lake, provinces));
    }

    @Test
    void airportsConnectAtAnyDistance() {
        Installation a = installation("a", InstallationKind.AIRPORT, 1, 0, 0);
        Installation far = installation("c", InstallationKind.AIRPORT, 7, 2000, 2000);
        use(TestGraphs.rail("owner", List.of(a, far), (from, to) -> false, 0));

        Link link = HubNetwork.connect(a, far, provinces);

        assertNotNull(link);
        assertEquals(Mode.AIR, link.mode());
        assertEquals(Math.hypot(2000, 2000), link.distance(), 1e-9);
        assertEquals(0.50 * Math.pow(0.80, link.distance() / 1000.0), link.productionFactor(), 1e-9);
    }

    @Test
    void fortsAndTheSameInstallationDoNotConnect_aSameProvinceEdgeDoes() {
        Installation station = installation("a", InstallationKind.TRAIN_STATION, 1, 0, 0);
        Installation sameProvince = installation("c", InstallationKind.TRAIN_STATION, 1, 50, 50);
        Installation fort = installation("d", InstallationKind.FORT, 5, 10, 10);
        Installation otherFort = installation("e", InstallationKind.FORT, 6, 10, 10);
        use(TestGraphs.rail("owner", List.of(station, sameProvince), (from, to) -> true, 10));

        assertEquals(Mode.RAIL, HubNetwork.connect(station, sameProvince, provinces).mode());
        assertNull(HubNetwork.connect(station, station, provinces));
        assertNull(HubNetwork.connect(fort, otherFort, provinces));
    }

    @Test
    void linksRunBothWaysOnlyWhenBothEndsAreHubbed() {
        Installation a = installation("a", InstallationKind.TRAIN_STATION, 1, 0, 0);
        Installation b = installation("b", InstallationKind.TRAIN_STATION, 5, 0, 0);
        Installation c = installation("c", InstallationKind.TRAIN_STATION, 6, 0, 0);
        TradeGraph graph = TestGraphs.rail("owner", List.of(a, b, c), (from, to) -> true, 500);
        Guild guild = guild("guild");

        use(graph, Map.of("guild", TestGraphs.hubs("owner", a, b, c)));
        List<Link> links = HubNetwork.linksFor(guild);

        assertEquals(6, links.size());
        assertEquals(1, links.get(0).fromProvince());
        assertEquals(5, links.get(0).toProvince());
        assertEquals(1, links.get(1).fromProvince());
        assertEquals(6, links.get(1).toProvince());
        assertEquals(5, links.get(2).fromProvince());
        assertEquals(1, links.get(2).toProvince());

        use(graph, Map.of("guild", TestGraphs.hubs("owner", a)));
        assertTrue(HubNetwork.linksFor(guild).isEmpty());
    }

    @Test
    void aPairUsesTheModeWithTheHigherTradeShareAndRailWinsATie() {
        Installation a = installation("a", InstallationKind.PORT, 1, 0, 0);
        Installation b = installation("b", InstallationKind.PORT, 5, 600, 800);
        use(seaAndRail(a, b));
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.trade", 0.50);
        config.set("supply-hubs.transport.rail.kept-per-1000-blocks", 1.0);
        config.set("supply-hubs.transport.sea.trade", 0.60);
        config.set("supply-hubs.transport.sea.kept-per-1000-blocks", 1.0);
        HubTransport.loadConfig(config);
        assertEquals(Mode.SEA, HubNetwork.connect(a, b, provinces).mode());

        config.set("supply-hubs.transport.rail.trade", 0.60);
        HubTransport.loadConfig(config);
        assertEquals(Mode.RAIL, HubNetwork.connect(a, b, provinces).mode());
    }

    @Test
    void potentialTradeUsesTheGraphAndTheSendingHub() {
        Installation near = installation("near", InstallationKind.TRAIN_STATION, 1, 0, 0);
        Installation target = installation("target", InstallationKind.TRAIN_STATION, 5, 1000, 0);
        Guild guild = guild("bog");
        provinces.get(1).setData(guild.getId(), new ProvinceDataEntry(guild, 10, 0));
        use(TestGraphs.linked("owner", 1000, near, target),
                Map.of("bog", TestGraphs.hubs("owner", near)));

        assertEquals(3.6, HubNetwork.potentialTrade(guild, target, provinces), 0.01);
        assertEquals(0, HubNetwork.potentialTrade(guild("other"), target, provinces), 0.01);
    }

    @Test
    void aDormantHubIsLeftOutOfTheActiveSet() {
        Installation a = installation("a", InstallationKind.TRAIN_STATION, 1, 0, 0);
        Installation b = installation("b", InstallationKind.TRAIN_STATION, 5, 0, 0);
        TradeGraph graph = TestGraphs.linked("home", 0, a, b);
        Faction home = mock(Faction.class);
        InstallationHandler sites = mock(InstallationHandler.class);
        when(home.getId()).thenReturn("home");
        when(home.hasFactionRule(Rules.SUPPLY_HUBS)).thenReturn(true);
        when(home.getInstallationHandler()).thenReturn(sites);
        when(sites.getById("a")).thenReturn(a);
        when(sites.getById("b")).thenReturn(b);
        when(sites.getAll()).thenReturn(List.of(a, b));
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
        when(guild.getFaction()).thenReturn(home);
        when(guild.getModifier(GuildModifier.HUB_LIMIT)).thenReturn(1.0);
        SupplyHub older = new SupplyHub("home", "a", 1);
        SupplyHub newer = new SupplyHub("home", "b", 2);
        when(guild.getSupplyHubs()).thenReturn(List.of(older, newer));

        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
                MockedStatic<InstallationConfigLoader> config = mockStatic(InstallationConfigLoader.class)) {
            factions.when(() -> FactionManager.getByString("home")).thenReturn(home);
            config.when(() -> InstallationConfigLoader.getHubSlots(InstallationKind.TRAIN_STATION, 1))
                    .thenReturn(1);
            assertEquals(Set.of(new HubSite("home", "a")), HubNetwork.activeHubs(guild, List.of(guild), graph));
        }
    }

    private void use(TradeGraph graph) {
        HubNetwork.setHighwayForTests(graph, Map.of());
    }

    private void use(TradeGraph graph, Map<String, Set<HubSite>> hubbed) {
        HubNetwork.setHighwayForTests(graph, hubbed);
    }

    private TradeGraph sea(List<Installation> sites) {
        return TradeGraphBuilder.build(owned(sites), coast(), (from, to) -> Optional.empty(), point -> 0);
    }

    private TradeGraph seaAndRail(Installation a, Installation b) {
        return TradeGraphBuilder.build(owned(List.of(a, b)), coast(),
                (from, to) -> Optional.of(new RailRoutes.Route(1000, List.of())), point -> 0);
    }

    private static List<Site> owned(List<Installation> sites) {
        List<Site> built = new java.util.ArrayList<>();
        for (Installation site : sites) {
            built.add(new Site("owner", site, 1, true));
        }
        return built;
    }

    private static Map<Integer, ProvinceData> coast() {
        Map<Integer, ProvinceData> provinces = new HashMap<>();
        provinces.put(1, new ProvinceData(Terrain.PLAINS, Set.of(2)));
        provinces.put(2, new ProvinceData(Terrain.SEA, Set.of(1, 3)));
        provinces.put(3, new ProvinceData(Terrain.SEA, Set.of(2, 4)));
        provinces.put(4, new ProvinceData(Terrain.SEA, Set.of(3, 5)));
        provinces.put(5, new ProvinceData(Terrain.PLAINS, Set.of(4, 6)));
        provinces.put(6, new ProvinceData(Terrain.PLAINS, Set.of(5)));
        provinces.put(7, new ProvinceData(Terrain.PLAINS, Set.of(8)));
        provinces.put(8, new ProvinceData(Terrain.SEA, Set.of(7)));
        return provinces;
    }

    private static Guild guild(String id) {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn(id);
        when(guild.getFaction()).thenReturn(null);
        when(guild.getModifier(GuildModifier.HUB_TRADE)).thenReturn(0.0);
        return guild;
    }

    private static Installation installation(String id, InstallationKind kind, int province, int x, int z) {
        return new Installation(id, id, kind, province, x, z, 1L);
    }

    private static Province province(int id, Terrain terrain) {
        return new Province(id, terrain.name(), 50);
    }

    private static void join(Map<Integer, Province> map, int a, int b) {
        map.get(a).addNeighbour(b);
        map.get(b).addNeighbour(a);
    }
}
