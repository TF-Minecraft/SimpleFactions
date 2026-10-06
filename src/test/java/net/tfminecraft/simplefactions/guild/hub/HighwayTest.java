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
import java.util.Optional;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
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

class HighwayTest {
    private Guild guild;
    private Faction realm;
    private MockedStatic<TitleManager> titles;
    private MockedStatic<FactionManager> factions;
    private boolean provincesWereEnabled;
    private Double previousPlains;

    @BeforeEach
    void setUp() {
        provincesWereEnabled = Cache.provincesEnabled;
        Cache.provincesEnabled = true;
        previousPlains = Cache.tradeCarry.get(Terrain.PLAINS);
        Cache.tradeCarry.put(Terrain.PLAINS, 0.85);
        realm = mock(Faction.class);
        when(realm.getId()).thenReturn("home");
        when(realm.getModifiers(any(), any(), any())).thenReturn(List.of());
        guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
        when(guild.getFaction()).thenReturn(realm);
        when(guild.hasCapital()).thenReturn(true);
        when(guild.getCapital()).thenReturn(1);
        when(guild.getModifier(GuildModifier.TRADE_POWER)).thenReturn(20.0);
        when(guild.getModifier(GuildModifier.TRADE_CARRY)).thenReturn(1.0);
        when(guild.getModifier(GuildModifier.PRODUCTION)).thenReturn(10.0);
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
        TradeGraph.setLiveForTests(null);
        HubTransport.resetConfig();
        Cache.provincesEnabled = provincesWereEnabled;
        if (previousPlains == null) {
            Cache.tradeCarry.remove(Terrain.PLAINS);
        } else {
            Cache.tradeCarry.put(Terrain.PLAINS, previousPlains);
        }
    }

    @Test
    void hopUsesTheLowerAccessAtItsEnds() {
        TradeGraph graph = edge("left", "right", 0);
        ProvinceManager provinces = isolated(1, 2);
        placeTrade(provinces, 1, 100);

        Highway.deliver(provinces, guild, graph, Map.of("left", 0.8, "right", 0.25));

        assertEquals(10, raw(provinces, 2), 1e-9);
        assertEquals(0.10, Highway.hopFactor(
                Highway.bestLink(graph, graph.nodes().get(0), graph.nodes().get(1)), 0.8, 0.25), 1e-9);
    }

    @Test
    void zeroAccessAtEitherEndBlocksTradeAndProduction() {
        TradeGraph graph = edge("left", "right", 0);
        ProvinceManager provinces = isolated(1, 2);
        placeTrade(provinces, 1, 100);
        placeProduction(provinces, 1, 10);

        Highway.deliver(provinces, guild, graph, Map.of("left", 1.0, "right", 0.0));
        Highway.deliverProduction(provinces, guild, graph, Map.of("left", 1.0, "right", 0.0));

        assertEquals(0, raw(provinces, 2));
        assertEquals(0, production(provinces, 2));
    }

    @Test
    void aGuildWithNoHubsGetsFullStrengthBetweenRealmNodes() {
        TradeGraph graph = edge("home", "home", 0);
        ProvinceManager provinces = isolated(1, 2);
        placeTrade(provinces, 1, 100);

        Highway.deliver(provinces, guild, graph, Map.of("home", 1.0));

        assertEquals(40, raw(provinces, 2), 1e-9);
    }

    @Test
    void tradeAndProductionSettleAlongAChain() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.trade", 0.40);
        config.set("supply-hubs.transport.rail.production", 0.80);
        HubTransport.loadConfig(config);
        TradeGraph graph = chain();
        ProvinceManager provinces = isolated(1, 2, 3);
        placeTrade(provinces, 1, 100);
        placeProduction(provinces, 1, 10);

        Highway.deliver(provinces, guild, graph, Map.of("home", 1.0));
        Highway.deliverProduction(provinces, guild, graph, Map.of("home", 1.0));

