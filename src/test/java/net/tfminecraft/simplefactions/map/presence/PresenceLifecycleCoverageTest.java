package net.tfminecraft.simplefactions.map.presence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.events.*;
import net.tfminecraft.simplefactions.loaders.RegionLoader;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.ProvinceSpatial;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.tiers.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresenceLifecycleCoverageTest {
  @TempDir Path temporary;
  private FactionDomainFixture fixture;
  private final Map<Field, Object> globals = new LinkedHashMap<>();
  private final List<Event> events = new ArrayList<>();
  private ProvincePresenceService previousProvinces;
  private TitlePresenceService previousTitles;
  private RegionPresenceService previousRegions;

  @BeforeEach
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    previousProvinces = ProvincePresenceService.getInstance();
    previousTitles = TitlePresenceService.getInstance();
    previousRegions = RegionPresenceService.getInstance();
    for (String name : List.of("regions", "byId", "byProvince")) remember(RegionLoader.class, name);
    for (String name :
        List.of("mapEnabled", "portSeaProximityBlocks", "battleProvincePollIntervalTicks"))
      remember(Cache.class, name);
    Cache.mapEnabled = true;
    fixture.provincesEnabled(true);
    ProvincePresenceService.resetForTests();
    TitlePresenceService.resetForTests();
    RegionPresenceService.resetForTests();
    var plugins = Bukkit.getPluginManager();
    doAnswer(
            call -> {
              events.add(call.getArgument(0));
              return null;
            })
        .when(plugins)
        .callEvent(any(Event.class));
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> fixture.online.values());
    ProvinceManager manager = new ProvinceManager();
    manager.start(
        Map.of(
            1,
            new Province(1, "PLAINS", 50),
            2,
            new Province(2, "SEA", 0),
            3,
            new Province(3, "PLAINS", 50)));
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(manager);
    grid(new int[][] {{1, 2, 0}, {3, 1, 9}});
  }

  @AfterEach
  void close() throws Exception {
    ProvincePresenceService.setInstance(previousProvinces);
    TitlePresenceService.setInstance(previousTitles);
    RegionPresenceService.setInstance(previousRegions);
    for (var entry : globals.entrySet()) entry.getKey().set(null, entry.getValue());
    fixture.close();
  }

  @Test
  void largeRadiusDoesNotWrapAndIncludeSeaOutsideTheCircle() throws Exception {
    grid(new int[][] {{2}});
    assertFalse(
        ProvinceSpatial.withinBlocksOfSea(65536, 65536, 65536),
        "The only sea cell is sqrt(2) radii away; integer squares must not wrap to zero");
    assertTrue(
        ProvinceSpatial.withinBlocksOfSea(65536, 0, 65536),
        "A point exactly on the large circle remains included");
    assertFalse(
        ProvinceSpatial.withinBlocksOfSea(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    assertTrue(ProvinceSpatial.withinBlocksOfSea(0, 0, Integer.MAX_VALUE));
  }

  @Test
  void crossingIntoAnotherWorldLeavesTheMappedProvinceAndItsTitles() throws Exception {
    titlesAndRegions();
    Player player = player("Alice");
    AtomicReference<Location> location =
        new AtomicReference<>(new Location(fixture.ui.world, 0, 64, 0));
    when(player.getLocation()).thenAnswer(call -> location.get());
    ProvincePresenceService service = ProvincePresenceService.getInstance();
    service.tick();
    events.clear();
    World other = mock(World.class);
    when(other.getName()).thenReturn("world_nether");
    location.set(new Location(other, 0, 64, 0));
    service.tick();
    assertEquals(ProvincePresenceService.UNKNOWN_PROVINCE, service.getCurrentProvince(player));
    assertEquals(1, events.stream().filter(PlayerProvinceLeaveEvent.class::isInstance).count());
    assertEquals(2, events.stream().filter(PlayerTitleLeaveEvent.class::isInstance).count());
    assertEquals(1, events.stream().filter(PlayerLeaveRegionEvent.class::isInstance).count());
  }

  @Test
  void defaultServicesPublishOrderedTransitionsAndQuitClearsEveryPresenceLayer() throws Exception {
    titlesAndRegions();
    Player player = player("Alice");
    AtomicReference<Location> location =
        new AtomicReference<>(new Location(fixture.ui.world, 0, 64, 0));
    when(player.getLocation()).thenAnswer(call -> location.get());
    ProvincePresenceService service = ProvincePresenceService.getInstance();
    service.tick();
    assertTrue(service.isInProvince(player, 1));
    assertEquals(
        List.of(
            PlayerProvinceEnterEvent.class,
            PlayerTitleEnterEvent.class,
            PlayerTitleEnterEvent.class,
            PlayerEnterRegionEvent.class),
        eventTypes());
    var enter = (PlayerProvinceEnterEvent) events.getFirst();
    assertSame(player, enter.getPlayer());
    assertEquals(1, enter.getProvinceId());
    assertNull(enter.getPreviousProvinceId());
    assertSame(PlayerProvinceEnterEvent.getHandlerList(), enter.getHandlers());
    var title = (PlayerTitleEnterEvent) events.get(1);
    assertSame(player, title.getPlayer());
    assertEquals("county", title.getTierId());
    assertEquals("west", title.getTitleId());
    assertEquals("west", title.getTitleName());
    assertNull(title.getPreviousTitleId());
    assertSame(PlayerTitleEnterEvent.getHandlerList(), title.getHandlers());
    var region = (PlayerEnterRegionEvent) events.getLast();
    assertSame(player, region.getPlayer());
    assertEquals("west", region.getRegionId());
    assertEquals("Western March", region.getRegionName());
    assertNull(region.getPreviousRegionId());
    assertSame(PlayerEnterRegionEvent.getHandlerList(), region.getHandlers());
    events.clear();
    service.tick();
    assertTrue(events.isEmpty());
    location.set(new Location(fixture.ui.world, 0, 64, 1));
    service.tick();
    assertEquals(
        List.of(
            PlayerProvinceLeaveEvent.class,
            PlayerProvinceEnterEvent.class,
            PlayerTitleLeaveEvent.class,
            PlayerTitleEnterEvent.class,
            PlayerLeaveRegionEvent.class,
            PlayerEnterRegionEvent.class),
        eventTypes());
    assertEquals(3, service.getCurrentProvince(player.getUniqueId()));
    var departure = (PlayerProvinceLeaveEvent) events.getFirst();
    assertSame(player, departure.getPlayer());
    assertEquals(1, departure.getProvinceId());
    assertEquals(3, departure.getNextProvinceId());
    assertSame(PlayerProvinceLeaveEvent.getHandlerList(), departure.getHandlers());
    assertNotSame(enter.getHandlers(), departure.getHandlers());
    var arrival = (PlayerProvinceEnterEvent) events.get(1);
    assertEquals(1, arrival.getPreviousProvinceId());
    assertEquals(3, arrival.getProvinceId());
    var oldTitle = (PlayerTitleLeaveEvent) events.get(2);
    assertSame(player, oldTitle.getPlayer());
    assertEquals("county", oldTitle.getTierId());
    assertEquals("west", oldTitle.getTitleId());
    assertEquals("east", oldTitle.getNextTitleId());
    assertSame(PlayerTitleLeaveEvent.getHandlerList(), oldTitle.getHandlers());
    assertNotSame(title.getHandlers(), oldTitle.getHandlers());
    var newTitle = (PlayerTitleEnterEvent) events.get(3);
    assertEquals("east", newTitle.getTitleId());
    assertEquals("west", newTitle.getPreviousTitleId());
    var oldRegion = (PlayerLeaveRegionEvent) events.get(4);
    assertSame(player, oldRegion.getPlayer());
    assertEquals("west", oldRegion.getRegionId());
    assertEquals("east", oldRegion.getNextRegionId());
    assertSame(PlayerLeaveRegionEvent.getHandlerList(), oldRegion.getHandlers());
    assertNotSame(region.getHandlers(), oldRegion.getHandlers());
    var newRegion = (PlayerEnterRegionEvent) events.get(5);
    assertEquals("east", newRegion.getRegionId());
    assertEquals("west", newRegion.getPreviousRegionId());
    events.clear();
    new ProvincePresenceListener().onPlayerQuit(new PlayerQuitEvent(player, "left"));
    assertEquals(
        List.of(
            PlayerProvinceLeaveEvent.class,
            PlayerTitleLeaveEvent.class,
            PlayerTitleLeaveEvent.class,
            PlayerLeaveRegionEvent.class),
        eventTypes());
    assertEquals(3, ((PlayerProvinceLeaveEvent) events.getFirst()).getProvinceId());
    assertNull(((PlayerProvinceLeaveEvent) events.getFirst()).getNextProvinceId());
    assertNull(((PlayerTitleLeaveEvent) events.get(1)).getNextTitleId());
    assertNull(((PlayerTitleLeaveEvent) events.get(2)).getNextTitleId());
    assertEquals("east", ((PlayerLeaveRegionEvent) events.getLast()).getRegionId());
    assertNull(((PlayerLeaveRegionEvent) events.getLast()).getNextRegionId());
    assertEquals(ProvincePresenceService.UNKNOWN_PROVINCE, service.getCurrentProvince(player));
    events.clear();
    new ProvincePresenceListener().onPlayerQuit(new PlayerQuitEvent(player, "left"));
    assertTrue(events.isEmpty());
  }

  @Test
  void spatialQueriesUseCircularDistanceMapBoundsAndKnownSeaProvinces() {
    assertFalse(ProvinceSpatial.isSeaAt(0, 0));
    assertTrue(ProvinceSpatial.isSeaAt(1, 0));
    assertFalse(ProvinceSpatial.isSeaAt(2, 0));
    assertFalse(ProvinceSpatial.isSeaAt(2, 1));
    assertFalse(ProvinceSpatial.isSeaAt(-1, 0));
    assertTrue(ProvinceSpatial.withinBlocksOfSea(0, 0, 1));
    assertFalse(ProvinceSpatial.withinBlocksOfSea(0, 1, 1));
    assertFalse(ProvinceSpatial.withinBlocksOfSea(0, 0, 0));
    assertFalse(ProvinceSpatial.withinBlocksOfSea(2, 0, 0));
    assertFalse(ProvinceSpatial.withinBlocksOfSea(0, 0, -1));
    assertFalse(ProvinceSpatial.withinBlocksOfSea(-4, -4, 1));
    Cache.portSeaProximityBlocks = 1;
    assertTrue(ProvinceSpatial.withinConfiguredPortSeaProximity(0, 0));
    when(fixture.ui.plugin.getProvinceGrid()).thenReturn(null);
    assertFalse(ProvinceSpatial.isSeaAt(1, 0));
    assertFalse(ProvinceSpatial.withinBlocksOfSea(0, 0, 1));
  }

  @Test
  void pollingStartsAtAValidIntervalAndDefersPresenceUntilItsScheduledCallback() {
    Player player = player("Alice");
    when(player.getLocation()).thenReturn(new Location(fixture.ui.world, 0, 64, 0));
    Cache.battleProvincePollIntervalTicks = 0;
    ProvincePresenceTickService.start();
    verify(fixture.ui.scheduler)
        .runTaskTimer(eq(fixture.ui.plugin), any(Runnable.class), eq(1L), eq(1L));
    assertTrue(events.isEmpty());
    assertEquals(1, fixture.ui.repeatingTasks.size());
    fixture.ui.repeatingTasks.getFirst().run();
    assertTrue(ProvincePresenceService.getInstance().isInProvince(player, 1));
    assertEquals(1, events.stream().filter(PlayerProvinceEnterEvent.class::isInstance).count());
    fixture.ui.repeatingTasks.getFirst().run();
    assertEquals(1, events.size(), "Stationary players produce no repeated entry events");
  }

  @Test
  void absentOfflineAndUnresolvedPlayersDoNotPublishSpuriousEvents() {
    Player player = player("Alice");
    when(player.getLocation()).thenReturn(new Location(fixture.ui.world, 0, 64, 0));
    ProvincePresenceService service = ProvincePresenceService.getInstance();
    assertEquals(
        ProvincePresenceService.UNKNOWN_PROVINCE, service.getCurrentProvince((Player) null));
    assertEquals(ProvincePresenceService.UNKNOWN_PROVINCE, service.getCurrentProvince((UUID) null));
    service.tick((List<Player>) null);
    service.handleQuit((Player) null);
    service.handleQuit((UUID) null);
    TitlePresenceService.getInstance().applyProvince((Player) null, 1);
    TitlePresenceService.getInstance().applyProvince((UUID) null, 1);
    TitlePresenceService.getInstance().handleQuit((Player) null);
    TitlePresenceService.getInstance().handleQuit((UUID) null);
    TitlePresenceService.getInstance().handleQuit(UUID.randomUUID());
    RegionPresenceService.getInstance().applyProvince((Player) null, 1);
    RegionPresenceService.getInstance().applyProvince((UUID) null, 1);
    RegionPresenceService.getInstance().handleQuit((Player) null);
    RegionPresenceService.getInstance().handleQuit((UUID) null);
    when(player.isOnline()).thenReturn(false);
    service.tick(Arrays.asList(null, player));
    assertTrue(events.isEmpty());
    when(player.isOnline()).thenReturn(true);
    SimpleFactions.plugin = null;
    service.tick();
    assertTrue(events.isEmpty());
    SimpleFactions.plugin = fixture.ui.plugin;
    when(Bukkit.getServer()).thenReturn(null);
    service.tick();
    assertTrue(events.isEmpty());
    when(Bukkit.getServer()).thenReturn(fixture.ui.server);
    when(Bukkit.getPlayer(player.getUniqueId())).thenReturn(null);
    service.tick();
    service.handleQuit(player);
    assertEquals(ProvincePresenceService.UNKNOWN_PROVINCE, service.getCurrentProvince(player));
    assertTrue(events.isEmpty());
  }

  @Test
  void injectableEntryOnlyCallbacksCanIgnoreDepartureAndResolveMissingTitles() {
    Player player = player("Alice");
    List<String> entries = new ArrayList<>();
    ProvincePresenceService provinces =
        new ProvincePresenceService(
            p -> 1, (id, province, previous) -> entries.add("province:" + province));
    ProvincePresenceService.setInstance(provinces);
    assertSame(provinces, ProvincePresenceService.getInstance());
    provinces.tick(List.of(player));
    provinces.handleQuit(player);
    assertEquals(ProvincePresenceService.UNKNOWN_PROVINCE, provinces.getCurrentProvince(player));
    TitlePresenceService titles =
        new TitlePresenceService(
            province ->
                province == 1
                    ? Map.of("county", new TitlePresenceResolver.ResolvedTitle("west", "West"))
                    : null,
            (id, tier, title, name, previous) -> entries.add("title:" + title));
    TitlePresenceService.setInstance(titles);
    assertSame(titles, TitlePresenceService.getInstance());
    titles.applyProvince(player, 1);
    titles.applyProvince(player, 2);
    titles.handleQuit(player);
    RegionPresenceService regions =
        new RegionPresenceService(
            province ->
                province == 1
                    ? new net.tfminecraft.simplefactions.map.MapRegion(
                        "west", "West", java.util.Set.of(1))
                    : null,
            (id, region, name, previous) -> entries.add("region:" + region));
    RegionPresenceService.setInstance(regions);
    assertSame(regions, RegionPresenceService.getInstance());
    regions.applyProvince(player, 1);
    regions.applyProvince(player, 1);
    regions.applyProvince(player, 2);
    regions.handleQuit(player);
    assertEquals(List.of("province:1", "title:west", "region:west"), entries);
    ProvincePresenceService.setInstance(null);
    TitlePresenceService.setInstance(null);
    RegionPresenceService.setInstance(null);
    assertNotSame(provinces, ProvincePresenceService.getInstance());
    assertNotSame(titles, TitlePresenceService.getInstance());
    assertNotSame(regions, RegionPresenceService.getInstance());
    assertEquals(
        ProvincePresenceService.UNKNOWN_PROVINCE,
        ProvincePresenceService.getInstance().getCurrentProvince(player));
  }

  @Test
  void titleResolutionKeepsTheFirstTierUsesIdFallbackAndReturnsAnImmutableHierarchy() {
    JsonObject unnamed = new JsonObject();
    JsonArray provinceIds = new JsonArray();
    provinceIds.add(1);
    unnamed.add("provinces", provinceIds);
    Title county = new Title(TierLoader.getByString("county"), "unnamed", unnamed);
    TitleLoader.getTitles().add(county);
    JsonObject parent = new JsonObject();
    parent.addProperty("name", "Higher County");
    JsonArray children = new JsonArray();
    children.add("unnamed");
    parent.add("titles", children);
    TitleLoader.getTitles().add(new Title(TierLoader.getByString("county"), "parent", parent));
    assertEquals(
        Map.of("county", new TitlePresenceResolver.ResolvedTitle("unnamed", "unnamed")),
        TitlePresenceResolver.resolve(1));
    assertThrows(
        UnsupportedOperationException.class, () -> TitlePresenceResolver.resolve(1).clear());
    assertTrue(TitlePresenceResolver.resolve(ProvincePresenceService.UNKNOWN_PROVINCE).isEmpty());
    assertTrue(TitlePresenceResolver.resolve(999).isEmpty());
    JsonObject legacy = new JsonObject();
    JsonArray unknown = new JsonArray();
    unknown.add(2);
    legacy.add("provinces", unknown);
    TitleLoader.getTitles().add(new Title(null, "legacy", legacy));
    assertTrue(
        TitlePresenceResolver.resolve(2).isEmpty(),
        "A title with an unavailable tier does not invent a tier event");
  }

  private Player player(String name) {
    Player player = fixture.player(name);
    when(Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
    return player;
  }

  private void titlesAndRegions() throws Exception {
    fixture.title("west", "county", 1);
    fixture.title("east", "county", 3);
    JsonObject duchy = new JsonObject();
    duchy.addProperty("name", "Both Counties");
    JsonArray children = new JsonArray();
    children.add("west");
    children.add("east");
    duchy.add("titles", children);
    TitleLoader.getTitles().add(new Title(TierLoader.getByString("duchy"), "both", duchy));
    Path path = temporary.resolve("regions.json");
    Files.writeString(
        path,
        "{\"west\":{\"name\":\"Western March\",\"provinces\":[1]},\"east\":{\"name\":\"Eastern"
            + " March\",\"provinces\":[3]}}");
    RegionLoader.loadAll(path.toFile());
  }

  private List<Class<?>> eventTypes() {
    return events.stream().<Class<?>>map(Event::getClass).toList();
  }

  private void grid(int[][] ids) throws Exception {
    int height = ids.length, width = ids[0].length;
    ByteBuffer buffer = ByteBuffer.allocate(8 + width * height * 2).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(width).putInt(height);
    for (int[] row : ids) for (int id : row) buffer.putShort((short) id);
    Path path = temporary.resolve("grid.bin.gz");
    try (GZIPOutputStream stream = new GZIPOutputStream(Files.newOutputStream(path))) {
      stream.write(buffer.array());
    }
    ProvinceGrid grid = ProvinceGrid.load(path.toFile());
    when(fixture.ui.plugin.getProvinceGrid()).thenReturn(grid);
  }

  private void remember(Class<?> owner, String name) throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    globals.put(field, field.get(null));
  }
}
