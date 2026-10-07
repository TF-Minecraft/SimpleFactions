package net.tfminecraft.simplefactions.guild.network;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.InstallationConstructionData;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.guild.hub.OpenTrackSettings;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.tracks.TrackRegistry;
import net.tfminecraft.vehicleframework.tracks.TrackSample;
import net.tfminecraft.vehicleframework.tracks.TrackSpline;
import net.tfminecraft.vehicleframework.tracks.TrackStore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class TradeNetworkRuntimeCoverageTest {
  @TempDir Path temporary;
  private FactionDomainFixture fixture;
  private MockedStatic<VehicleFramework> vehicles;
  private ProvinceManager provinces;
  private final Map<Field, Object> saved = new LinkedHashMap<>();
  private Map<Object, Object> installationKinds;
  private Map<Object, Object> routeCache;
  private Map<Object, Object> savedRoutes;
  private TradeGraph previousLive;
  private TrackRegistry registry;
  private TrackSpline track;

  @BeforeEach
  void setUp() throws Exception {
    fixture = new FactionDomainFixture();
    vehicles = mockStatic(VehicleFramework.class);
    previousLive = TradeGraph.live();
    for (String field : List.of("enabled", "share", "keptPer1000", "rangeBlocks"))
      remember(OpenTrackSettings.class, field);
    remember(InstallationConfigLoader.class, "consentProximityBlocks");
    remember(InstallationConfigLoader.class, "transferRequestTimeoutSeconds");
    installationKinds = new LinkedHashMap<>(kindMap());
    routeCache = routeCache();
    savedRoutes = new LinkedHashMap<>(routeCache);
    TradeGraph.forgetRoutes();
    OpenTrackSettings.reset();
    configureInstallations();
    when(Bukkit.getPluginManager().isPluginEnabled("VehicleFramework")).thenReturn(true);
    when(Bukkit.isPrimaryThread()).thenReturn(true);
    Map<Integer, Province> data = new LinkedHashMap<>();
    data.put(0, new Province());
    for (int id = 1; id <= 5; id++) data.put(id, new Province(id, "PLAINS", 50, (id - 1) * 100, 0));
    provinces = new ProvinceManager();
    provinces.start(data);
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(provinces);
    when(fixture.ui.plugin.getProvinceGrid()).thenReturn(grid());
    track =
        new TrackSpline(
            UUID.randomUUID(),
            "world",
            false,
            List.of(
                new TrackSample(0, 0, 0, 0, 0, 0),
                new TrackSample(100, 0, 0, 0, 0, 100),
                new TrackSample(200, 0, 0, 0, 0, 200)),
            null);
    TrackStore store = new TrackStore(temporary.resolve("tracks").toFile());
    store.save(track);
    store.close();
    registry = new TrackRegistry(temporary.resolve("tracks").toFile());
    registry.loadFromDisk();
    vehicles.when(VehicleFramework::getTrackRegistry).thenReturn(registry);
  }

  @AfterEach
  void close() throws Exception {
    registry.close();
    TradeGraph.setLiveForTests(previousLive);
    routeCache.clear();
    routeCache.putAll(savedRoutes);
    kindMap().clear();
    kindMap().putAll(installationKinds);
    for (var entry : saved.entrySet()) entry.getKey().set(null, entry.getValue());
    vehicles.close();
    fixture.close();
  }

  @Test
  void refreshReadsFinishedInstallationsRealTrackRoutesAndProvinceGrid() {
    Faction home = stations();
    home.getInstallationHandler()
        .acceptTransferred(new Installation("fort", "Keep", InstallationKind.FORT, 2, 100, 0, 2));
    InstallationConstructionData pending = new InstallationConstructionData();
    pending.id = "pending";
    pending.name = "Future station";
    pending.kind = InstallationKind.TRAIN_STATION.getCommandName();
    pending.province = 5;
    pending.centerX = 400;
    pending.centerZ = 0;
    pending.timeLeft = 60;
    pending.startedAt = 1L;
    home.getInstallationHandler().loadConstruction(pending);
    FactionManager.factions.add(null);

    TradeGraph.refresh(provinces);

    TradeGraph graph = TradeGraph.live();
    assertEquals(2, graph.nodes().size());
    assertNull(graph.node("home", "fort"));
    assertNull(graph.node("home", "pending"));
    assertNotNull(home.getInstallationHandler().getPendingConstruction());
    assertEquals(1, graph.edges().size());
    TradeGraph.Edge edge = graph.edges().getFirst();
    assertEquals(Mode.RAIL, edge.mode());
    assertEquals(List.of(2), edge.provinces());
    assertTrue(edge.length() > 190 && edge.length() <= 200);
    assertTrue(graph.openTrack("HOME", "WEST").containsKey(2));
    assertTrue(graph.openTrack("home", "west").containsKey(3));
    assertFalse(graph.openTrack("home", "west").containsKey(1));
    assertEquals(2, graph.networkOf(graph.node("home", "west")).size());
    assertFalse(graph.networkOf(graph.node("home", "west")).global());
  }

  @Test
  void routeCacheRetainsSuccessfulMeasurementsUntilExplicitTrackRefresh() {
    stations();
    TradeGraph.refresh(provinces);
    TradeGraph before = TradeGraph.live();
    assertEquals(1, before.edges().size());
    assertTrue(registry.delete(track.getId()));
    TradeGraph.refresh(provinces);
    assertEquals(before.edges(), TradeGraph.live().edges());
    assertTrue(TradeGraph.live().openTrack("home", "west").isEmpty());
    TradeGraph.forgetRoutes();
    TradeGraph.refresh(provinces);
    assertEquals(2, TradeGraph.live().nodes().size());
    assertTrue(TradeGraph.live().edges().isEmpty());
    assertTrue(
        before.openTrack("home", "west").containsKey(2), "The old immutable snapshot stays usable");
  }

  @Test
  void liveRefreshRequiresTheActualManagerAndPrimaryThread() {
    stations();
    TradeGraph.refresh(provinces);
    TradeGraph before = TradeGraph.live();
    TradeGraph.refreshIfLive(new ProvinceManager());
    assertSame(before, TradeGraph.live());
    when(Bukkit.isPrimaryThread())
        .thenReturn(false)
        .thenThrow(new IllegalStateException("server unavailable"))
        .thenReturn(true);
    TradeGraph.refreshIfLive(provinces);
    assertSame(before, TradeGraph.live());
    TradeGraph.refreshIfLive(provinces);
    assertSame(before, TradeGraph.live());
    TradeGraph.refreshIfLive(provinces);
    assertNotSame(before, TradeGraph.live());
    assertEquals(before.edges(), TradeGraph.live().edges());
    before = TradeGraph.live();
    SimpleFactions.plugin = null;
    TradeGraph.refreshIfLive(provinces);
    assertSame(before, TradeGraph.live());
    SimpleFactions.plugin = fixture.ui.plugin;
  }

  @Test
  void unavailableVehiclePluginLeavesIndependentNodesInsteadOfFailingRefresh() {
    stations();
    when(Bukkit.getPluginManager().isPluginEnabled("VehicleFramework")).thenReturn(false);
    TradeGraph.refresh(provinces);
    assertIsolatedStations();
    TradeGraph.forgetRoutes();
    when(Bukkit.getPluginManager().isPluginEnabled("VehicleFramework"))
        .thenThrow(new NoClassDefFoundError("older installation"));
    TradeGraph.refresh(provinces);
    assertIsolatedStations();
    TradeGraph.forgetRoutes();
    var plugins = Bukkit.getPluginManager();
    doReturn(true).when(plugins).isPluginEnabled("VehicleFramework");
    vehicles
        .when(VehicleFramework::getTrackRegistry)
        .thenThrow(new IllegalStateException("stopping"));
    TradeGraph.refresh(provinces);
    assertIsolatedStations();
  }

  @Test
  void missingStationConfigurationUsesTheDocumentedOpenTrackRadiusFallback() throws Exception {
    Faction home = fixture.saved("home", "Alice");
    home.getInstallationHandler()
        .acceptTransferred(
            new Installation("west", "West", InstallationKind.TRAIN_STATION, 1, 40, 0, 1));
    kindMap().clear();
    TradeGraph.refresh(provinces);
    assertEquals(1, TradeGraph.live().nodes().size());
    assertTrue(TradeGraph.live().openTrack("home", "west").containsKey(3));
    assertFalse(TradeGraph.live().openTrack("home", "west").containsKey(1));
  }

  @Test
  void disabledOpenTracksNullWorldAndAbsentGridHaveExplicitEmptyReach() {
    stations();
    YamlConfiguration config = new YamlConfiguration();
    config.set("installation-trade.open-track.enabled", false);
    OpenTrackSettings.load(config);
    TradeGraph.refresh(provinces);
    assertEquals(1, TradeGraph.live().edges().size());
    assertTrue(TradeGraph.live().openTrack("home", "west").isEmpty());
    OpenTrackSettings.reset();
    Cache.worldName = null;
    TradeGraph.forgetRoutes();
    TradeGraph.refresh(provinces);
    assertIsolatedStations();
    Cache.worldName = "world";
    when(fixture.ui.plugin.getProvinceGrid()).thenReturn(null);
    TradeGraph.forgetRoutes();
    TradeGraph.refresh(provinces);
    assertEquals(1, TradeGraph.live().edges().size());
    assertTrue(TradeGraph.live().edges().getFirst().provinces().isEmpty());
    assertTrue(TradeGraph.live().openTrack("home", "west").isEmpty());
    SimpleFactions.plugin = null;
    TradeGraph.refresh(provinces);
    assertEquals(2, TradeGraph.live().nodes().size());
    assertTrue(TradeGraph.live().openTrack("home", "west").isEmpty());
    SimpleFactions.plugin = fixture.ui.plugin;
  }

  @Test
  void anUninitializedFactionRegistryProducesAnEmptyImmutableSnapshot() {
    FactionManager.factions = null;
    TradeGraph.refresh(provinces);
    assertTrue(TradeGraph.live().nodes().isEmpty());
    assertTrue(TradeGraph.live().edges().isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> TradeGraph.live().nodes().clear());
  }

  @Test
  void publicSnapshotsFilterBadDistancesMergeDuplicateStationsAndRejectForeignEdgeNodes() {
    stations();
    TradeGraph.refresh(provinces);
    TradeGraph before = TradeGraph.live();
    TradeGraph graph =
        before.withOpenTracks(
            Arrays.asList(
                null,
                new TradeGraph.OpenTrack(null, "west", Map.of(7, 1.0)),
                new TradeGraph.OpenTrack("home", null, Map.of(7, 1.0)),
                new TradeGraph.OpenTrack("home", "west", null),
                new TradeGraph.OpenTrack("HOME", "WEST", Map.of(2, 10.0, 3, Double.NaN, 4, -2.0)),
                new TradeGraph.OpenTrack("home", "west", Map.of(2, 5.0, 5, 20.0))));
    assertEquals(Map.of(2, 5.0, 5, 20.0), graph.openTrack("home", "west"));
    assertTrue(graph.openTrack(null, "west").isEmpty());
    assertTrue(graph.openTrack("home", null).isEmpty());
    assertTrue(graph.withOpenTracks(null).openTrack("home", "west").isEmpty());
    assertThrows(
        UnsupportedOperationException.class, () -> graph.openTrack("home", "west").put(6, 2.0));
    assertNotEquals(before.openTrack("home", "west"), graph.openTrack("home", "west"));
    TradeGraph.Node outsider =
        new TradeGraph.Node("elsewhere", "other", "Other", InstallationKind.PORT, 5, 0, 0, 1, 0);
    TradeGraph.Edge edge = graph.edges().getFirst();
    assertThrows(IllegalArgumentException.class, () -> edge.other(outsider));
    assertThrows(IllegalArgumentException.class, () -> edge.provincesFrom(outsider));
    assertNull(graph.node(null, "west"));
    assertNull(graph.node("home", null));
    assertNull(graph.networkOf(outsider));
    assertEquals(Set.of(1, 3), Set.of(edge.first().provinceId(), edge.second().provinceId()));
  }

  @Test
  void openTrackConfigurationFallsBackForNonFiniteSharesAndDisablesInvalidRange() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("installation-trade.open-track.share", Double.NaN);
    config.set("installation-trade.open-track.kept-per-1000-blocks", -1);
    config.set("installation-trade.open-track.range-blocks", Double.POSITIVE_INFINITY);
    OpenTrackSettings.load(config);
    assertEquals(OpenTrackSettings.DEFAULT_SHARE, OpenTrackSettings.share());
    assertEquals(OpenTrackSettings.DEFAULT_KEPT, OpenTrackSettings.keptPer1000());
    assertEquals(0, OpenTrackSettings.rangeBlocks());
    stations();
    TradeGraph.refresh(provinces);
    assertTrue(TradeGraph.live().openTrack("home", "west").isEmpty());
    config.set("installation-trade.open-track.share", -1);
    config.set("installation-trade.open-track.kept-per-1000-blocks", Double.NaN);
    OpenTrackSettings.load(config);
    assertEquals(OpenTrackSettings.DEFAULT_SHARE, OpenTrackSettings.share());
    assertEquals(OpenTrackSettings.DEFAULT_KEPT, OpenTrackSettings.keptPer1000());
  }

  @Test
  void missingRealmAndUnrelatedLawScopesCannotInventInstallationAccess() {
    assertNull(InstallationAccess.topRealm(null));
    var law = fixture.lawGroup("economy", Map.of()).getLaw("current");
    assertNotNull(law);
    assertEquals(0, InstallationAccess.amount(law, Scope.DOMESTIC_GUILDS, Region.OUR_TERRITORY));
    Faction home = fixture.saved("home", "Alice");
    Faction foreign = fixture.saved("foreign", "Bob");
    assertEquals(0, InstallationAccess.of(home, foreign));
    assertEquals(1, InstallationAccess.of(home, home));
  }

  private Faction stations() {
    Faction home = fixture.saved("home", "Alice");
    home.getInstallationHandler()
        .acceptTransferred(
            new Installation("west", "West", InstallationKind.TRAIN_STATION, 1, 0, 0, 1));
    home.getInstallationHandler()
        .acceptTransferred(
            new Installation("east", "East", InstallationKind.TRAIN_STATION, 3, 200, 0, 1));
    return home;
  }

  private void assertIsolatedStations() {
    assertEquals(2, TradeGraph.live().nodes().size());
    assertTrue(TradeGraph.live().edges().isEmpty());
    assertTrue(TradeGraph.live().openTrack("home", "west").isEmpty());
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

  @SuppressWarnings("unchecked")
  private Map<Object, Object> routeCache() throws Exception {
    Field field = LiveTradeGraph.class.getDeclaredField("routes");
    field.setAccessible(true);
    Object cache = field.get(null);
    field = RailRouteCache.class.getDeclaredField("routes");
    field.setAccessible(true);
    return (Map<Object, Object>) field.get(cache);
  }
}
