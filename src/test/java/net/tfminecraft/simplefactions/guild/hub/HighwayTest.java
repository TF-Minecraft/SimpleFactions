package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.Highway.HubSite;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.guild.network.RailRoutes;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Edge;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.ProvinceData;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.Site;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Faction;

/** The hop formula, isolated from the capital walk except where the walk is the point. */
class HighwayTest {
    private Guild guild;
    private MockedStatic<TitleManager> titles;
    private MockedStatic<FactionManager> factions;
    private boolean provincesWereEnabled;
    private double previousStrength;
    private Double previousPlains;

    @BeforeEach
    void setUp() {
        provincesWereEnabled = Cache.provincesEnabled;
        Cache.provincesEnabled = true;
        previousStrength = Cache.supplyHubNoHubStrength;
        Cache.supplyHubNoHubStrength = 0.5;
        previousPlains = Cache.tradeCarry.get(Terrain.PLAINS);
        Cache.tradeCarry.put(Terrain.PLAINS, 0.85);
        Faction host = mock(Faction.class);
        when(host.getId()).thenReturn("host");
        when(host.hasFactionRule(Rules.SUPPLY_HUBS)).thenReturn(true);
        when(host.getModifiers(any(), any(), any())).thenReturn(List.of());
        guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
        when(guild.getFaction()).thenReturn(host);
        when(guild.hasCapital()).thenReturn(true);
        when(guild.getCapital()).thenReturn(1);
        when(guild.getModifier(GuildModifier.TRADE_POWER)).thenReturn(20.0);
        when(guild.getModifier(GuildModifier.TRADE_CARRY)).thenReturn(1.0);
        when(guild.getModifier(GuildModifier.PRODUCTION)).thenReturn(10.0);
        when(guild.getModifier(GuildModifier.HUB_TRADE)).thenReturn(0.0);
        when(guild.getModifier(GuildModifier.HUB_PRODUCTION)).thenReturn(0.0);
        titles = mockStatic(TitleManager.class);
        factions = mockStatic(FactionManager.class);
        factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(guild);
        factions.when(FactionManager::getAllGuilds).thenReturn(List.of(guild));
        titles.when(() -> TitleManager.getByProvince(org.mockito.ArgumentMatchers.anyInt())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        titles.close();
        factions.close();
        HubNetwork.setHighwayForTests(null, null);
        HubTransport.resetConfig();
        Cache.supplyHubNoHubStrength = previousStrength;
        Cache.provincesEnabled = provincesWereEnabled;
        if (previousPlains == null) {
            Cache.tradeCarry.remove(Terrain.PLAINS);
        } else {
            Cache.tradeCarry.put(Terrain.PLAINS, previousPlains);
        }
    }

    @Test
    void wouldArriveAssumesAHubAtTheNode() {
        TradeGraph graph = edge(0);
        ProvinceManager provinces = isolated(1, 2);
        place(provinces, 1, 100);
        Node destination = node(graph, "b");
        assertEquals(40, Highway.wouldArrive(provinces, guild, graph, Set.of(site("a")), destination, 0), 1e-9);
        assertEquals(20, Highway.wouldArrive(provinces, guild, graph, Set.of(), destination, 0), 1e-9);
        assertEquals(20, Highway.wouldArrive(provinces, guild, graph, Set.of(site("b")), destination, 0), 1e-9);
        assertEquals(52, Highway.wouldArrive(provinces, guild, graph, Set.of(site("a")), destination, 0.30), 1e-9);
        assertEquals(20, Highway.wouldArrive(provinces, guild, graph, Set.of(), destination, 0.30), 1e-9);

        TradeGraph longer = edge(1000);
        assertEquals(18, Highway.wouldArrive(provinces, guild, longer, Set.of(), node(longer, "b"), 0), 1e-9);

        Installation rich = station("a", 1);
        Installation here = station("b", 2);
        Installation quiet = station("c", 3);
        TradeGraph fork = TestGraphs.rail("owner", List.of(rich, here, quiet),
                (from, to) -> "b".equals(from.getId()) || "b".equals(to.getId()), 0);
        ProvinceManager forkProvinces = isolated(1, 2, 3);
        place(forkProvinces, 1, 100);
        place(forkProvinces, 3, 10);
        assertEquals(40, Highway.wouldArrive(
                forkProvinces, guild, fork, TestGraphs.hubs("owner", rich, quiet), fork.node("owner", "b"), 0), 1e-9);
    }

    @Test
    void fourStrengthsOnOneEdge() {
        TradeGraph graph = edge(0);
        assertEquals(40, deliver(graph, both(), 100), 1e-9);
        assertEquals(20, deliver(graph, Set.of(site("a")), 100), 1e-9);
        assertEquals(20, deliver(graph, Set.of(site("b")), 100), 1e-9);
        assertEquals(10, deliver(graph, Set.of(), 100), 1e-9);
    }

    @Test
    void hubTradeBoostAppliesOnlyAtTheSendingEnd() {
        when(guild.getModifier(GuildModifier.HUB_TRADE)).thenReturn(0.30);
        TradeGraph graph = edge(0);
        // 0.40 * 1.30 = 0.52, and only the sending hub raises the share.
        assertEquals(52, arrive(graph, both(), 100, true), 1e-9);
        assertEquals(26, arrive(graph, Set.of(site("a")), 100, true), 1e-9);
        assertEquals(20, arrive(graph, Set.of(site("b")), 100, true), 1e-9);
        assertEquals(10, arrive(graph, Set.of(), 100, true), 1e-9);
    }

    @Test
    void aDormantHubCountsAsNoHub() {
        TradeGraph graph = edge(0);
        // The dormant end is simply absent from the active set.
        assertEquals(20, deliver(graph, Set.of(site("a")), 100), 1e-9);
        assertEquals(10, deliver(graph, Set.of(), 100), 1e-9);
    }

    @Test
    void aChainOfThreeSettlesAtTheFixedPoint() {
        Installation a = station("a", 1);
        Installation b = station("b", 2);
        Installation c = station("c", 3);
        TradeGraph graph = TestGraphs.rail("owner", List.of(a, b, c), (from, to) -> consecutive(from, to), 0);
        Set<HubSite> hubs = TestGraphs.hubs("owner", a, b, c);
        ProvinceManager provinces = isolated(1, 2, 3);
        place(provinces, 1, 100);
        Highway.deliver(provinces, guild, graph, hubs, 0);
        assertEquals(40, raw(provinces, 2), 1e-9);
        assertEquals(16, raw(provinces, 3), 1e-9);
        assertEquals(100, raw(provinces, 1), 1e-9);
        assertEquals(fixedPoint(graph, hubs, Map.of(1, 100.0, 2, 0.0, 3, 0.0), 0),
                Map.of(1, raw(provinces, 1), 2, raw(provinces, 2), 3, raw(provinces, 3)));
    }

    @Test
    void aLaterRaiseStillReachesTheFarNode() {
        // A is richest, then C, then B. One pass that does not revisit C leaves D short.
        Installation a = station("a", 1);
        Installation b = station("b", 2);
        Installation c = station("c", 3);
        Installation d = station("d", 4);
        TradeGraph graph = TestGraphs.rail("owner", List.of(a, b, c, d),
                (from, to) -> Math.abs(from.getProvince() - to.getProvince()) == 1, 0);
        Set<HubSite> hubs = TestGraphs.hubs("owner", a, b, c, d);
        ProvinceManager provinces = isolated(1, 2, 3, 4);
        place(provinces, 1, 100);
        place(provinces, 3, 10);
        Highway.deliver(provinces, guild, graph, hubs, 0);
        assertEquals(40, raw(provinces, 2), 1e-9);
        assertEquals(16, raw(provinces, 3), 1e-9);
        assertEquals(6.4, raw(provinces, 4), 1e-9);
    }

    @Test
    void nothingUnderHalfAPointIsDelivered() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.trade", 0.02);
        HubTransport.loadConfig(config);
        assertEquals(0, deliver(edge(0), both(), 20), 1e-9);
    }

