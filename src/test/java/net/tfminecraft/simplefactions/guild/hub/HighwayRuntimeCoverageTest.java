package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import net.tfminecraft.simplefactions.Cache;
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
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.tracks.TrackJunction;
import net.tfminecraft.vehicleframework.tracks.TrackRegistry;
import net.tfminecraft.vehicleframework.tracks.TrackSample;
import net.tfminecraft.vehicleframework.tracks.TrackSegment;
import net.tfminecraft.vehicleframework.tracks.TrackSpline;
import net.tfminecraft.vehicleframework.tracks.TrackStore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class HighwayRuntimeCoverageTest {
  @TempDir Path temporary;
  private FactionDomainFixture fixture;
  private MockedStatic<VehicleFramework> vehicles;
  private final Map<Field, Object> saved = new LinkedHashMap<>();
  private final List<TrackRegistry> registries = new ArrayList<>();
  private Map<Object, Object> installationKinds;

  @BeforeEach
  void setUp() throws Exception {
    fixture = new FactionDomainFixture();
    vehicles = mockStatic(VehicleFramework.class);
    remember(HubTransport.class, "rates");
    for (String field : List.of("enabled", "share", "keptPer1000", "rangeBlocks"))
      remember(OpenTrackSettings.class, field);
    remember(Cache.class, "supplyHubCorridorShare");
    remember(InstallationConfigLoader.class, "consentProximityBlocks");
    remember(InstallationConfigLoader.class, "transferRequestTimeoutSeconds");
    installationKinds = new LinkedHashMap<>(kindMap());
    HubTransport.resetConfig();
    OpenTrackSettings.reset();
    when(Bukkit.getPluginManager().isPluginEnabled("VehicleFramework")).thenReturn(true);
    configureInstallations();
  }

  @AfterEach
  void close() throws Exception {
    for (TrackRegistry registry : registries) registry.close();
    kindMap().clear();
    kindMap().putAll(installationKinds);
    for (var entry : saved.entrySet()) entry.getKey().set(null, entry.getValue());
    vehicles.close();
    fixture.close();
  }

  @Test
  void stationCanBoardTheMiddleOfAnUnbrokenCoarseTrackSegment() {
    TrackSpline track = spline(0, 200);
    Map<Integer, Double> reached =
        VehicleFrameworkTracks.reach(
            List.of(track), List.of(), 100, 0, 10, 60, (x, z) -> x < 80 ? 2 : x > 120 ? 3 : 1, 1);

    assertTrue(
        reached.containsKey(2), "The station touches the segment towards the western province");
    assertTrue(
        reached.containsKey(3), "The same station can travel east along the unbroken segment");
    assertTrue(reached.values().stream().allMatch(distance -> distance > 0 && distance <= 60));
    assertFalse(reached.containsKey(1), "The home province is not an open-track destination");
  }

  @Test
  void midpointStationCanReachANearbyJunctionWithoutDetouringThroughRemoteEndpoints() {
    TrackSpline stem = spline(0, 200);
    TrackSpline branch =
        new TrackSpline(
            UUID.randomUUID(),
            "world",
            false,
            List.of(new TrackSample(120, 0, 0, 0, 0, 0), new TrackSample(120, 0, 40, 0, 0, 40)),
            null);
    TrackJunction junction =
        new TrackJunction(
            UUID.randomUUID(), stem.getId(), 120, 0, TrackJunction.Side.LEFT, branch.getId(), true);
    Map<Integer, Double> reached =
        VehicleFrameworkTracks.reach(
            List.of(stem, branch), List.of(junction), 100, 0, 5, 50, (x, z) -> z >= 20 ? 2 : 1, 1);
    assertTrue(
        reached.containsKey(2),
        "The junction is 20 blocks east and the destination 20 blocks up its branch");
    assertTrue(reached.get(2) >= 30 && reached.get(2) <= 50);
    assertFalse(reached.containsKey(1));
  }

  @Test
  void aStationTouchingOnlyABrokenSegmentCannotBoardOrReachItsJunction() {
    TrackSpline intact = spline(0, 200);
    TrackSpline broken =
        new TrackSpline(
            intact.getId(),
            "world",
            false,
            intact.getSamples(),
            List.of(new TrackSegment(0, true, 1)));
    TrackSpline branch =
        new TrackSpline(
            UUID.randomUUID(),
            "world",
            false,
            List.of(new TrackSample(120, 0, 0, 0, 0, 0), new TrackSample(120, 0, 40, 0, 0, 40)),
            null);
    TrackJunction junction =
        new TrackJunction(
            UUID.randomUUID(),
            broken.getId(),
            120,
            0,
            TrackJunction.Side.LEFT,
            branch.getId(),
            true);
    assertTrue(
        VehicleFrameworkTracks.reach(
                List.of(broken, branch),
                List.of(junction),
                100,
                0,
                5,
                100,
                (x, z) -> z >= 20 || x < 80 || x > 140 ? 2 : 1,
                1)
            .isEmpty());
  }

  @Test
  void continuousBoardingMeasuresDistanceFromTheCircleEdgeAndHonorsTangency() {
    TrackSpline line = spline(0, 200);
    TrackReach.At endpoints = (x, z) -> x == 0 ? 2 : x == 200 ? 3 : 1;
    assertDistances(
        Map.of(2, 90.0, 3, 90.0),
        VehicleFrameworkTracks.reach(List.of(line), List.of(), 100, 0, 10, 100, endpoints, 1));
    assertDistances(
        Map.of(2, 100.0, 3, 100.0),
        VehicleFrameworkTracks.reach(List.of(line), List.of(), 100, 0, 0, 100, endpoints, 1));
    assertDistances(
        Map.of(2, 100.0, 3, 100.0),
        VehicleFrameworkTracks.reach(List.of(line), List.of(), 100, 10, 10, 100, endpoints, 1));
    assertTrue(
        VehicleFrameworkTracks.reach(List.of(line), List.of(), 100, 11, 10, 200, endpoints, 1)
            .isEmpty());
    assertTrue(
        VehicleFrameworkTracks.reach(List.of(line), List.of(), 220, 0, 10, 200, endpoints, 1)
            .isEmpty());
  }

  @Test
  void aLoopUsesItsShortClosingEdgeAndZeroLengthSamplesDoNotCreateExtraDistance() {
    TrackSpline loop =
        TrackSpline.fromPoints(
            UUID.randomUUID(),
            "world",
            true,
            List.of(
                new double[] {0, 0, 0}, new double[] {100, 0, 0},
                new double[] {100, 0, 100}, new double[] {0, 0, 100}));
    assertEquals(
        Map.of(2, 100.0),
        VehicleFrameworkTracks.reach(
            List.of(loop), List.of(), 0, 0, 0, 100, (x, z) -> x == 0 && z == 100 ? 2 : 1, 1));
    TrackSpline repeated =
        TrackSpline.fromPoints(
            UUID.randomUUID(),
            "world",
            false,
            List.of(new double[] {0, 0, 0}, new double[] {0, 0, 0}, new double[] {80, 0, 0}));
    assertEquals(
        Map.of(2, 80.0),
        VehicleFrameworkTracks.reach(
            List.of(repeated), List.of(), 0, 0, 0, 80, (x, z) -> x == 80 ? 2 : 1, 1));
    assertTrue(
        VehicleFrameworkTracks.reach(
                List.of(repeated), List.of(), -10, 0, 1, 80, (x, z) -> x == 80 ? 2 : 1, 1)
            .isEmpty());
  }

  @Test
  void realRegistryRoutesAndSamplesOnlyKnownLandInTheSelectedWorld() throws Exception {
    TrackRegistry registry = registry(spline(0, 100, 200));
    Installation west = station("west", 1, 0);
    Installation east = station("east", 3, 200);
    double length = VehicleFrameworkTracks.distance("world", west, east).orElseThrow();
    RailRoutes.Route route = VehicleFrameworkTracks.route("world", west, east).orElseThrow();
    assertEquals(length, route.length(), 1e-9);
    assertTrue(length > 190 && length <= 200);
    assertFalse(route.points().isEmpty());
    assertTrue(route.points().stream().allMatch(point -> point.y() == 0 && point.z() == 0));
    assertTrue(route.points().getFirst().x() < route.points().getLast().x());
    ProvinceGrid grid = grid();
    Map<Integer, Province> provinces =
        Map.of(
            1, new Province(1, "PLAINS", 50),
            2, new Province(2, "SEA", 0),
            3, new Province(3, "PLAINS", 50));
    assertEquals(Set.of(1, 3), VehicleFrameworkTrackProvinces.sample("world", grid, provinces));
    assertEquals(Set.of(), VehicleFrameworkTracks.sample("elsewhere", grid, provinces));
    assertEquals(Set.of(), VehicleFrameworkTracks.sample(null, grid, provinces));
    assertTrue(VehicleFrameworkTracks.distance(null, west, east).isEmpty());
    assertTrue(VehicleFrameworkTracks.route(null, west, east).isEmpty());
    assertTrue(VehicleFrameworkTracks.route("elsewhere", west, east).isEmpty());
    assertEquals(1, registry.inWorld("world").size());
  }

  @Test
  void absentOrOlderVehicleFrameworkDisablesRoutesWithoutLosingTheCallersState() {
    Installation west = station("west", 1, 0);
    Installation east = station("east", 2, 200);
    assertTrue(VehicleFrameworkTracks.distance("world", west, east).isEmpty());
    assertTrue(VehicleFrameworkTracks.sample("world", null, Map.of()).isEmpty());
    assertTrue(VehicleFrameworkTracks.route("world", west, east).isEmpty());
    vehicles.when(VehicleFramework::getTrackRegistry).thenThrow(new NoSuchMethodError("older API"));
    assertTrue(VehicleFrameworkTracks.route("world", west, east).isEmpty());
    vehicles
        .when(VehicleFramework::getTrackRegistry)
        .thenThrow(new IllegalStateException("stopping"));
    assertTrue(VehicleFrameworkTracks.route("world", west, east).isEmpty());
    when(Bukkit.getPluginManager().isPluginEnabled("VehicleFramework")).thenReturn(false);
    assertTrue(VehicleFrameworkTracks.route("world", west, east).isEmpty());
    assertEquals("west", west.getId());
    assertEquals(200, east.getCenterX());
  }

  @Test
  void openTrackSnapshotsIncludeOnlyStationsAndHonorWorldAndInputGuards() throws Exception {
    TrackRegistry registry = registry(spline(0, 100, 200));
    TrackReach.At provinceAt = (x, z) -> (int) (x / 100) + 1;
    TradeGraph.Node station = node("west", InstallationKind.TRAIN_STATION, 1, 0);
    TradeGraph.Node port = node("port", InstallationKind.PORT, 1, 0);
    List<TradeGraph.OpenTrack> found =
        VehicleFrameworkTracks.openTracks(
            "world", Arrays.asList(null, port, station), 2, 130, provinceAt);
    assertEquals(1, found.size());
    assertEquals("home", found.getFirst().ownerFactionId());
    assertEquals("west", found.getFirst().installationId());
    assertTrue(found.getFirst().distances().containsKey(2));
    assertFalse(found.getFirst().distances().containsKey(3));
    assertThrows(
        UnsupportedOperationException.class, () -> found.getFirst().distances().put(7, 2.0));
    assertTrue(
        VehicleFrameworkTracks.openTracks(null, List.of(station), 2, 100, provinceAt).isEmpty());
    assertTrue(VehicleFrameworkTracks.openTracks("world", null, 2, 100, provinceAt).isEmpty());
    assertTrue(
        VehicleFrameworkTracks.openTracks("world", List.of(station), 2, 100, null).isEmpty());
    assertTrue(
        VehicleFrameworkTracks.openTracks("world", List.of(station), -1, 100, provinceAt)
            .isEmpty());
    assertTrue(
        VehicleFrameworkTracks.openTracks("world", List.of(station), 2, 0, provinceAt).isEmpty());
    assertTrue(
        VehicleFrameworkTracks.openTracks("absent", List.of(station), 2, 100, provinceAt)
            .isEmpty());
    vehicles.when(VehicleFramework::getTrackRegistry).thenReturn(null);
    assertTrue(
        VehicleFrameworkTracks.openTracks("world", List.of(station), 2, 100, provinceAt).isEmpty());
    assertEquals(1, registry.inWorld("world").size());
  }

  @Test
  void malformedExternalJunctionRowsDoNotDiscardAValidIndependentTrack() {
    TrackSpline line = spline(0, 100);
    TrackJunction dangling =
        new TrackJunction(
            UUID.randomUUID(),
            line.getId(),
            0,
            0,
            TrackJunction.Side.LEFT,
            UUID.randomUUID(),
            true);
    TrackJunction self =
        new TrackJunction(
            UUID.randomUUID(), line.getId(), 0, 0, TrackJunction.Side.LEFT, line.getId(), true);
    TrackJunction missing =
        new TrackJunction(
            UUID.randomUUID(), line.getId(), 0, 0, TrackJunction.Side.LEFT, null, true);
    Map<Integer, Double> reached =
        VehicleFrameworkTracks.reach(
            Arrays.asList(null, line),
            Arrays.asList(null, dangling, self, missing),
            0,
            0,
            1,
            100,
            (x, z) -> x < 50 ? 1 : 2,
            1);
    assertTrue(reached.get(2) >= 48 && reached.get(2) <= 64);
    assertTrue(VehicleFrameworkTracks.reach(null, null, 0, 0, 1, 100, (x, z) -> 2, 1).isEmpty());
    assertTrue(VehicleFrameworkTracks.reach(List.of(line), null, 0, 0, 1, 100, null, 1).isEmpty());
    TrackSpline broken =
        new TrackSpline(
            line.getId(), "world", false, line.getSamples(), List.of(new TrackSegment(0, true, 1)));
    assertTrue(
        VehicleFrameworkTracks.reach(
                List.of(broken), null, 0, 0, 1, 100, (x, z) -> x < 50 ? 1 : 2, 1)
            .isEmpty());
  }

  @Test
  void shortestTrackTravelSupersedesAnEarlierLongerPathAndRejectsInvalidEdges() {
    List<List<TrackReach.Edge>> graph = new ArrayList<>();
    for (int i = 0; i < 4; i++) graph.add(new ArrayList<>());
    TrackReach.link(graph, 0, 1, 9);
    TrackReach.link(graph, 0, 2, 2);
    TrackReach.link(graph, 2, 1, 2);
    TrackReach.link(graph, 1, 3, 20);
    int edges = graph.stream().mapToInt(List::size).sum();
    TrackReach.link(graph, 0, 0, 1);
    TrackReach.link(graph, 0, 3, -1);
    TrackReach.link(graph, 0, 3, Double.NaN);
    assertEquals(edges, graph.stream().mapToInt(List::size).sum());
    assertArrayEquals(
        new double[] {0, 4, 2, Double.POSITIVE_INFINITY},
        TrackReach.travel(graph, new boolean[] {true}, 10));
    assertTrue(
        Arrays.stream(TrackReach.travel(graph, new boolean[] {true}, -1))
            .allMatch(value -> value == Double.POSITIVE_INFINITY));
  }

  @Test
  void previewSelectsBestModeOnceAndReturnsStableDirectedHubIdentities() {
    TradeGraph graph = parallelGraph();
    TradeGraph.Node first = graph.node("home", "a");
    TradeGraph.Node last = graph.node("home", "b");
    var hubs = Set.of(new Highway.HubSite("HOME", "A"), new Highway.HubSite("home", "B"));
    assertTrue(new Highway.HubSite("HOME", "A").matches(first));
    assertFalse(new Highway.HubSite(null, null).matches(first));
    assertFalse(new Highway.HubSite(null, null).matches(null));
    List<HubTransport.Link> links = Highway.links(graph, hubs);
    assertEquals(2, links.size());
    assertEquals(List.of(1, 2), links.stream().map(HubTransport.Link::fromProvince).toList());
    assertEquals(List.of(2, 1), links.stream().map(HubTransport.Link::toProvince).toList());
    assertTrue(links.stream().allMatch(link -> link.mode() == Mode.RAIL));
    assertEquals("a", links.getFirst().fromInstallationId());
    assertEquals("b", links.getFirst().toInstallationId());
    assertThrows(UnsupportedOperationException.class, () -> links.clear());
    assertTrue(Highway.links(null, hubs).isEmpty());
    assertTrue(Highway.links(graph, null).isEmpty());
    assertTrue(Highway.links(graph, Set.of(new Highway.HubSite("home", "a"))).isEmpty());
    assertTrue(
        Highway.links(
                graph,
                Set.of(new Highway.HubSite("home", "a"), new Highway.HubSite("home", "missing")))
            .isEmpty());
    assertNull(Highway.bestLink(null, first, last));
    assertNull(Highway.bestLink(graph, null, last));
    assertNull(Highway.bestLink(graph, first, null));
    assertEquals(0, Highway.hopFactor(null, 1, 1));
    assertEquals(0, Highway.hopFactor(links.getFirst(), Double.NaN, 1));
    assertEquals(0.1, Highway.hopFactor(links.getFirst(), 2, .25), 1e-9);
    Faction home = fixture.saved("home", "Alice");
    Guild guild = fixture.guild(home, "traders", "Bob");
    ProvinceManager provinces = provinces(1, 2);
    provinces.get(1).setData(guild.getId(), new ProvinceDataEntry(guild, 100, 10));
    assertEquals(40, Highway.wouldArrive(provinces, guild, graph, hubs, last, 0), 1e-9);
    assertEquals(0, Highway.wouldArrive(provinces, guild, graph, hubs, first, 0));
    assertEquals(0, Highway.wouldArrive(null, guild, graph, hubs, last, 0));
    assertEquals(0, Highway.wouldArrive(provinces, null, graph, hubs, last, 0));
    assertEquals(0, Highway.wouldArrive(provinces, guild, null, hubs, last, 0));
    assertEquals(0, Highway.wouldArrive(provinces, guild, graph, hubs, null, 0));
    assertEquals(100, provinces.get(1).getRawGuildTrade(guild));
    assertEquals(0, provinces.get(2).getRawGuildTrade(guild));
  }

  @Test
  void equalTradeModesSelectRailInBothDirections() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("installation-trade.transport.rail.trade", .4);
    config.set("installation-trade.transport.sea.trade", .4);
    HubTransport.loadConfig(config);
    TradeGraph graph = parallelGraph();
    var links =
        Highway.links(
            graph, Set.of(new Highway.HubSite("home", "a"), new Highway.HubSite("home", "b")));
    assertEquals(2, links.size());
    assertTrue(links.stream().allMatch(link -> link.mode() == Mode.RAIL));
    assertTrue(links.stream().allMatch(link -> link.tradeFactor() == .4));
  }

  @Test
  void transportLinksKeepTheirInstallationIdentityAndUseCurrentDistanceLossForBonuses() {
    Installation west = station("west", 1, 0);
    Installation east = station("east", 2, 1000);
    HubTransport.Link link = HubTransport.link(west, "home", east, "guest", Mode.RAIL, 1000);
    assertEquals("home", link.fromFactionId());
    assertEquals("west", link.fromInstallationId());
    assertEquals("guest", link.toFactionId());
    assertEquals("east", link.toInstallationId());
    assertEquals(1, link.fromProvince());
    assertEquals(2, link.toProvince());
    assertEquals(.36, link.tradeFactor(), 1e-9);
    assertEquals(.72, link.boostedTradeFactor(1), 1e-9);
    assertEquals(.72, link.boostedProductionFactor(1), 1e-9);
    assertEquals(.36, HubTransport.link(1, 2, Mode.RAIL, 1000).tradeFactor(), 1e-9);
    YamlConfiguration config = new YamlConfiguration();
    config.set("installation-trade.transport.rail.kept-per-1000-blocks", 0);
    HubTransport.loadConfig(config);
    assertEquals(
        0,
        link.boostedTradeFactor(1),
        "A previously cached link cannot divide by zero after reload");
    assertEquals(0, link.boostedProductionFactor(1));
  }

  @Test
  void zeroAccessOrMissingDeliveryInputsPreserveRealGuildTradeAndProduction() {
    Faction home = fixture.saved("home", "Alice");
    Guild guild = fixture.guild(home, "traders", "Bob");
    ProvinceManager provinces = provinces(1, 2);
    provinces.get(1).setData(guild.getId(), new ProvinceDataEntry(guild, 100, 10));
    TradeGraph graph = TestGraphs.linked("home", 0, station("a", 1, 0), station("b", 2, 0));
    for (TradeGraph missing :
        Arrays.asList(
            null,
            TradeGraphBuilder.build(
                List.of(), Map.of(), (left, right) -> Optional.empty(), point -> 0))) {
      Highway.deliver(provinces, guild, missing, Map.of("home", 1.0));
      Highway.deliverProduction(provinces, guild, missing, Map.of("home", 1.0));
    }
    Highway.deliver(null, guild, graph, Map.of());
    Highway.deliver(provinces, null, graph, Map.of());
    Highway.deliverProduction(null, guild, graph, Map.of());
    Highway.deliverProduction(provinces, null, graph, Map.of());
    Highway.deliver(provinces, guild, graph, null);
    Highway.deliverProduction(provinces, guild, graph, Map.of("home", 0.0));
    assertEquals(100, provinces.get(1).getRawGuildTrade(guild));
    assertEquals(10, provinces.get(1).getGuildProduction(guild));
    assertEquals(0, provinces.get(2).getRawGuildTrade(guild));
    assertEquals(0, provinces.get(2).getGuildProduction(guild));
    Highway.deliver(provinces, guild, graph, Map.of("home", 1.0));
    Highway.deliverProduction(provinces, guild, graph, Map.of("home", 1.0));
    assertEquals(40, provinces.get(2).getRawGuildTrade(guild), 1e-9);
    assertEquals(4, provinces.get(2).getGuildProduction(guild), 1e-9);
  }

  @Test
  void malformedLineInputsFailClearlyAndNonFiniteConfigurationCannotCreateTrade() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Highway.lineDeliveries(new double[] {0}, new double[] {}, new double[] {1}, .4, .9, 1));
    assertArrayEquals(
        new double[] {0, 0},
        Highway.lineDeliveries(
            new double[] {0, 1}, new double[] {100, 0}, new double[] {1, 1}, .4, .9, Double.NaN));
    Cache.supplyHubCorridorShare = Double.NaN;
    assertEquals(.5, Highway.corridorShare());
    Cache.supplyHubCorridorShare = -1;
    assertEquals(0, Highway.corridorShare());
    Cache.supplyHubCorridorShare = 3;
    assertEquals(1, Highway.corridorShare());
    HubTransport.loadConfig(null);
    assertEquals(new HubTransport.Rates(.4, .4, .9), HubTransport.rates(Mode.RAIL));
    YamlConfiguration config = new YamlConfiguration();
    config.set("installation-trade.transport.rail.trade", Double.NaN);
    config.set("installation-trade.transport.rail.production", -1);
    config.set("installation-trade.transport.rail.kept-per-1000-blocks", 0);
    HubTransport.loadConfig(config);
    assertEquals(new HubTransport.Rates(0, 0, 0), HubTransport.rates(Mode.RAIL));
    var legacy = new HubTransport.Link(1, 2, Mode.RAIL, 1000, .5, .5);
    assertEquals(0, legacy.boostedTradeFactor(1));
    assertEquals(0, legacy.boostedProductionFactor(1));
    assertEquals(0, HubTransport.delivered(.4, 0, 0));
  }

  private TradeGraph parallelGraph() {
    Installation left = new Installation("a", "West", InstallationKind.PORT, 1, 0, 0, 1);
    Installation right = new Installation("b", "East", InstallationKind.PORT, 2, 0, 0, 1);
    return TradeGraphBuilder.build(
        List.of(new Site("home", left, true), new Site("home", right, true)),
        Map.of(
            1,
            new ProvinceData(Terrain.PLAINS, Set.of(9)),
            9,
            new ProvinceData(Terrain.SEA, Set.of(1, 2)),
            2,
            new ProvinceData(Terrain.PLAINS, Set.of(9))),
        (from, to) -> Optional.of(new RailRoutes.Route(0, List.of())),
        point -> 0);
  }

  private ProvinceManager provinces(int... ids) {
    Map<Integer, Province> data = new LinkedHashMap<>();
    for (int id : ids) data.put(id, new Province(id, "PLAINS", 50));
    ProvinceManager provinces = new ProvinceManager();
    provinces.start(data);
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(provinces);
    fixture.provincesEnabled(true);
    return provinces;
  }

  private TrackRegistry registry(TrackSpline... tracks) {
    Path directory = temporary.resolve("registry-" + registries.size());
    TrackStore store = new TrackStore(directory.toFile());
    for (TrackSpline track : tracks) store.save(track);
    store.close();
    TrackRegistry registry = new TrackRegistry(directory.toFile());
    registry.loadFromDisk();
    registries.add(registry);
    vehicles.when(VehicleFramework::getTrackRegistry).thenReturn(registry);
    return registry;
  }

  private ProvinceGrid grid() throws Exception {
    ByteBuffer buffer = ByteBuffer.allocate(8 + 201 * 2).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(201).putInt(1);
    for (int x = 0; x <= 200; x++) buffer.putShort((short) (x / 100 + 1));
    Path path = temporary.resolve("grid.bin.gz");
    try (GZIPOutputStream stream = new GZIPOutputStream(Files.newOutputStream(path))) {
      stream.write(buffer.array());
    }
    return ProvinceGrid.load(path.toFile());
  }

  private void configureInstallations() throws Exception {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("consent-proximity-blocks", 20);
    yaml.set("transfer-request-timeout-seconds", 60);
    for (InstallationKind kind : InstallationKind.values()) {
      String key = kind.getCommandName();
      yaml.set(key + ".daily-upkeep", 1);
      yaml.set(key + ".construction-time", 1);
      yaml.set(key + ".radius", 2);
      yaml.createSection(key + ".slots");
    }
    Path path = temporary.resolve("installations.yml");
    yaml.save(path.toFile());
    InstallationConfigLoader.load(path.toFile());
  }

  private void remember(Class<?> owner, String name) throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    saved.put(field, field.get(null));
  }

  @SuppressWarnings("unchecked")
  private Map<Object, Object> kindMap() throws Exception {
    Field field = InstallationConfigLoader.class.getDeclaredField("byKind");
    field.setAccessible(true);
    return (Map<Object, Object>) field.get(null);
  }

  private static Installation station(String id, int province, int x) {
    return new Installation(id, id, InstallationKind.TRAIN_STATION, province, x, 0, 1);
  }

  private static TradeGraph.Node node(String id, InstallationKind kind, int province, int x) {
    return new TradeGraph.Node("home", id, id, kind, province, x, 0, 1, 0);
  }

  private static void assertDistances(Map<Integer, Double> expected, Map<Integer, Double> actual) {
    assertEquals(expected.keySet(), actual.keySet());
    expected.forEach((province, distance) -> assertEquals(distance, actual.get(province), 1e-9));
  }

  private static TrackSpline spline(double... xs) {
    java.util.ArrayList<TrackSample> samples = new java.util.ArrayList<>();
    for (double x : xs) samples.add(new TrackSample(x, 0, 0, 0, 0, x));
    return new TrackSpline(UUID.randomUUID(), "world", false, samples, null);
  }
}
