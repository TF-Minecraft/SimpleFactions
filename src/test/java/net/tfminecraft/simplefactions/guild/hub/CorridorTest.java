package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.Highway.HubSite;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.guild.network.RailRoutes;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.ProvinceData;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.Site;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceLookup.Point;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Faction;

/** Corridor deposits after the highway has settled. Provinces are not neighbours unless a test joins them. */
class CorridorTest {
    private Guild guild;
    private MockedStatic<TitleManager> titles;
    private MockedStatic<FactionManager> factions;
    private boolean provincesWereEnabled;
    private double previousStrength;
    private double previousCorridor;
    private Double previousPlains;

    @BeforeEach
    void setUp() {
        provincesWereEnabled = Cache.provincesEnabled;
        Cache.provincesEnabled = true;
        previousStrength = Cache.supplyHubNoHubStrength;
        previousCorridor = Cache.supplyHubCorridorShare;
        Cache.supplyHubNoHubStrength = 0.5;
        Cache.supplyHubCorridorShare = 0.5;
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
        titles.when(() -> TitleManager.getByProvince(anyInt())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        titles.close();
        factions.close();
        HubNetwork.setHighwayForTests(null, null);
        HubTransport.resetConfig();
        Cache.supplyHubNoHubStrength = previousStrength;
        Cache.supplyHubCorridorShare = previousCorridor;
        Cache.provincesEnabled = provincesWereEnabled;
        if (previousPlains == null) {
            Cache.tradeCarry.remove(Terrain.PLAINS);
        } else {
            Cache.tradeCarry.put(Terrain.PLAINS, previousPlains);
        }
    }

    @Test
    void depositsSitOnTheStoredProvincesAndSpreadToANeighbour() {
        TradeGraph graph = rail(0, 10, 11);
        ProvinceManager provinces = map(1, 2, 10, 11, 12, 99);
        provinces.get(10).addNeighbour(12);
        place(provinces, 1, 100);
        Highway.deposit(provinces, guild, graph, both(), 0);
        assertEquals(20, raw(provinces, 10), 1e-9);
        assertEquals(20, raw(provinces, 11), 1e-9);
        assertEquals(20 * 0.85, raw(provinces, 12), 1e-9);
        assertEquals(0, raw(provinces, 99), 1e-9);
        assertEquals(0, raw(provinces, 2), 1e-9);
        assertEquals(100, raw(provinces, 1), 1e-9);
    }

    @Test
    void depositsFadeFromTheSenderAndBothDirectionsLeaveTheNearerAmount() {
        TradeGraph graph = rail(3000, 10, 11);
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.kept-per-1000-blocks", 0.90);
        HubTransport.loadConfig(config);
        ProvinceManager oneWay = map(1, 2, 10, 11);
        place(oneWay, 1, 100);
        Highway.deposit(oneWay, guild, graph, both(), 0);
        assertEquals(18, raw(oneWay, 10), 1e-9);
        assertEquals(16.2, raw(oneWay, 11), 1e-9);
        assertTrue(raw(oneWay, 10) > raw(oneWay, 11));

        ProvinceManager bothWays = map(1, 2, 10, 11);
        place(bothWays, 1, 100);
        place(bothWays, 2, 100);
        Highway.deposit(bothWays, guild, graph, both(), 0);
        // Each province is the near end of one direction, so both keep the larger amount.
        assertEquals(18, raw(bothWays, 10), 1e-9);
        assertEquals(18, raw(bothWays, 11), 1e-9);
    }

    @Test
    void aHubAtTheSenderDoublesTheDepositAndTheReceiverDoesNot() {
        TradeGraph graph = rail(0, 10);
        ProvinceManager hubbed = map(1, 2, 10);
        place(hubbed, 1, 100);
        Highway.deposit(hubbed, guild, graph, Set.of(site("a")), 0);
        ProvinceManager open = map(1, 2, 10);
        place(open, 1, 100);
        Highway.deposit(open, guild, graph, Set.of(), 0);
        ProvinceManager receiver = map(1, 2, 10);
        place(receiver, 1, 100);
        Highway.deposit(receiver, guild, graph, Set.of(site("b")), 0);
        assertEquals(20, raw(hubbed, 10), 1e-9);
        assertEquals(10, raw(open, 10), 1e-9);
        assertEquals(10, raw(receiver, 10), 1e-9);
        assertEquals(raw(hubbed, 10), raw(open, 10) * 2, 1e-9);
    }

    @Test
    void hubTradeRaisesOnlyAHubbedSenderAndStaysUnderTheCap() {
        TradeGraph graph = rail(0, 10);
        ProvinceManager provinces = map(1, 2, 10);
        place(provinces, 1, 100);
        Highway.deposit(provinces, guild, graph, Set.of(site("a")), 0.30);
        assertEquals(26, raw(provinces, 10), 1e-9);
        ProvinceManager receiver = map(1, 2, 10);
        place(receiver, 1, 100);
        Highway.deposit(receiver, guild, graph, Set.of(site("b")), 0.30);
        assertEquals(10, raw(receiver, 10), 1e-9);

        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.trade", 0.80);
        HubTransport.loadConfig(config);
        ProvinceManager capped = map(1, 2, 10);
        place(capped, 1, 100);
        Highway.deposit(capped, guild, graph, Set.of(site("a")), 0.50);
        assertEquals(47.5, raw(capped, 10), 1e-9);
    }

    @Test
    void airLeavesNothingWhileTheLosingModeStillDeposits() {
        Installation airport = new Installation("air-a", "air-a", InstallationKind.AIRPORT, 1, 0, 0, 1L);
        Installation other = new Installation("air-b", "air-b", InstallationKind.AIRPORT, 2, 1000, 0, 1L);
        TradeGraph air = TradeGraphBuilder.build(
                List.of(new Site("owner", airport, 1, true), new Site("owner", other, 1, true)),
                Map.of(
                        1, new ProvinceData(Terrain.PLAINS, Set.of()),
                        2, new ProvinceData(Terrain.PLAINS, Set.of()),
                        5, new ProvinceData(Terrain.PLAINS, Set.of())),
                (from, to) -> Optional.empty(),
                point -> 0);
        assertEquals(Mode.AIR, air.edges().get(0).mode());
        ProvinceManager sky = map(1, 2, 5);
        place(sky, 1, 100);
        Highway.deposit(sky, guild, air, Set.of(new HubSite("owner", "air-a")), 0);
        assertEquals(0, raw(sky, 5), 1e-9);
        assertEquals(0, raw(sky, 2), 1e-9);
        assertEquals(100, raw(sky, 1), 1e-9);

        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.sea.trade", 0.50);
        config.set("supply-hubs.transport.sea.kept-per-1000-blocks", 1);
        config.set("supply-hubs.transport.rail.trade", 0.10);
        config.set("supply-hubs.transport.rail.kept-per-1000-blocks", 1);
        HubTransport.loadConfig(config);
        TradeGraph both = seaAndRail();
        assertEquals(Mode.SEA, Highway.bestLink(both, both.node("owner", "a"), both.node("owner", "b")).mode());
        ProvinceManager provinces = map(1, 2, 8, 9);
        place(provinces, 1, 100);
        Highway.deposit(provinces, guild, both, Set.of(site("a")), 0);
        assertEquals(25, raw(provinces, 9), 1e-9);
        assertEquals(5, raw(provinces, 8), 1e-9);
    }

    @Test
    void aMissingProvinceIsSkippedAndKeepsItsPlaceInTheLength() {
        TradeGraph graph = rail(1000, 10, 404, 11);
        assertEquals(List.of(10, 404, 11), graph.edges().get(0).provinces());
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.kept-per-1000-blocks", 0.50);
        HubTransport.loadConfig(config);
        ProvinceManager provinces = map(1, 2, 10, 11);
        assertFalse(provinces.contains(404));
        place(provinces, 1, 100);
        Highway.deposit(provinces, guild, graph, both(), 0);
        assertFalse(provinces.contains(404));
        // Province i of 3, including the missing id, sits at L * (i + 1) / 4.
        assertEquals(20 * Math.pow(0.5, 0.25), raw(provinces, 10), 1e-9);
        assertEquals(20 * Math.pow(0.5, 0.75), raw(provinces, 11), 1e-9);
    }

    @Test
    void oneDepositDoesNotRaiseTheNextDepositSource() {
        TradeGraph graph = chain();
        assertEquals(List.of(10), graph.edges().get(0).provinces());
        ProvinceManager provinces = map(1, 2, 3, 10, 20);
        provinces.get(10).addNeighbour(2);
        place(provinces, 1, 100);
        place(provinces, 2, 10);
        Highway.deposit(provinces, guild, graph, Set.of(site("a"), site("b"), site("c")), 0);
        assertEquals(17, raw(provinces, 2), 1e-9);
        assertEquals(2, raw(provinces, 20), 1e-9);
    }

    @Test
    void aDepositDoesNotChangeWhatTheHighwayDelivered() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.trade", 0.95);
        config.set("supply-hubs.transport.rail.production", 0);
        config.set("supply-hubs.transport.rail.kept-per-1000-blocks", 1);
        HubTransport.loadConfig(config);
        Cache.supplyHubCorridorShare = 1;
        TradeGraph graph = feedbackChain();
        ProvinceManager provinces = map(1, 2, 3, 10);
        provinces.get(10).addNeighbour(2);
        HubNetwork.setHighwayForTests(graph, Map.of("guild", Set.of(site("a"), site("c"))));
        provinces.recalculateForSingleGuild(guild, false);
        // The spread raises B. C would rise too if delivery ran again.
        assertEquals(20 * 0.95 * 0.85, raw(provinces, 2), 1e-9);
        assertEquals(20 * 0.95 * 0.5 * 0.5 * 0.95, raw(provinces, 3), 1e-9);
        double delivered = raw(provinces, 3);
        provinces.recalculateForSingleGuild(guild, false);
        assertEquals(delivered, raw(provinces, 3), 1e-9);
        assertEquals(20 * 0.95 * 0.85, raw(provinces, 2), 1e-9);
    }

