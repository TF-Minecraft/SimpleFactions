package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.network.RailRoutes;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.ProvinceData;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.Site;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceLookup.Point;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;

class CorridorTest {
    private Guild guild;
    private double previousCorridor;
    private Double previousPlains;

    @BeforeEach
    void setUp() {
        previousCorridor = Cache.supplyHubCorridorShare;
        Cache.supplyHubCorridorShare = 0.5;
        previousPlains = Cache.tradeCarry.get(Terrain.PLAINS);
        Cache.tradeCarry.put(Terrain.PLAINS, 0.85);
        guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
    }

    @AfterEach
    void tearDown() {
        HubTransport.resetConfig();
        Cache.supplyHubCorridorShare = previousCorridor;
        if (previousPlains == null) {
            Cache.tradeCarry.remove(Terrain.PLAINS);
        } else {
            Cache.tradeCarry.put(Terrain.PLAINS, previousPlains);
        }
    }

    @Test
    void lineUsesTheLowerAccessAtTheTwoEnds() {
        TradeGraph graph = rail("left", "right", 0, 10);
        ProvinceManager provinces = map(1, 2, 10);
        place(provinces, 1, 100);

        Highway.deliver(provinces, guild, graph, Map.of("left", 0.8, "right", 0.25));

        assertEquals(5, raw(provinces, 10), 1e-9);
    }

    @Test
    void zeroAccessAtEitherEndBlocksTheWholeLine() {
        TradeGraph graph = rail("left", "right", 0, 10);
        ProvinceManager provinces = map(1, 2, 10);
        place(provinces, 1, 100);

        Highway.deliver(provinces, guild, graph, Map.of("left", 1.0, "right", 0.0));

        assertEquals(0, raw(provinces, 10));
        assertEquals(0, raw(provinces, 2));
    }

    @Test
    void corridorStopsFadeFromEachSender() {
        TradeGraph graph = rail("owner", "owner", 3000, 10, 11);
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.kept-per-1000-blocks", 0.90);
        HubTransport.loadConfig(config);
        ProvinceManager provinces = map(1, 2, 10, 11);
        place(provinces, 1, 100);

        Highway.deliver(provinces, guild, graph, Map.of("owner", 1.0));

        assertEquals(18, raw(provinces, 10), 1e-9);
        assertEquals(16.2, raw(provinces, 11), 1e-9);
    }

    @Test
    void missingProvinceKeepsItsPlaceInTheDistance() {
        TradeGraph graph = rail("owner", "owner", 1000, 10, 404, 11);
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.kept-per-1000-blocks", 0.50);
        HubTransport.loadConfig(config);
        ProvinceManager provinces = map(1, 2, 10, 11);
        place(provinces, 1, 100);

        Highway.deliver(provinces, guild, graph, Map.of("owner", 1.0));

        assertFalse(provinces.contains(404));
        assertEquals(20 * Math.pow(0.5, 0.25), raw(provinces, 10), 1e-9);
        assertEquals(20 * Math.pow(0.5, 0.75), raw(provinces, 11), 1e-9);
    }

    @Test
    void airHasNoCorridorStops() {
        Installation first = new Installation("a", "a", InstallationKind.AIRPORT, 1, 0, 0, 1L);
        Installation second = new Installation("b", "b", InstallationKind.AIRPORT, 2, 1000, 0, 1L);
        TradeGraph graph = TradeGraphBuilder.build(
                List.of(new Site("owner", first, true), new Site("owner", second, true)),
                Map.of(),
                (left, right) -> Optional.empty(),
                point -> 0);
        ProvinceManager provinces = map(1, 2, 10);
        place(provinces, 1, 100);

        Highway.deliver(provinces, guild, graph, Map.of("owner", 1.0));

        assertEquals(0, raw(provinces, 10));
    }

    @Test
    void boardingAtAMidLineStopReachesBothInstallations() {
        TradeGraph graph = rail("owner", "owner", 1000, 10);
        ProvinceManager provinces = map(1, 2, 10);
        place(provinces, 10, 100);

        Highway.deliver(provinces, guild, graph, Map.of("owner", 1.0));

        double expected = 100 * 0.5 * 0.4 * Math.pow(0.9, 0.5);
        assertEquals(expected, raw(provinces, 1), 1e-9);
        assertEquals(expected, raw(provinces, 2), 1e-9);
    }