    @Test
    void aGuildWithNoHubsStillGainsAtAFarPort() {
        Installation capital = station("a", 1);
        Installation far = station("b", 12);
        TradeGraph graph = TestGraphs.linked("owner", 0, capital, far);
        ProvinceManager provinces = road();
        HubNetwork.setHighwayForTests(graph, Map.of("guild", Set.of()));
        provinces.recalculateForSingleGuild(guild, false);
        assertEquals(0, provinces.get(8).getStoredGuildTrade(guild));
        assertEquals(2, provinces.get(12).getRawGuildTrade(guild), 1e-9);
    }

    @Test
    void theBetterModeCarriesTheHopAndRailWinsATie() {
        TradeGraph graph = seaAndRail();
        assertEquals(40, deliver(graph, both(), 100), 1e-9);
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.trade", 0.10);
        config.set("supply-hubs.transport.sea.trade", 0.30);
        config.set("supply-hubs.transport.sea.kept-per-1000-blocks", 1);
        config.set("supply-hubs.transport.rail.kept-per-1000-blocks", 1);
        HubTransport.loadConfig(config);
        assertEquals(30, deliver(graph, both(), 100), 1e-9);

        config.set("supply-hubs.transport.rail.trade", 0.30);
        HubTransport.loadConfig(config);
        Link chosen = Highway.bestLink(graph, node(graph, "a"), node(graph, "b"));
        assertEquals(Mode.RAIL, chosen.mode());
        assertEquals(30, deliver(graph, both(), 100), 1e-9);
    }

