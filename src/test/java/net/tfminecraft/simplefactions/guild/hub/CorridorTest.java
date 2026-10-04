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
    void depositUsesTheLowerAccessAtTheTwoEnds() {
        TradeGraph graph = rail("left", "right", 0, 10);
        ProvinceManager provinces = map(1, 2, 10);
        place(provinces, 1, 100);

        Highway.deposit(provinces, guild, graph, Map.of("left", 0.8, "right", 0.25));

        assertEquals(5, raw(provinces, 10), 1e-9);
    }

    @Test
    void zeroAccessBlocksCorridorDeposits() {
        TradeGraph graph = rail("left", "right", 0, 10);
        ProvinceManager provinces = map(1, 2, 10);
        place(provinces, 1, 100);

        Highway.deposit(provinces, guild, graph, Map.of("left", 1.0, "right", 0.0));

        assertEquals(0, raw(provinces, 10));
    }

    @Test
    void depositsFadeFromEachSender() {
        TradeGraph graph = rail("owner", "owner", 3000, 10, 11);
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.transport.rail.kept-per-1000-blocks", 0.90);
        HubTransport.loadConfig(config);
        ProvinceManager provinces = map(1, 2, 10, 11);
        place(provinces, 1, 100);

        Highway.deposit(provinces, guild, graph, Map.of("owner", 1.0));

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

        Highway.deposit(provinces, guild, graph, Map.of("owner", 1.0));

        assertFalse(provinces.contains(404));
        assertEquals(20 * Math.pow(0.5, 0.25), raw(provinces, 10), 1e-9);
        assertEquals(20 * Math.pow(0.5, 0.75), raw(provinces, 11), 1e-9);
    }

    @Test
    void airHasNoCorridorDeposit() {
        Installation first = new Installation("a", "a", InstallationKind.AIRPORT, 1, 0, 0, 1L);
        Installation second = new Installation("b", "b", InstallationKind.AIRPORT, 2, 1000, 0, 1L);
        TradeGraph graph = TradeGraphBuilder.build(
                List.of(new Site("owner", first, 1, true), new Site("owner", second, 1, true)),
                Map.of(),
                (left, right) -> Optional.empty(),
                point -> 0);
        ProvinceManager provinces = map(1, 2, 10);
        place(provinces, 1, 100);

        Highway.deposit(provinces, guild, graph, Map.of("owner", 1.0));

        assertEquals(0, raw(provinces, 10));
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
                List.of(new Site(firstOwner, first, 1, true), new Site(secondOwner, second, 1, true)),
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

    private double raw(ProvinceManager provinces, int id) {
        return provinces.get(id).getRawGuildTrade(guild);
    }

    private static Installation station(String id, int province) {
        return new Installation(id, id, InstallationKind.TRAIN_STATION, province, 0, 0, 1L);
    }
}