    @Test
    void corridorArrivalHasItsStopStrengthApplied() {
        double[] delivered = Highway.lineDeliveries(
                new double[] {0, 500, 500},
                new double[] {100, 0, 0},
                new double[] {1, 1, 0.5},
                0.4, 0.9, 1);

        assertEquals(delivered[1] * 0.5, delivered[2], 1e-9);
    }

    @Test
    void tradeMovesFromCorridorToCorridor() {
        TradeGraph graph = rail("owner", "owner", 1000, 10, 11);
        ProvinceManager provinces = map(1, 2, 10, 11);
        place(provinces, 10, 100);

        Highway.deliver(provinces, guild, graph, Map.of("owner", 1.0));

        assertEquals(100 * 0.5 * 0.4 * Math.pow(0.9, 1.0 / 3.0) * 0.5,
                raw(provinces, 11), 1e-9);
    }

    @Test
    void productionUsesTheSameLineStops() {
        TradeGraph graph = rail("owner", "owner", 1000, 10);
        ProvinceManager provinces = map(1, 2, 10);
        placeProduction(provinces, 10, 100);

        Highway.deliverProduction(provinces, guild, graph, Map.of("owner", 1.0));

        double expected = 100 * 0.5 * 0.8 * Math.pow(0.9, 0.5);
        assertEquals(expected, production(provinces, 1), 1e-9);
        assertEquals(expected, production(provinces, 2), 1e-9);
    }

    @Test
    void sweepsEqualBruteForceAllPairs() {
        Random random = new Random(4829384L);
        for (int trial = 0; trial < 500; trial++) {
            int size = 2 + random.nextInt(30);
            double[] positions = new double[size];
            double[] raw = new double[size];
            double[] stops = new double[size];
            for (int index = 0; index < size; index++) {
                positions[index] = index == 0
                        ? 0 : positions[index - 1] + random.nextDouble(1000);
                raw[index] = random.nextDouble(200);
                stops[index] = index == 0 || index == size - 1
                        ? 1 : random.nextDouble();
            }
            double share = random.nextDouble(0.95);
            double kept = random.nextDouble(0.95);
            double access = random.nextDouble();

            double[] swept = Highway.lineDeliveries(
                    positions, raw, stops, share, kept, access);

            for (int to = 0; to < size; to++) {
                double brute = 0;
                for (int from = 0; from < size; from++) {
                    double amount = raw[from] * stops[from] * share
                            * Math.pow(kept, Math.abs(positions[to] - positions[from]) / 1000.0)
                            * stops[to] * access;
                    brute = Math.max(brute, amount);
                }
                assertEquals(brute, swept[to], 1e-9, "trial " + trial + ", stop " + to);
            }
        }
    }

    private static TradeGraph rail(
            String firstOwner, String secondOwner, double length, int... corridor) {
        Installation first = station("a", 1);
        Installation second = station("b", 2);
        Map<Integer, ProvinceData> data = new HashMap<>();
        data.put(1, new ProvinceData(Terrain.PLAINS, Set.of()));
        data.put(2, new ProvinceData(Terrain.PLAINS, Set.of()));
        List<Point> points = new ArrayList<>();
        points.add(new Point(1, 0, 0));
        for (int id : corridor) {
            data.put(id, new ProvinceData(Terrain.PLAINS, Set.of()));
            points.add(new Point(id, 0, 0));
        }
        points.add(new Point(2, 0, 0));
        return TradeGraphBuilder.build(
                List.of(new Site(firstOwner, first, true), new Site(secondOwner, second, true)),
                data,
                (left, right) -> Optional.of(new RailRoutes.Route(length, points)),
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

    private void placeProduction(ProvinceManager provinces, int id, double amount) {
        provinces.get(id).setData(guild.getId(), new ProvinceDataEntry(guild, 0, amount));
    }

    private double raw(ProvinceManager provinces, int id) {
        return provinces.get(id).getRawGuildTrade(guild);
    }

    private double production(ProvinceManager provinces, int id) {
        return provinces.get(id).getGuildProduction(guild);
    }

    private static Installation station(String id, int province) {
        return new Installation(id, id, InstallationKind.TRAIN_STATION, province, 0, 0, 1L);
    }
}