    @Test
    void aRingOfSeaEdgesIsUnchangedOnTheSecondRecalculation() {
        TradeGraph graph = seaRing();
        assertEquals(3, graph.edges().size());
        ProvinceManager provinces = map(1, 2, 3, 10, 11, 12);
        HubNetwork.setHighwayForTests(graph, Map.of("guild", Set.of(site("a"), site("b"), site("c"))));
        provinces.recalculateForSingleGuild(guild, false);
        Map<Integer, Double> first = snapshot(provinces);
        provinces.recalculateForSingleGuild(guild, false);
        assertEquals(first, snapshot(provinces));
        assertEquals(20, first.get(1), 1e-9);
        assertEquals(6, first.get(2), 1e-9);
        assertEquals(6, first.get(3), 1e-9);
        assertEquals(3, first.get(10), 1e-9);
        assertEquals(0.9, first.get(11), 1e-9);
        assertEquals(3, first.get(12), 1e-9);
        assertTrue(first.values().stream().allMatch(value -> value <= 20));
    }

    private Map<Integer, Double> snapshot(ProvinceManager provinces) {
        Map<Integer, Double> trade = new HashMap<>();
        for (Province province : provinces.getProvinces()) {
            trade.put(province.getId(), province.getRawGuildTrade(guild));
        }
        return trade;
    }