    @Test
    void productionMovesOnlyWhenBothEndsAreHubbed() {
        Installation capital = station("a", 1);
        Installation far = station("b", 12);
        TradeGraph graph = TestGraphs.linked("owner", 0, capital, far);
        ProvinceManager both = road();
        HubNetwork.setHighwayForTests(graph, Map.of("guild", TestGraphs.hubs("owner", capital, far)));
        both.recalculateForSingleGuild(guild, false);
        assertEquals(8, both.get(12).getGuildProduction(guild), 1e-9);

        ProvinceManager sender = road();
        HubNetwork.setHighwayForTests(graph, Map.of("guild", TestGraphs.hubs("owner", capital)));
        sender.recalculateForSingleGuild(guild, false);
        assertEquals(0, sender.get(12).getGuildProduction(guild));
        assertTrue(sender.get(12).getRawGuildTrade(guild) > 0);

        ProvinceManager none = road();
        HubNetwork.setHighwayForTests(graph, Map.of("guild", Set.of()));
        none.recalculateForSingleGuild(guild, false);
        assertEquals(0, none.get(12).getGuildProduction(guild));
        assertEquals(2, none.get(12).getRawGuildTrade(guild), 1e-9);
    }

    private double deliver(TradeGraph graph, Set<HubSite> hubbed, double atSender) {
        return arrive(graph, hubbed, atSender, false);
    }

    private double arrive(TradeGraph graph, Set<HubSite> hubbed, double atSender, boolean useModifier) {
        ProvinceManager provinces = isolated(1, 2);
        place(provinces, 1, atSender);
        double bonus = useModifier ? guild.getModifier(GuildModifier.HUB_TRADE) : 0;
        Highway.deliver(provinces, guild, graph, hubbed, bonus);
        return raw(provinces, 2);
    }