        assertEquals(40, raw(provinces, 2), 1e-9);
        assertEquals(16, raw(provinces, 3), 1e-9);
        assertEquals(8, production(provinces, 2), 1e-9);
        assertEquals(6.4, production(provinces, 3), 1e-9);
    }

    @Test
    void productionMovesDuringRecalculationWithoutHubs() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.production", 0.80);
        HubTransport.loadConfig(config);
        TradeGraph graph = edge("home", "home", 0);
        ProvinceManager provinces = road();
        TradeGraph.setLiveForTests(graph);

        provinces.recalculateForSingleGuild(guild, false);

        assertEquals(8, provinces.get(2).getGuildProduction(guild), 1e-9);
        assertEquals(17, provinces.get(2).getRawGuildTrade(guild), 1e-9);
    }

    @Test
    void betterModeIsChosenSeparatelyForTradeAndProduction() {
        TradeGraph graph = seaAndRail();
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.trade", 0.30);
        config.set("supply-hubs.transport.rail.production", 0.80);
        config.set("supply-hubs.transport.sea.trade", 0.60);
        config.set("supply-hubs.transport.sea.production", 0.20);
        config.set("supply-hubs.transport.sea.kept-per-1000-blocks", 1);
        config.set("supply-hubs.transport.rail.kept-per-1000-blocks", 1);
        HubTransport.loadConfig(config);
        ProvinceManager provinces = isolated(1, 2, 9);
        placeTrade(provinces, 1, 100);
        placeProduction(provinces, 1, 10);

        Highway.deliver(provinces, guild, graph, Map.of("home", 1.0));
        Highway.deliverProduction(provinces, guild, graph, Map.of("home", 1.0));

        assertEquals(60, raw(provinces, 2), 1e-9);
        assertEquals(8, production(provinces, 2), 1e-9);
        assertEquals(Mode.SEA, Highway.bestLink(graph, graph.nodes().get(0), graph.nodes().get(1)).mode());
    }

    @Test
    void overlappingRailLinesSettleAndDoNotGrowWhenRunAgain() {
        TradeGraph graph = railRoutes(
                List.of(station("a", 1), station("b", 2), station("c", 3)),
                Map.of("a-b", List.of(1, 10, 11, 2), "b-c", List.of(2, 11, 12, 3)));
        ProvinceManager provinces = isolated(1, 2, 3, 10, 11, 12);
        placeTrade(provinces, 1, 100);

        Highway.deliver(provinces, guild, graph, Map.of("home", 1.0));
        Map<Integer, Double> settled = rawAt(provinces, 1, 2, 3, 10, 11, 12);

        assertTrue(settled.get(3) > 0);
        Highway.deliver(provinces, guild, graph, Map.of("home", 1.0));
        assertEquals(settled, rawAt(provinces, 1, 2, 3, 10, 11, 12));
    }

    @Test
    void ringOfLinesDoesNotGrowWhenRunAgain() {
        TradeGraph graph = railRoutes(
                List.of(station("a", 1), station("b", 2), station("c", 3), station("d", 4)),
                Map.of(
                        "a-b", List.of(1, 2),
                        "b-c", List.of(2, 3),
                        "c-d", List.of(3, 4),
                        "a-d", List.of(1, 4)));
        ProvinceManager provinces = isolated(1, 2, 3, 4);
        provinces.setHighwayOverride(graph);

        provinces.recalculateForSingleGuild(guild, false);
        Map<Integer, Double> settled = rawAt(provinces, 1, 2, 3, 4);

        provinces.recalculateForSingleGuild(guild, false);
        assertEquals(settled, rawAt(provinces, 1, 2, 3, 4));
    }

    private static TradeGraph edge(String firstOwner, String secondOwner, double length) {
        Installation first = station("a", 1);
        Installation second = station("b", 2);
        return TradeGraphBuilder.build(
                List.of(new Site(firstOwner, first, true), new Site(secondOwner, second, true)),
                Map.of(),
                (left, right) -> Optional.of(new RailRoutes.Route(length, List.of())),
                point -> 0);
    }

    private static TradeGraph chain() {
        Installation a = station("a", 1);
        Installation b = station("b", 2);
        Installation c = station("c", 3);
        return TestGraphs.rail("home", List.of(a, b, c),
                (left, right) -> Math.abs(left.getProvince() - right.getProvince()) == 1, 0);
    }

    private static TradeGraph seaAndRail() {
        Installation a = new Installation("a", "a", InstallationKind.PORT, 1, 0, 0, 1L);
        Installation b = new Installation("b", "b", InstallationKind.PORT, 2, 0, 0, 1L);
        Map<Integer, ProvinceData> provinces = Map.of(
                1, new ProvinceData(Terrain.PLAINS, Set.of(9)),
                9, new ProvinceData(Terrain.SEA, Set.of(1, 2)),
                2, new ProvinceData(Terrain.PLAINS, Set.of(9)));
        return TradeGraphBuilder.build(
                List.of(new Site("home", a, true), new Site("home", b, true)),
                provinces,
                (left, right) -> Optional.of(new RailRoutes.Route(0, List.of())),
                point -> 0);
    }

    private static TradeGraph railRoutes(
            List<Installation> installations, Map<String, List<Integer>> routes) {
        Map<Integer, ProvinceData> data = new HashMap<>();
        for (Installation installation : installations) {
            data.put(installation.getProvince(), new ProvinceData(Terrain.PLAINS, Set.of()));
        }
        for (List<Integer> route : routes.values()) {
            for (int provinceId : route) {
                data.putIfAbsent(provinceId, new ProvinceData(Terrain.PLAINS, Set.of()));
            }
        }
        List<Site> sites = installations.stream()
                .map(installation -> new Site("home", installation, true))
                .toList();
        return TradeGraphBuilder.build(sites, data, (from, to) -> {
            List<Integer> route = routes.get(from.getId() + "-" + to.getId());
            if (route == null) {
                return Optional.empty();
            }
            return Optional.of(new RailRoutes.Route(1000,
                    route.stream().map(id -> new Point(id, 0, 0)).toList()));
        }, point -> (int) point.x());
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
        ProvinceManager provinces = isolated(1, 2);
        provinces.get(1).addNeighbour(2);
        provinces.get(2).addNeighbour(1);
        return provinces;
    }

    private void placeTrade(ProvinceManager provinces, int id, double trade) {
        provinces.get(id).setData(guild.getId(), new ProvinceDataEntry(guild, trade, 0));
    }

    private void placeProduction(ProvinceManager provinces, int id, double amount) {
        ProvinceDataEntry entry = provinces.get(id).getAllData().get(guild.getId());
        if (entry == null) {
            entry = new ProvinceDataEntry(guild);
            provinces.get(id).setData(guild.getId(), entry);
        }
        entry.setProduction(amount);
    }

    private double raw(ProvinceManager provinces, int id) {
        return provinces.get(id).getRawGuildTrade(guild);
    }

    private double production(ProvinceManager provinces, int id) {
        return provinces.get(id).getGuildProduction(guild);
    }

    private Map<Integer, Double> rawAt(ProvinceManager provinces, int... ids) {
        Map<Integer, Double> values = new HashMap<>();
        for (int id : ids) {
            values.put(id, raw(provinces, id));
        }
        return values;
    }

    private static Installation station(String id, int province) {
        return new Installation(id, id, InstallationKind.TRAIN_STATION, province, 0, 0, 1L);
    }
}