    private static Set<HubSite> both() {
        return Set.of(site("a"), site("b"));
    }

    private static HubSite site(String id) {
        return new HubSite("owner", id);
    }

    private static TradeGraph rail(double length, int... corridor) {
        return railBetween("a", 1, "b", 2, length, corridor);
    }

    private static TradeGraph railBetween(
            String fromId, int fromProvince, String toId, int toProvince, double length, int... corridor) {
        Installation from = station(fromId, fromProvince);
        Installation to = station(toId, toProvince);
        Map<Integer, ProvinceData> data = new HashMap<>();
        data.put(fromProvince, new ProvinceData(Terrain.PLAINS, Set.of()));
        data.put(toProvince, new ProvinceData(Terrain.PLAINS, Set.of()));
        List<Point> points = new ArrayList<>();
        points.add(new Point(fromProvince, 0, 0));
        for (int id : corridor) {
            data.put(id, new ProvinceData(Terrain.PLAINS, Set.of()));
            points.add(new Point(id, 0, 0));
        }
        points.add(new Point(toProvince, 0, 0));
        return TradeGraphBuilder.build(
                List.of(new Site("owner", from, 1, true), new Site("owner", to, 1, true)),
                data,
                (left, right) -> Optional.of(new RailRoutes.Route(length, points)),
                point -> (int) point.x());
    }