    /** Repeat every hop with no ordering until nothing moves. Provinces are not neighbours. */
    private Map<Integer, Double> fixedPoint(
            TradeGraph graph, Set<HubSite> hubbed, Map<Integer, Double> start, double bonus) {
        Map<Integer, Double> trade = new HashMap<>(start);
        boolean moved = true;
        while (moved) {
            moved = false;
            for (Node from : graph.nodes()) {
                double rawFrom = trade.getOrDefault(from.provinceId(), 0.0);
                boolean fromHub = hubbed.contains(new HubSite(from.ownerFactionId(), from.installationId()));
                for (Edge edge : graph.edgesAt(from)) {
                    Node to = edge.other(from);
                    Link link = Highway.bestLink(graph, from, to);
                    if (link == null || link.mode() != edge.mode()) {
                        continue;
                    }
                    boolean toHub = hubbed.contains(new HubSite(to.ownerFactionId(), to.installationId()));
                    double endFrom = fromHub ? 1 : Highway.noHubStrength();
                    double endTo = toHub ? 1 : Highway.noHubStrength();
                    double delivered = rawFrom * endFrom * link.boostedTradeFactor(fromHub ? bonus : 0) * endTo;
                    double rawTo = trade.getOrDefault(to.provinceId(), 0.0);
                    if (delivered >= 0.5 && delivered > rawTo) {
                        trade.put(to.provinceId(), delivered);
                        moved = true;
                    }
                }
            }
        }
        return trade;
    }

    private static boolean consecutive(Installation from, Installation to) {
        return Math.abs(from.getProvince() - to.getProvince()) == 1;
    }

    private static Set<HubSite> both() {
        return Set.of(site("a"), site("b"));
    }

    private static HubSite site(String id) {
        return new HubSite("owner", id);
    }

    private static TradeGraph edge(double length) {
        return TestGraphs.linked("owner", length, station("a", 1), station("b", 2));
    }

    private static TradeGraph seaAndRail() {
        Installation a = new Installation("a", "a", InstallationKind.PORT, 1, 0, 0, 1L);
        Installation b = new Installation("b", "b", InstallationKind.PORT, 2, 0, 0, 1L);
        Map<Integer, ProvinceData> provinces = Map.of(
                1, new ProvinceData(Terrain.PLAINS, Set.of(9)),
                9, new ProvinceData(Terrain.SEA, Set.of(1, 2)),
                2, new ProvinceData(Terrain.PLAINS, Set.of(9)));
        return TradeGraphBuilder.build(
                List.of(new Site("owner", a, 1, true), new Site("owner", b, 1, true)),
                provinces,
                (from, to) -> java.util.Optional.of(new RailRoutes.Route(0, List.of())),
                point -> 0);
    }

    private static Node node(TradeGraph graph, String id) {
        return graph.node("owner", id);
    }

    private ProvinceManager isolated(int... ids) {
        Map<Integer, Province> map = new HashMap<>();
        for (int id : ids) {
            map.put(id, new Province(id, Terrain.PLAINS.name(), 50));
        }
        ProvinceManager provinces = new ProvinceManager();
        provinces.start(map);
        return provinces;
    }

    private ProvinceManager road() {
        Map<Integer, Province> map = new HashMap<>();
        for (int id = 1; id <= 24; id++) {
            map.put(id, new Province(id, Terrain.PLAINS.name(), 50));
        }
        for (int id = 1; id < 24; id++) {
            map.get(id).addNeighbour(id + 1);
            map.get(id + 1).addNeighbour(id);
        }
        ProvinceManager provinces = new ProvinceManager();
        provinces.start(map);
        return provinces;
    }

    private void place(ProvinceManager provinces, int id, double trade) {
        provinces.get(id).setData(guild.getId(), new ProvinceDataEntry(guild, trade, 0));
    }

    private double raw(ProvinceManager provinces, int id) {
        return provinces.get(id).getRawGuildTrade(guild);
    }

    private static Installation station(String id, int province) {
        return new Installation(id, id, InstallationKind.TRAIN_STATION, province, 0, 0, 1L);
    }
}
