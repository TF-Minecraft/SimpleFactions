package net.tfminecraft.simplefactions.war.battle.template;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.loaders.BattleTemplateLoader;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.enums.DefenderRespawnMode;
import net.tfminecraft.simplefactions.war.battle.enums.LifeType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class BattleTemplateLifecycleCoverageTest {
  @TempDir Path directory;
  private Map<String, BattleTemplate> previous;
  private String previousWorld;
  private MockedStatic<Bukkit> bukkit;
  private Logger logger;

  @BeforeEach
  void setUp() {
    previous = BattleTemplateLoader.getAll();
    previousWorld = Cache.worldName;
    BattleTemplateLoader.resetForTests();
    Cache.worldName = "template_world";
    logger = mock(Logger.class);
    Server server = mock(Server.class);
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getServer).thenReturn(server);
    bukkit.when(Bukkit::getLogger).thenReturn(logger);
  }

  @AfterEach
  void restore() {
    bukkit.close();
    BattleTemplateLoader.resetForTests();
    previous.values().forEach(BattleTemplateLoader::putForTests);
    Cache.worldName = previousWorld;
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "yaml", "entry"})
  void failedReloadKeepsEveryPreviouslyUsableTemplate(String failure) throws Exception {
    var initial = directory.resolve("initial.yml");
    Files.writeString(initial, "field_default:\n  type: field\n  lives: 17\n");
    BattleTemplateLoader loader = new BattleTemplateLoader();
    loader.load(initial.toFile());
    BattleTemplate original = BattleTemplateLoader.getByName("field_default");
    assertNotNull(original);
    var replacement = directory.resolve("replacement.yml");
    if (failure.equals("yaml")) {
      Files.writeString(replacement, "broken: [unterminated\n");
    } else if (failure.equals("entry")) {
      Files.writeString(
          replacement, "replacement:\n  type: raid\nbroken:\n  type: unknown_battle_kind\n");
    }
    loadAndAssertFailure(loader, replacement);
    assertSame(original, BattleTemplateLoader.getByName("field_default"));
    assertEquals(Map.of("field_default", original), BattleTemplateLoader.getAll());
    assertEquals(17, original.getConfig().getLives());
    verify(logger, atLeastOnce()).warning(contains("Failed to load"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"north", ".NaN", ".inf", "-.inf"})
  void malformedCoordinatesCannotBecomeAUsableSpawn(String x) throws Exception {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.loadFromString("world: template_world\nx: " + x + "\ny: 64\nz: 5\n");
    assertNull(BattleLocation.fromSection(yaml));
  }

  @Test
  void successfulReloadReplacesTheRegistryAndAnEmptyFileClearsIt() throws Exception {
    Path file = directory.resolve("templates.yml");
    Files.writeString(file, "Field_Default:\n  type: field\n  lives: 17\n");
    BattleTemplateLoader loader = new BattleTemplateLoader();
    loader.load(file.toFile());
    BattleTemplate first = BattleTemplateLoader.getByName("Field_Default");
    assertSame(first, BattleTemplateLoader.getByName("field_default"));
    assertNull(BattleTemplateLoader.getByName(null));
    assertNull(BattleTemplateLoader.getByName("missing"));
    loader.load(null);
    assertSame(first, BattleTemplateLoader.getByName("Field_Default"));
    var snapshot = BattleTemplateLoader.getAll();
    assertThrows(UnsupportedOperationException.class, snapshot::clear);
    Files.writeString(file, "replacement:\n  type: raid\n");
    loader.load(file.toFile());
    assertNull(BattleTemplateLoader.getByName("Field_Default"));
    assertEquals(BattleType.RAID, BattleTemplateLoader.getByName("replacement").getType());
    assertSame(first, snapshot.get("Field_Default"));
    Files.writeString(file, "# intentionally no templates\n");
    loader.load(file.toFile());
    assertTrue(BattleTemplateLoader.getAll().isEmpty());
    verify(logger).info(contains("Loaded 0"));
  }

  @Test
  void aHeadlessParseFailureRetainsThePreviouslyLoadedDefinitions() throws Exception {
    bukkit.when(Bukkit::getServer).thenReturn(null);
    Path file = directory.resolve("headless.yml");
    Files.writeString(file, "ready:\n  type: siege\n");
    BattleTemplateLoader loader = new BattleTemplateLoader();
    loader.load(file.toFile());
    BattleTemplate original = BattleTemplateLoader.getByName("ready");
    Files.writeString(file, "invalid: [broken\n");
    loadAndAssertFailure(loader, file);
    assertSame(original, BattleTemplateLoader.getByName("ready"));
    Files.writeString(file, "invalid: scalar\n");
    loadAndAssertFailure(loader, file);
    assertSame(original, BattleTemplateLoader.getByName("ready"));
    verifyNoInteractions(logger);
  }

  @Test
  void locationsResolveCurrentWorldFallbackAndPreserveCoordinatesAndRotation() throws Exception {
    World world = world("template_world");
    var yaml = yaml("world: ' '\nx: 12.5\ny: 64\nz: -8\nyaw: 90.25\npitch: -12.5\n");
    BattleLocation parsed = BattleLocation.fromSection(yaml);
    Location expected = new Location(world, 12.5, 64, -8, 90.25f, -12.5f);
    assertEquals(expected, parsed.toBukkitLocation());
    assertEquals("template_world", parsed.getWorld());
    assertEquals(expected, BattleLocation.fromBukkitLocation(expected).toBukkitLocation());
    assertEquals(
        new Location(world, 1, 2, 3),
        BattleLocation.fromMap(Map.of("x", 1, "y", 2L, "z", 3.0)).toBukkitLocation());
    var explicit =
        BattleLocation.fromMap(
            Map.of("world", "template_world", "x", 4, "y", 5, "z", 6, "yaw", 45, "pitch", -30));
    assertEquals(new Location(world, 4, 5, 6, 45, -30), explicit.toBukkitLocation());
    var unavailable = new BattleLocation("unloaded_world", 2, 3, 4, 0, 0);
    assertNull(unavailable.toBukkitLocation());
    assertEquals("unloaded_world", unavailable.getWorld());
  }

  @Test
  void missingCoordinateShapesDoNotProduceAnOriginSpawn() throws Exception {
    assertNull(BattleLocation.fromSection(null));
    assertNull(BattleLocation.fromMap(null));
    assertNull(BattleLocation.fromBukkitLocation(null));
    assertNull(BattleLocation.fromBukkitLocation(new Location(null, 1, 2, 3)));
    assertNull(BattleLocation.fromSection(yaml("x: 2\ny: 5\n")));
    assertNull(BattleLocation.fromMap(Map.of("x", 1, "y", 2)));
    assertNull(BattleLocation.fromMap(Map.of("x", "east", "y", 2, "z", 3)));
    assertNull(TemplateSideConfig.fromSection(null));
    assertNull(CapturePointDefinition.fromSection(null));
    assertNull(CapturePointDefinition.fromMap(null));
    assertNull(CapturePointDefinition.fromMap(Map.of("id", 123, "x", 1, "y", 2, "z", 3)));
    assertNull(CapturePointDefinition.fromMap(Map.of("id", "missing_location")));
    assertNull(CapturePointDefinition.fromSection(yaml("id: missing_location\n")));
    assertNull(CapturePointDefinition.fromSection(yaml("x: 1\ny: 2\nz: 3\n")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"x", "y", "z", "yaw", "pitch"})
  void nonfiniteMapValuesAreRejectedForEveryCoordinateAndRotation(String field) {
    var values = new java.util.HashMap<String, Object>();
    values.putAll(Map.of("x", 1, "y", 2, "z", 3, "yaw", 4, "pitch", 5));
    for (double invalid :
        new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      values.put(field, invalid);
      assertNull(BattleLocation.fromMap(values), field + " must be finite");
    }
  }

  @Test
  void editableLocationAndPointDtosSurviveJsonWithoutLosingGeometry() {
    World world = world("edited_world");
    BattleLocation edited = new BattleLocation();
    edited.setWorld("edited_world");
    edited.setX(10.25);
    edited.setY(80);
    edited.setZ(-15.5);
    edited.setYaw(37.5f);
    edited.setPitch(-12.25f);
    CapturePointDefinition point = new CapturePointDefinition();
    point.setId("tower");
    point.setLocation(edited);
    Gson gson = new Gson();
    CapturePointDefinition restored =
        gson.fromJson(gson.toJson(point), CapturePointDefinition.class);
    assertEquals("tower", restored.getId());
    assertEquals(
        new Location(world, 10.25, 80, -15.5, 37.5f, -12.25f),
        restored.getLocation().toBukkitLocation());
    assertEquals(10.25, restored.getLocation().getX());
    assertEquals(80, restored.getLocation().getY());
    assertEquals(-15.5, restored.getLocation().getZ());
    assertEquals(37.5f, restored.getLocation().getYaw());
    assertEquals(-12.25f, restored.getLocation().getPitch());
    TemplateSideConfig side = new TemplateSideConfig(edited, edited);
    side.setSpawn(new BattleLocation("edited_world", 100, 64, 1, 0, 0));
    side.setJail(new BattleLocation("edited_world", 200, 32, 2, 180, 10));
    TemplateSideConfig sideRestored = gson.fromJson(gson.toJson(side), TemplateSideConfig.class);
    assertEquals(new Location(world, 100, 64, 1), sideRestored.getSpawn().toBukkitLocation());
    assertEquals(
        new Location(world, 200, 32, 2, 180, 10), sideRestored.getJail().toBukkitLocation());
    edited.setWorld(null);
    World fallback = world("template_world");
    assertSame(fallback, edited.toBukkitLocation().getWorld());
    assertEquals("edited_world", restored.getLocation().getWorld());
  }

  @Test
  void currentAndLegacySideAndCaptureLayoutsResolveTheSameRealLocations() throws Exception {
    World world = world("template_world");
    TemplateSideConfig current =
        TemplateSideConfig.fromSection(
            yaml("spawn: {x: 1, y: 64, z: 2}\njail: {x: 3, y: 32, z: 4}\n"));
    TemplateSideConfig legacy =
        TemplateSideConfig.fromSection(yaml("spawn: true\nx: 1\ny: 64\nz: 2\n"));
    assertEquals(new Location(world, 1, 64, 2), current.getSpawn().toBukkitLocation());
    assertEquals(current.getSpawn().toBukkitLocation(), legacy.getSpawn().toBukkitLocation());
    assertEquals(new Location(world, 3, 32, 4), current.getJail().toBukkitLocation());
    assertNull(legacy.getJail());
    CapturePointDefinition nested =
        CapturePointDefinition.fromMap(
            Map.of("id", "bridge", "location", Map.of("x", 8, "y", 65, "z", 9)));
    CapturePointDefinition flat =
        CapturePointDefinition.fromMap(Map.of("id", "bridge", "x", 8, "y", 65, "z", 9));
    CapturePointDefinition section =
        CapturePointDefinition.fromSection(yaml("id: bridge\nx: 8\ny: 65\nz: 9\n"));
    assertEquals("bridge", nested.getId());
    assertEquals(new Location(world, 8, 65, 9), nested.getLocation().toBukkitLocation());
    assertEquals(nested.getLocation().toBukkitLocation(), flat.getLocation().toBukkitLocation());
    assertEquals(nested.getLocation().toBukkitLocation(), section.getLocation().toBukkitLocation());
  }

  @Test
  void completeYamlConfigurationAppliesSettingsAndRetainsDeclaredGeometry() throws Exception {
    World world = world("template_world");
    Path file = directory.resolve("complete.yml");
    Files.writeString(
        file,
        """
        configured:
          type: raid
          friendly_fire: false
          keep_inventory: false
          loot_enabled: false
          life_type: COLLECTIVE
          lives: 43
          attacker:
            spawn: {x: 1, y: 64, z: 2}
            jail: {x: 3, y: 64, z: 4}
          defender:
            spawn: {x: 5, y: 64, z: 6}
          capture_points:
            - id: outer
              location: {x: 8, y: 64, z: 9}
            - id: inner
              x: 10
              y: 64
              z: 11
            - id: missing_position
            - unusable_scalar
          contest_area:
            min: {x: 0, y: 60, z: 0}
            max: {x: 20, y: 70, z: 20}
          contest_duration_seconds: 75
          naval_variant: true
          naval_spawn:
            spawn: {x: 30, y: 63, z: 40}
          defender_respawn_mode: lives
          defender_lives: 11
          capture_points_enabled: true
          campaign_raid: true
          raid_target:
            id: harbour
            location: {x: 15, y: 64, z: 15}
        """);
    new BattleTemplateLoader().load(file.toFile());
    BattleModeTemplate mode = BattleTemplateService.getInstance().getModeConfig("configured");
    assertEquals(
        List.of("outer", "inner"),
        mode.getCapturePoints().stream().map(CapturePointDefinition::getId).toList());
    assertEquals(new Location(world, 1, 64, 2), mode.getAttacker().getSpawn().toBukkitLocation());
    assertEquals(new Location(world, 5, 64, 6), mode.getDefender().getSpawn().toBukkitLocation());
    assertTrue(mode.getContestArea().contains(new Location(world, 10, 64, 10)));
    assertFalse(mode.getContestArea().contains(new Location(world, 21, 64, 10)));
    assertEquals(75, mode.getContestDurationSeconds());
    assertEquals(
        new Location(world, 30, 63, 40), mode.getNavalSpawn().getSpawn().toBukkitLocation());
    assertEquals("harbour", mode.getRaidTarget().getId());
    var battle = BattleFactory.createBlank(BattleType.RAID, "configured_raid");
    BattleFactory.applyTemplate(battle, "configured");
    assertFalse(battle.hasFriendlyFire());
    assertFalse(battle.hasKeepInventory());
    assertFalse(battle.hasLootEnabled());
    assertTrue(battle.isCampaignRaid());
    assertTrue(battle.isCapturePointsEnabled());
    assertEquals(LifeType.COLLECTIVE, battle.getLifeType());
    assertEquals(43, battle.getLives());
    assertEquals(DefenderRespawnMode.LIVES, battle.getDefenderRespawnMode());
    assertEquals(11, battle.getDefenderLives());
    assertNotNull(battle.getSideById(BattleTemplate.ATTACKER_SIDE));
    assertNotNull(battle.getSideById(BattleTemplate.DEFENDER_SIDE));
  }

  @Test
  void programmaticSectionsAndMalformedCaptureListsPreserveOnlyUsableDefinitions()
      throws Exception {
    YamlConfiguration configuration = yaml("capture_points: not_a_list\n");
    assertTrue(BattleModeTemplate.fromSection(configuration).getCapturePoints().isEmpty());
    var valid = yaml("id: valid\nlocation: {x: 1, y: 2, z: 3}\n");
    var invalid = yaml("id: incomplete\n");
    configuration.set("capture_points", new ArrayList<>(List.of(valid, invalid)));
    BattleModeTemplate mode = BattleModeTemplate.fromSection(configuration);
    assertEquals(
        List.of("valid"),
        mode.getCapturePoints().stream().map(CapturePointDefinition::getId).toList());
  }

  private World world(String name) {
    World world = mock(World.class);
    when(world.getName()).thenReturn(name);
    bukkit.when(() -> Bukkit.getWorld(name)).thenReturn(world);
    return world;
  }

  private static YamlConfiguration yaml(String contents) throws Exception {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.loadFromString(contents);
    return yaml;
  }

  private static void loadAndAssertFailure(BattleTemplateLoader loader, Path file) {
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> loader.load(file.toFile()));
    assertTrue(failure.getMessage().contains(file.toString()));
    assertNotNull(failure.getCause());
    assertNotNull(failure.getCause().getMessage());
    assertFalse(failure.getCause().getMessage().isBlank());
  }
}