    /** a-b passes province 10, which the live map joins to b. b-c passes province 20. */
    private static TradeGraph chain() {
        Installation a = station("a", 1);
        Installation b = station("b", 2);
        Installation c = station("c", 3);
        Map<Integer, ProvinceData> data = new HashMap<>();
        for (int id : new int[] {1, 2, 3, 10, 20}) {
            data.put(id, new ProvinceData(Terrain.PLAINS, Set.of()));
        }
        return TradeGraphBuilder.build(
                List.of(new Site("owner", a, 1, true), new Site("owner", b, 1, true), new Site("owner", c, 1, true)),
                data,
                (from, to) -> {
                    int left = Math.min(from.getProvince(), to.getProvince());
                    int right = Math.max(from.getProvince(), to.getProvince());
                    if (left == 1 && right == 2) {
                        return Optional.of(new RailRoutes.Route(0, List.of(
                                new Point(1, 0, 0), new Point(10, 0, 0), new Point(2, 0, 0))));
                    }
                    if (left == 2 && right == 3) {
                        return Optional.of(new RailRoutes.Route(0, List.of(
                                new Point(2, 0, 0), new Point(20, 0, 0), new Point(3, 0, 0))));
                    }
                    return Optional.empty();
                },
                point -> (int) point.x());
    }

    /** Rail from a to b through 10, and from b to c with nothing stored. No direct a-c edge. */
    private static TradeGraph feedbackChain() {
        Installation a = station("a", 1);
        Installation b = station("b", 2);
        Installation c = station("c", 3);
        Map<Integer, ProvinceData> data = new HashMap<>();
        for (int id : new int[] {1, 2, 3, 10}) {
            data.put(id, new ProvinceData(Terrain.PLAINS, Set.of()));
        }
        return TradeGraphBuilder.build(
                List.of(new Site("owner", a, 1, true), new Site("owner", b, 1, true), new Site("owner", c, 1, true)),
                data,
                (from, to) -> {
                    int left = Math.min(from.getProvince(), to.getProvince());
                    int right = Math.max(from.getProvince(), to.getProvince());
                    if (left == 1 && right == 2) {
                        return Optional.of(new RailRoutes.Route(0, List.of(
                                new Point(1, 0, 0), new Point(10, 0, 0), new Point(2, 0, 0))));
                    }
                    if (left == 2 && right == 3) {
                        return Optional.of(new RailRoutes.Route(0, List.of()));
                    }
                    return Optional.empty();
                },
                point -> (int) point.x());
    }

    private static TradeGraph seaRing() {
        Installation a = new Installation("a", "a", InstallationKind.PORT, 1, 0, 0, 1L);
        Installation b = new Installation("b", "b", InstallationKind.PORT, 2, 0, 0, 1L);
        Installation c = new Installation("c", "c", InstallationKind.PORT, 3, 0, 0, 1L);
        Map<Integer, ProvinceData> data = Map.of(
                1, new ProvinceData(Terrain.PLAINS, Set.of(10, 12)),
                2, new ProvinceData(Terrain.PLAINS, Set.of(10, 11)),
                3, new ProvinceData(Terrain.PLAINS, Set.of(11, 12)),
                10, new ProvinceData(Terrain.SEA, Set.of(1, 2)),
                11, new ProvinceData(Terrain.SEA, Set.of(2, 3)),
                12, new ProvinceData(Terrain.SEA, Set.of(3, 1)));
        return TradeGraphBuilder.build(
                List.of(new Site("owner", a, 1, true), new Site("owner", b, 1, true), new Site("owner", c, 1, true)),
                data,
                (from, to) -> Optional.empty(),
                point -> 0);
    }

    private static TradeGraph seaAndRail() {
        Installation a = new Installation("a", "a", InstallationKind.PORT, 1, 0, 0, 1L);
        Installation b = new Installation("b", "b", InstallationKind.PORT, 2, 0, 0, 1L);
        Map<Integer, ProvinceData> data = new HashMap<>();
        data.put(1, new ProvinceData(Terrain.PLAINS, Set.of(9)));
        data.put(2, new ProvinceData(Terrain.PLAINS, Set.of(9)));
        data.put(9, new ProvinceData(Terrain.SEA, Set.of(1, 2)));
        data.put(8, new ProvinceData(Terrain.PLAINS, Set.of()));
        return TradeGraphBuilder.build(
                List.of(new Site("owner", a, 1, true), new Site("owner", b, 1, true)),
                data,
                (from, to) -> Optional.of(new RailRoutes.Route(0, List.of(
                        new Point(1, 0, 0), new Point(8, 0, 0), new Point(2, 0, 0)))),
                point -> (int) point.x());
    }

    private ProvinceManager map(int... ids) {
        Map<Integer, Province> provinces = new HashMap<>();
        for (int id : ids) {
            provinces.put(id, new Province(id, Terrain.PLAINS.name(), 50));
        }
        ProvinceManager manager = new ProvinceManager();
        manager.start(provinces);
        return manager;
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
