package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.InstallationKindConfig;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class InstallationConfigLifecycleCoverageTest {
  @TempDir Path temporary;
  private GuiTestFixture fixture;
  private final Map<Field, Object> globals = new LinkedHashMap<>();
  private final Map<InstallationKind, InstallationKindConfig> previousKinds =
      new EnumMap<>(InstallationKind.class);
  private Locale previousLocale;
  private Logger logger;

  @BeforeEach
  void setup() throws Exception {
    fixture = new GuiTestFixture();
    previousLocale = Locale.getDefault();
    Locale.setDefault(Locale.ROOT);
    previousKinds.putAll(InstallationConfigLoader.getAll());
    kinds().clear();
    remember(InstallationConfigLoader.class, "consentProximityBlocks");
    remember(InstallationConfigLoader.class, "transferRequestTimeoutSeconds");
    for (String name :
        List.of(
            "personalSlotLimit",
            "defaultPerPerson",
            "maintenanceHourlyDamagePercent",
            "maintenanceMinHealthPercent",
            "maintenanceIntervalTicks",
            "categoryIds",
            "typesByCategory",
            "categoryByVehicleTypeId",
            "categoryDisplayNames",
            "feeExcludedCategories")) {
      remember(VehiclesConfigLoader.class, name);
    }
    logger = mock(Logger.class);
    when(Bukkit.getLogger()).thenReturn(logger);
    YamlConfiguration vehicles = new YamlConfiguration();
    for (String category :
        List.of("static_emplacements", "aircraft", "ships", "land_vehicles", "train")) {
      vehicles.createSection("categories." + category);
    }
    Path path = temporary.resolve("vehicles.yml");
    vehicles.save(path.toFile());
    VehiclesConfigLoader.load(path.toFile());
  }

  @AfterEach
  void close() throws Exception {
    kinds().clear();
    kinds().putAll(previousKinds);
    for (var entry : globals.entrySet()) entry.getKey().set(null, entry.getValue());
    Locale.setDefault(previousLocale);
    fixture.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedReadPreservesTheWholePreviouslyLoadedConfiguration(boolean malformed)
      throws Exception {
    InstallationConfigLoader.load(write(valid()).toFile());
    Map<InstallationKind, InstallationKindConfig> before =
        new EnumMap<>(InstallationConfigLoader.getAll());
    Path bad = temporary.resolve("bad.yml");
    if (malformed) Files.writeString(bad, "fort: [unterminated");

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class, () -> InstallationConfigLoader.load(bad.toFile()));

    assertEquals("Failed to load installations.yml", error.getMessage());
    assertEquals(before, InstallationConfigLoader.getAll());
    assertEquals(7, InstallationConfigLoader.getConsentProximityBlocks());
    assertEquals(33, InstallationConfigLoader.getTransferRequestTimeoutSeconds());
    verify(logger).severe("[SimpleFactions] Failed to load installations.yml");
  }

  @Test
  void lateValidationFailureDoesNotPartiallyReplaceKindsOrGlobalSettings() throws Exception {
    InstallationConfigLoader.load(write(valid()).toFile());
    Map<InstallationKind, InstallationKindConfig> before =
        new EnumMap<>(InstallationConfigLoader.getAll());
    YamlConfiguration replacement = valid();
    replacement.set("consent-proximity-blocks", 91);
    replacement.set("transfer-request-timeout-seconds", 99);
    replacement.set("fort.daily-upkeep", 500);
    replacement.set("airport.radius", null);
    Path invalid = write(replacement);

    assertThrows(
        IllegalStateException.class, () -> InstallationConfigLoader.load(invalid.toFile()));

    assertEquals(before, InstallationConfigLoader.getAll());
    assertEquals(7, InstallationConfigLoader.getConsentProximityBlocks());
    assertEquals(33, InstallationConfigLoader.getTransferRequestTimeoutSeconds());
  }

  @Test
  void categoryIdentifiersRemainCaseInsensitiveUnderTurkishLocale() throws Exception {
    YamlConfiguration config = valid();
    config.set("airport.slots", null);
    config.set("airport.slots.AIRCRAFT", 3);
    Path path = write(config);
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));

    InstallationConfigLoader.load(path.toFile());

    assertEquals(
        3, InstallationConfigLoader.getCategorySlotCapacity(InstallationKind.AIRPORT, "AIRCRAFT"));
    assertEquals(
        Map.of("aircraft", 3), InstallationConfigLoader.getCategorySlots(InstallationKind.AIRPORT));
  }

  @ParameterizedTest
  @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY})
  void upkeepMustBeFiniteBeforeItCanReachGuildBalances(double amount) throws Exception {
    InstallationConfigLoader.load(write(valid()).toFile());
    Map<InstallationKind, InstallationKindConfig> before =
        new EnumMap<>(InstallationConfigLoader.getAll());
    for (String key : List.of("fort.daily-upkeep", "fort.levels.2.daily-upkeep")) {
      YamlConfiguration invalid = valid();
      invalid.set(key, amount);
      Path path = write(invalid);

      assertThrows(IllegalStateException.class, () -> InstallationConfigLoader.load(path.toFile()));

      assertEquals(before, InstallationConfigLoader.getAll());
      assertEquals(4, InstallationConfigLoader.getDailyUpkeep(InstallationKind.FORT));
      assertEquals(9, InstallationConfigLoader.getDailyUpkeep(InstallationKind.FORT, 2));
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidConfigurations")
  void invalidFieldsAreRejectedWithoutPublishingPartialSettings(
      String key, Object value, String diagnostic) throws Exception {
    InstallationConfigLoader.load(write(valid()).toFile());
    Map<InstallationKind, InstallationKindConfig> before =
        new EnumMap<>(InstallationConfigLoader.getAll());
    YamlConfiguration invalid = valid();
    invalid.set(key, value);
    Path path = write(invalid);

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class, () -> InstallationConfigLoader.load(path.toFile()));

    assertTrue(error.getMessage().contains(diagnostic), error.getMessage());
    assertEquals(before, InstallationConfigLoader.getAll());
    assertEquals(7, InstallationConfigLoader.getConsentProximityBlocks());
    assertEquals(33, InstallationConfigLoader.getTransferRequestTimeoutSeconds());
    verify(logger).severe("[SimpleFactions] " + error.getMessage());
  }

  private static Stream<Arguments> invalidConfigurations() {
    return Stream.of(
        Arguments.of("consent-proximity-blocks", null, "consent-proximity-blocks is required"),
        Arguments.of(
            "transfer-request-timeout-seconds",
            null,
            "transfer-request-timeout-seconds is required"),
        Arguments.of("consent-proximity-blocks", -1, "consent-proximity-blocks must be >= 0"),
        Arguments.of(
            "transfer-request-timeout-seconds", 0, "transfer-request-timeout-seconds must be > 0"),
        Arguments.of("port", null, "missing required section: port"),
        Arguments.of("fort.daily-upkeep", null, "fort.daily-upkeep is required"),
        Arguments.of("fort.construction-time", null, "fort.construction-time is required"),
        Arguments.of("fort.radius", null, "fort.radius is required"),
        Arguments.of("fort.daily-upkeep", -1, "fort.daily-upkeep"),
        Arguments.of("fort.construction-time", 0, "fort.construction-time must be > 0"),
        Arguments.of("fort.radius", 0, "fort.radius must be > 0"),
        Arguments.of("fort.slots", null, "fort.slots is required"),
        Arguments.of("fort.slots.unknown", 1, "references unknown vehicle category"),
        Arguments.of("fort.slots.static_emplacements", -1, "must be >= 0"),
        Arguments.of("fort.levels", "invalid", "fort.levels must be a section"),
        Arguments.of("fort.levels.two.daily-upkeep", 1, "must be a level number >= 2"),
        Arguments.of("fort.levels.1.daily-upkeep", 1, "levels must start at 2 with no gaps"),
        Arguments.of("fort.levels.02.daily-upkeep", 1, "levels must start at 2 with no gaps"),
        Arguments.of("fort.levels.4.daily-upkeep", 1, "levels must start at 2 with no gaps"),
        Arguments.of("fort.levels.2", "invalid", "levels must start at 2 with no gaps"),
        Arguments.of("fort.levels.2.daily-upkeep", null, "fort.levels.2.daily-upkeep is required"),
        Arguments.of(
            "fort.levels.2.construction-time", null, "fort.levels.2.construction-time is required"),
        Arguments.of("fort.levels.2.daily-upkeep", -1, "fort.levels.2.daily-upkeep"),
        Arguments.of(
            "fort.levels.2.construction-time", 0, "fort.levels.2.construction-time must be > 0"),
        Arguments.of("fort.levels.2.slots", null, "fort.levels.2.slots is required"));
  }

  @Test
  void aCompleteReplacementPublishesReadOnlyLevelsAndCompatibleStationDefaults() throws Exception {
    InstallationConfigLoader.load(write(valid()).toFile());
    YamlConfiguration replacement = valid();
    replacement.set("consent-proximity-blocks", 0);
    replacement.set("transfer-request-timeout-seconds", 1);
    replacement.set("fort.daily-upkeep", 0);
    replacement.set("fort.slots.static_emplacements", 0);
    replacement.set("fort.levels.3.daily-upkeep", 15);
    replacement.set("fort.levels.3.construction-time", 60);
    replacement.set("fort.levels.3.slots.static_emplacements", 5);
    replacement.set("train_station", null);

    InstallationConfigLoader.load(write(replacement).toFile());

    assertEquals(0, InstallationConfigLoader.getConsentProximityBlocks());
    assertEquals(1, InstallationConfigLoader.getTransferRequestTimeoutSeconds());
    assertEquals(4, InstallationConfigLoader.getAll().size());
    assertThrows(
        UnsupportedOperationException.class, () -> InstallationConfigLoader.getAll().clear());
    InstallationKindConfig fort = InstallationConfigLoader.getAll().get(InstallationKind.FORT);
    assertEquals(3, fort.getMaximumLevel());
    assertEquals(0, fort.getDailyUpkeep());
    assertEquals(15, fort.getDailyUpkeep(100));
    assertEquals(10, fort.getConstructionTimeSeconds());
    assertEquals(60, fort.getConstructionTimeSeconds(100));
    assertEquals(20, fort.getRadius());
    assertEquals(Map.of("static_emplacements", 0), fort.getCategorySlots());
    assertEquals(0, fort.getLevel(-1).dailyUpkeep());
    assertEquals(5, fort.getCategorySlots(3).get("static_emplacements"));
    assertThrows(UnsupportedOperationException.class, () -> fort.getLevels().clear());
    assertThrows(UnsupportedOperationException.class, () -> fort.getCategorySlots().clear());
    assertEquals(0, InstallationConfigLoader.getCategorySlotCapacity(InstallationKind.FORT, null));
    assertEquals(0, InstallationConfigLoader.getCategorySlotCapacity(InstallationKind.FORT, ""));
    assertEquals(
        0, InstallationConfigLoader.getCategorySlotCapacity(InstallationKind.FORT, "aircraft"));
    assertEquals(3, InstallationConfigLoader.getMaximumLevel(InstallationKind.TRAIN_STATION));
    assertEquals(80, InstallationConfigLoader.getRadius(InstallationKind.TRAIN_STATION));
    assertEquals(100, InstallationConfigLoader.getDailyUpkeep(InstallationKind.TRAIN_STATION, 3));
    assertEquals(
        432000,
        InstallationConfigLoader.getConstructionTimeSeconds(InstallationKind.TRAIN_STATION, 3));
    assertEquals(
        4,
        InstallationConfigLoader.getCategorySlotCapacity(
            InstallationKind.TRAIN_STATION, 3, "STATIC_EMPLACEMENTS"));
  }

  @Test
  void anUnconfiguredColdStartReportsMissingConfigurationEvenWithoutBukkit() throws Exception {
    assertTrue(InstallationConfigLoader.getAll().isEmpty());
    IllegalStateException missing =
        assertThrows(
            IllegalStateException.class,
            () -> InstallationConfigLoader.getRadius(InstallationKind.FORT));
    assertEquals("Installation config not loaded for kind fort", missing.getMessage());
    when(Bukkit.getServer()).thenReturn(null);
    Path file = write(new YamlConfiguration());
    assertThrows(IllegalStateException.class, () -> InstallationConfigLoader.load(file.toFile()));
    assertTrue(InstallationConfigLoader.getAll().isEmpty());
    verifyNoInteractions(logger);
  }

  private YamlConfiguration valid() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("consent-proximity-blocks", 7);
    config.set("transfer-request-timeout-seconds", 33);
    for (InstallationKind kind : InstallationKind.values()) {
      String key = kind.getCommandName();
      config.set(key + ".daily-upkeep", 4);
      config.set(key + ".construction-time", 10);
      config.set(key + ".radius", 20);
      config.set(key + ".slots.static_emplacements", 2);
      config.set(key + ".levels.2.daily-upkeep", 9);
      config.set(key + ".levels.2.construction-time", 30);
      config.set(key + ".levels.2.slots.static_emplacements", 3);
    }
    return config;
  }

  private Path write(YamlConfiguration config) throws Exception {
    Path path = temporary.resolve("installations.yml");
    config.save(path.toFile());
    return path;
  }

  private void remember(Class<?> type, String name) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    globals.put(field, field.get(null));
  }

  @SuppressWarnings("unchecked")
  private Map<InstallationKind, InstallationKindConfig> kinds() throws Exception {
    Field field = InstallationConfigLoader.class.getDeclaredField("byKind");
    field.setAccessible(true);
    return (Map<InstallationKind, InstallationKindConfig>) field.get(null);
  }
}
