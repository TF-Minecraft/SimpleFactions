package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.network.InstallationAccess;
import net.tfminecraft.simplefactions.guild.network.RailRoutes;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.ProvinceData;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.Site;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceLookup.Point;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Faction;

class RailTradeBalanceTest {
    private Guild guild;
    private Faction guildFaction;
    private double previousCorridor;
    private Double previousPlains;

    @BeforeEach
    void setUp() {
        previousCorridor = Cache.supplyHubCorridorShare;
        Cache.supplyHubCorridorShare = 0.5;
        previousPlains = Cache.tradeCarry.get(Terrain.PLAINS);
        Cache.tradeCarry.put(Terrain.PLAINS, 0.85);
        HubTransport.resetConfig();
        OpenTrackSettings.reset();
        guildFaction = mock(Faction.class);
        when(guildFaction.getId()).thenReturn("guild-realm");
        when(guildFaction.getOverlord()).thenReturn(null);
        guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
        when(guild.getFaction()).thenReturn(guildFaction);
    }

    @AfterEach
    void tearDown() {
        HubTransport.resetConfig();
        OpenTrackSettings.reset();
        Cache.supplyHubCorridorShare = previousCorridor;
        if (previousPlains == null) {
            Cache.tradeCarry.remove(Terrain.PLAINS);
        } else {
            Cache.tradeCarry.put(Terrain.PLAINS, previousPlains);
        }
    }

    @Test
    void stationBoardingDoesNotWriteTheNeighbourBackOntoTheStation() {
        TradeGraph graph = rail(0, "owner", "owner");
        ProvinceManager provinces = map(Terrain.PLAINS, 1, 2, 3);
        provinces.get(1).addNeighbour(3);
        place(provinces, 1, 10);
        place(provinces, 3, 100);

        Faction owner = realm("owner");
        try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
                MockedStatic<InstallationAccess> access = mockStatic(InstallationAccess.class, CALLS_REAL_METHODS)) {
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenReturn(owner);
            Highway.deliver(provinces, guild, graph, Map.of("owner", 1.0));
            assertEquals(10, raw(provinces, 1), 1e-9);
            assertEquals(100, raw(provinces, 3), 1e-9);
            assertEquals(40, raw(provinces, 2), 1e-9);

            Highway.deliver(provinces, guild, graph, Map.of("owner", 1.0));
            assertEquals(10, raw(provinces, 1), 1e-9);
            assertEquals(40, raw(provinces, 2), 1e-9);
        }
    }

    @Test
    void neighbourBoostReachesTheNextStationWithoutRaisingTheSource() {
        Installation first = new Installation("a", "a", InstallationKind.TRAIN_STATION, 1, 0, 0, 1L);
        Installation middle = new Installation("b", "b", InstallationKind.TRAIN_STATION, 2, 0, 0, 1L);
        Installation last = new Installation("c", "c", InstallationKind.TRAIN_STATION, 3, 0, 0, 1L);
        TradeGraph graph = TradeGraphBuilder.build(
                List.of(new Site("owner", first, true), new Site("owner", middle, true),
                        new Site("owner", last, true)),
                Map.of(
                        1, new ProvinceData(Terrain.PLAINS, java.util.Set.of()),
                        2, new ProvinceData(Terrain.PLAINS, java.util.Set.of()),
                        3, new ProvinceData(Terrain.PLAINS, java.util.Set.of())),
                (from, to) -> {
                    int left = from.getProvince();
                    int right = to.getProvince();
                    if (Math.abs(left - right) == 1) {
                        return Optional.of(new RailRoutes.Route(0, List.of()));
                    }
                    return Optional.empty();
                },
                point -> 0);
        ProvinceManager provinces = map(Terrain.PLAINS, 1, 2, 3, 4);
        provinces.get(1).addNeighbour(4);
        place(provinces, 1, 10);
        place(provinces, 4, 100);
        Faction owner = realm("owner");
        try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
                MockedStatic<InstallationAccess> access = mockStatic(InstallationAccess.class, CALLS_REAL_METHODS)) {
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenReturn(owner);
            Highway.deliver(provinces, guild, graph, Map.of("owner", 1.0));
        }
        assertEquals(10, raw(provinces, 1), 1e-9);
        assertEquals(40, raw(provinces, 2), 1e-9);
        assertEquals(16, raw(provinces, 3), 1e-9);
        assertEquals(100, raw(provinces, 4), 1e-9);
    }

    @Test
    void incomingTradeStillArrivesAtAStationThatBoardsANeighbour() {
        TradeGraph graph = rail(0, "owner", "owner");
        ProvinceManager provinces = map(Terrain.PLAINS, 1, 2, 3);
        provinces.get(1).addNeighbour(3);
        place(provinces, 1, 10);
        place(provinces, 2, 1000);
        place(provinces, 3, 100);

        Faction owner = realm("owner");
        try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
                MockedStatic<InstallationAccess> access = mockStatic(InstallationAccess.class, CALLS_REAL_METHODS)) {
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenReturn(owner);
            Highway.deliver(provinces, guild, graph, Map.of("owner", 1.0));
        }

        assertEquals(400, raw(provinces, 1), 1e-9);
        assertEquals(1000, raw(provinces, 2), 1e-9);
    }

    @Test
    void waterAndForeignNeighboursDoNotBoard() {
        TradeGraph graph = rail(0, "owner", "owner");
        ProvinceManager provinces = withWater(1, 2, 3, 4);
        place(provinces, 1, 10);
        place(provinces, 3, 80);
        place(provinces, 4, 500);
        place(provinces, 6, 900);

        Faction owner = realm("owner");
        Faction other = realm("other");
        try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
                MockedStatic<InstallationAccess> access = mockStatic(InstallationAccess.class, CALLS_REAL_METHODS)) {
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenAnswer(invocation -> {
                int id = invocation.getArgument(0);
                if (id == 4) return other;
                if (id == 1 || id == 3) return owner;
                return null;
            });
            Highway.deliver(provinces, guild, graph, Map.of("owner", 1.0));
        }

        assertEquals(32, raw(provinces, 2), 1e-9);
        assertEquals(10, raw(provinces, 1), 1e-9);
        assertEquals(900, raw(provinces, 6), 1e-9);
        assertEquals(500, raw(provinces, 4), 1e-9);
    }

    @Test
    void productionBoardingIgnoresNeighboursAndAirCarriesNone() {
        TradeGraph graph = rail(0, "owner", "owner");
        ProvinceManager provinces = map(Terrain.PLAINS, 1, 2, 3);
        provinces.get(1).addNeighbour(3);
        placeProduction(provinces, 1, 10);
        placeProduction(provinces, 3, 100);
        Faction owner = realm("owner");
        try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
                MockedStatic<InstallationAccess> access = mockStatic(InstallationAccess.class, CALLS_REAL_METHODS)) {
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenReturn(owner);
            Highway.deliverProduction(provinces, guild, graph, Map.of("owner", 1.0));
        }
        assertEquals(4, production(provinces, 2), 1e-9);
        assertEquals(10, production(provinces, 1), 1e-9);

        Installation first = new Installation("a", "a", InstallationKind.AIRPORT, 1, 0, 0, 1L);
        Installation second = new Installation("b", "b", InstallationKind.AIRPORT, 2, 0, 0, 1L);
        TradeGraph air = TradeGraphBuilder.build(
                List.of(new Site("owner", first, true), new Site("owner", second, true)),
                Map.of(), (left, right) -> Optional.empty(), point -> 0);
        ProvinceManager airports = map(Terrain.PLAINS, 1, 2);
        placeProduction(airports, 1, 100);
        Highway.deliverProduction(airports, guild, air, Map.of("owner", 1.0));
        assertEquals(0, production(airports, 2), 1e-9);
        assertEquals(100, production(airports, 1), 1e-9);
    }

    @Test
    void corridorStrengthScalesWithOwnerAccessAndZeroBlocksTheStop() {
        TradeGraph graph = rail(1000, "owner", "owner", 10);
        ProvinceManager provinces = map(Terrain.PLAINS, 1, 2, 10, 11);
        place(provinces, 1, 100);
        Faction foreign = realm("foreign");
        try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
                MockedStatic<InstallationAccess> access = mockStatic(InstallationAccess.class)) {
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenAnswer(invocation -> {
                int id = invocation.getArgument(0);
                return id == 10 || id == 11 ? foreign : null;
            });
            access.when(() -> InstallationAccess.of(any(), any())).thenReturn(0.5);
            Highway.deliver(provinces, guild, graph, Map.of("owner", 1.0));
            access.verify(() -> InstallationAccess.of(guildFaction, foreign), times(1));
        }
        assertEquals(100 * 0.4 * Math.pow(0.9, 0.5) * 0.25, raw(provinces, 10), 1e-9);
        assertEquals(100 * 0.4 * Math.pow(0.9, 1), raw(provinces, 2), 1e-9);

        ProvinceManager blocked = map(Terrain.PLAINS, 1, 2, 10);
        place(blocked, 10, 100);
        try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
                MockedStatic<InstallationAccess> access = mockStatic(InstallationAccess.class)) {
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenAnswer(invocation -> {
                int id = invocation.getArgument(0);
                return id == 10 ? foreign : null;
            });
            access.when(() -> InstallationAccess.of(any(), any())).thenReturn(0.0);
            Highway.deliver(blocked, guild, graph, Map.of("owner", 1.0));
        }
        assertEquals(100, raw(blocked, 10), 1e-9);
        assertEquals(0, raw(blocked, 1), 1e-9);
        assertEquals(0, raw(blocked, 2), 1e-9);
    }

    @Test
    void productionCorridorUsesTheSameScaledStrength() {
        TradeGraph graph = rail(1000, "owner", "owner", 10);
        ProvinceManager provinces = map(Terrain.PLAINS, 1, 2, 10);
        placeProduction(provinces, 1, 100);
        Faction foreign = realm("foreign");
        try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
                MockedStatic<InstallationAccess> access = mockStatic(InstallationAccess.class)) {
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenAnswer(invocation ->
                    invocation.getArgument(0).equals(10) ? foreign : null);
            access.when(() -> InstallationAccess.of(any(), any())).thenReturn(0.5);
            Highway.deliverProduction(provinces, guild, graph, Map.of("owner", 1.0));
        }
        assertEquals(100 * 0.4 * Math.pow(0.9, 0.5) * 0.25, production(provinces, 10), 1e-9);
    }

    @Test
    void boostedStationBoardingDoesNotCountAsAnArrivalAtThatStop() {
        double[] delivered = Highway.lineDeliveries(
                new double[] {0, 0},
                new double[] {100, 0},
                new double[] {10, 0},
                new double[] {1, 1},
                0.4, 1, 1);
        assertEquals(4, delivered[0], 1e-9);
        assertEquals(40, delivered[1], 1e-9);
    }

    @Test
    void loneStationPushesTradeAndDoesNotStackWithALine() {
        Installation station = new Installation("drammen", "Drammen", InstallationKind.TRAIN_STATION, 1, 0, 0, 1L);
        TradeGraph alone = TradeGraphBuilder.build(
                List.of(new Site("owner", station, true)),
                Map.of(1, new ProvinceData(Terrain.PLAINS, java.util.Set.of())),
                (left, right) -> Optional.empty(),
                point -> 0).withOpenTracks(List.of(
                        new TradeGraph.OpenTrack("owner", "drammen", Map.of(4, 1000.0))));
        ProvinceManager provinces = map(Terrain.PLAINS, 1, 4);
        place(provinces, 1, 100);
        Highway.deliver(provinces, guild, alone, Map.of("owner", 1.0));
        assertEquals(25.5, raw(provinces, 4), 1e-9);
        assertEquals(100, raw(provinces, 1), 1e-9);

        TradeGraph line = rail(1000, "owner", "owner", 5).withOpenTracks(List.of(
                new TradeGraph.OpenTrack("owner", "a", Map.of(5, 0.0))));
        ProvinceManager shared = map(Terrain.PLAINS, 1, 2, 5);
        place(shared, 1, 10);
        Highway.deliver(shared, guild, line, Map.of("owner", 1.0));
        double fromLine = 10 * 0.4 * Math.pow(0.9, 0.5) * 0.5;
        double fromPush = 10 * Math.min(0.95, 0.40 * 0.75);
        assertEquals(Math.max(fromLine, fromPush), raw(shared, 5), 1e-9);
        assertTrue(raw(shared, 5) < fromLine + fromPush);
    }

    @Test
    void openTrackUsesNeighbourInputOwnerAccessAndSkipsZero() {
        Installation station = new Installation("drammen", "Drammen", InstallationKind.TRAIN_STATION, 1, 0, 0, 1L);
        TradeGraph graph = TradeGraphBuilder.build(
                List.of(new Site("owner", station, true)),
                Map.of(1, new ProvinceData(Terrain.PLAINS, java.util.Set.of(3))),
                (left, right) -> Optional.empty(),
                point -> 0).withOpenTracks(List.of(
                        new TradeGraph.OpenTrack("owner", "drammen", Map.of(4, 1000.0, 8, 0.0))));
        ProvinceManager provinces = map(Terrain.PLAINS, 1, 3, 4, 8);
        provinces.get(1).addNeighbour(3);
        place(provinces, 1, 10);
        place(provinces, 3, 200);
        Faction owner = realm("owner");
        Faction closed = realm("closed");
        try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
                MockedStatic<InstallationAccess> access = mockStatic(InstallationAccess.class, CALLS_REAL_METHODS)) {
            titles.when(() -> TitleManager.getByProvince(anyInt())).thenAnswer(invocation -> {
                int id = invocation.getArgument(0);
                if (id == 8) return closed;
                if (id == 1 || id == 3) return owner;
                return null;
            });
            access.when(() -> InstallationAccess.of(any(), any())).thenAnswer(invocation -> {
                Faction land = invocation.getArgument(1);
                return land == closed ? 0.0 : 1.0;
            });
            Highway.deliver(provinces, guild, graph, Map.of("owner", 0.5));
        }
        assertEquals(200 * 0.30 * 0.5 * 0.85, raw(provinces, 4), 1e-9);
        assertEquals(0, raw(provinces, 8), 1e-9);
        assertEquals(10, raw(provinces, 1), 1e-9);
        assertEquals(200, raw(provinces, 3), 1e-9);
    }

    @Test
    void disabledOpenTrackPushesNothing() {
        Installation station = new Installation("drammen", "Drammen", InstallationKind.TRAIN_STATION, 1, 0, 0, 1L);
        TradeGraph graph = TradeGraphBuilder.build(
                List.of(new Site("owner", station, true)),
                Map.of(),
                (left, right) -> Optional.empty(),
                point -> 0).withOpenTracks(List.of(
                        new TradeGraph.OpenTrack("owner", "drammen", Map.of(4, 0.0))));
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("installation-trade.open-track.enabled", false);
        OpenTrackSettings.load(config);
        ProvinceManager provinces = map(Terrain.PLAINS, 1, 4);
        place(provinces, 1, 100);
        Highway.deliver(provinces, guild, graph, Map.of("owner", 1.0));
        assertEquals(0, raw(provinces, 4), 1e-9);
    }

    private Faction realm(String id) {
        Faction faction = mock(Faction.class);
        when(faction.getId()).thenReturn(id);
        when(faction.getOverlord()).thenReturn(null);
        return faction;
    }

    private static TradeGraph rail(double length, String firstOwner, String secondOwner, int... corridor) {
        Installation first = new Installation("a", "a", InstallationKind.TRAIN_STATION, 1, 0, 0, 1L);
        Installation second = new Installation("b", "b", InstallationKind.TRAIN_STATION, 2, 1000, 0, 1L);
        Map<Integer, ProvinceData> data = new HashMap<>();
        data.put(1, new ProvinceData(Terrain.PLAINS, java.util.Set.of()));
        data.put(2, new ProvinceData(Terrain.PLAINS, java.util.Set.of()));
        List<Point> points = new java.util.ArrayList<>();
        points.add(new Point(1, 0, 0));
        for (int id : corridor) {
            data.put(id, new ProvinceData(Terrain.PLAINS, java.util.Set.of()));
            points.add(new Point(id, 0, 0));
        }
        points.add(new Point(2, 0, 0));
        return TradeGraphBuilder.build(
                List.of(new Site(firstOwner, first, true), new Site(secondOwner, second, true)),
                data,
                (left, right) -> Optional.of(new RailRoutes.Route(length, points)),
                point -> (int) point.x());
    }

    private ProvinceManager map(Terrain terrain, int... ids) {
        Map<Integer, Province> provinces = new HashMap<>();
        for (int id : ids) {
            provinces.put(id, new Province(id, terrain.name(), 50));
        }
        ProvinceManager manager = new ProvinceManager();
        manager.start(provinces);
        return manager;
    }

    private ProvinceManager withWater(int... land) {
        Map<Integer, Province> provinces = new HashMap<>();
        for (int id : land) {
            provinces.put(id, new Province(id, Terrain.PLAINS.name(), 50));
        }
        provinces.put(6, new Province(6, Terrain.WATER.name(), 50));
        ProvinceManager manager = new ProvinceManager();
        manager.start(provinces);
        manager.get(1).addNeighbour(3);
        manager.get(1).addNeighbour(4);
        manager.get(1).addNeighbour(6);
        return manager;
    }

    private void place(ProvinceManager provinces, int id, double trade) {
        provinces.get(id).setData(guild.getId(), new ProvinceDataEntry(guild, trade, 0));
    }

    private void placeProduction(ProvinceManager provinces, int id, double amount) {
        provinces.get(id).setData(guild.getId(), new ProvinceDataEntry(guild, 0, amount));
    }

    private double raw(ProvinceManager provinces, int id) {
        return provinces.get(id).getRawGuildTrade(guild);
    }

    private double production(ProvinceManager provinces, int id) {
        return provinces.get(id).getGuildProduction(guild);
    }
}
