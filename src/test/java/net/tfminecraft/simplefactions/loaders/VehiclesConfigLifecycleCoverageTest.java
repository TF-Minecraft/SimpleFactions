package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;
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

class VehiclesConfigLifecycleCoverageTest {
  @TempDir Path temporary;
  private GuiTestFixture fixture;
  private final Map<Field, Object> globals = new LinkedHashMap<>();
  private Logger logger;

  @BeforeEach
  void setup() throws Exception {
    fixture = new GuiTestFixture();
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
      Field field = VehiclesConfigLoader.class.getDeclaredField(name);
      field.setAccessible(true);
      globals.put(field, field.get(null));
    }
    logger = mock(Logger.class);
    when(Bukkit.getLogger()).thenReturn(logger);
    VehiclesConfigLoader.load(write(valid()).toFile());
  }

  @AfterEach
  void close() throws Exception {
    for (var entry : globals.entrySet()) entry.getKey().set(null, entry.getValue());
    fixture.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unavailableOrMalformedFilesPreserveEveryLiveSetting(boolean malformed) throws Exception {
    Path file = temporary.resolve("bad.yml");
    if (malformed) Files.writeString(file, "categories: [unfinished");

    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> VehiclesConfigLoader.load(file.toFile()));

    assertEquals("Failed to load vehicles.yml", failure.getMessage());
    assertOriginalConfiguration();
    verify(logger).severe("[SimpleFactions] Failed to load vehicles.yml");
  }

  @Test
  void lateTypeValidationFailurePreservesGlobalLimitsMaintenanceAndFeeExclusions()
      throws Exception {
    YamlConfiguration replacement = valid();
    replacement.set("personal-slot-limit", 9);
    replacement.set("default-per-person", 8);
    replacement.set("maintenance-hourly-damage-percent", 45);
    replacement.set("maintenance-min-health-percent", 4);
    replacement.set("maintenance-interval-ticks", 1234);
    replacement.set("fee-excluded-categories", List.of("ships"));
    replacement.set("categories.ships.ironclad.size", 0);
    Path file = write(replacement);

    assertThrows(IllegalStateException.class, () -> VehiclesConfigLoader.load(file.toFile()));

    assertOriginalConfiguration();
  }

  @ParameterizedTest
  @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY})
  void nonFiniteUpkeepIsRejectedForBothDefaultsAndIndividualTypes(double amount) throws Exception {
    for (String key : List.of("default-upkeep", "categories.ships.ironclad.upkeep")) {
      YamlConfiguration invalid = valid();
      if (key.equals("default-upkeep")) invalid.set("categories.ships.ironclad.upkeep", null);
      invalid.set(key, amount);
      Path file = write(invalid);

      assertThrows(IllegalStateException.class, () -> VehiclesConfigLoader.load(file.toFile()));

      assertOriginalConfiguration();
    }
  }

  @Test
  void categoriesDifferingOnlyByCaseCannotSilentlyErasePreviouslyParsedTypes() throws Exception {
    YamlConfiguration invalid = valid();
    invalid.set("categories.SHIPS.raft.upkeep", 1);
    invalid.set("categories.SHIPS.raft.size", 1);
    Path file = write(invalid);

    assertThrows(IllegalStateException.class, () -> VehiclesConfigLoader.load(file.toFile()));

    assertOriginalConfiguration();
    assertFalse(VehiclesConfigLoader.isKnownType("raft"));
  }

  @ParameterizedTest(name = "{0}={1}")
  @MethodSource("invalidFields")
  void invalidSettingsAreRejectedWithoutChangingTheActiveSnapshot(
      String key, Object value, String expected) throws Exception {
    YamlConfiguration invalid = valid();
    invalid.set(key, value);
    Path file = write(invalid);

    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> VehiclesConfigLoader.load(file.toFile()));

    assertTrue(failure.getMessage().contains(expected), failure.getMessage());
    assertOriginalConfiguration();
  }

  private static Stream<Arguments> invalidFields() {
    return Stream.of(
        Arguments.of("personal-slot-limit", -1, "personal-slot-limit"),
        Arguments.of("default-per-person", 0, "default-per-person"),
        Arguments.of("default-upkeep", -1, "default-upkeep"),
        Arguments.of("maintenance-hourly-damage-percent", -1, "maintenance-hourly-damage-percent"),
        Arguments.of("maintenance-hourly-damage-percent", 101, "maintenance-hourly-damage-percent"),
        Arguments.of("maintenance-min-health-percent", -1, "maintenance-min-health-percent"),
        Arguments.of("maintenance-min-health-percent", 101, "maintenance-min-health-percent"),
        Arguments.of("maintenance-interval-ticks", 0, "maintenance-interval-ticks"),
        Arguments.of("upkeep.ironclad", 20, "legacy upkeep block"),
        Arguments.of("categories", null, "missing required categories section"),
        Arguments.of("categories.ships.ironclad.size", null, "size is required"),
        Arguments.of("categories.ships.ironclad.upkeep", null, "upkeep is required"),
        Arguments.of("categories.ships.ironclad.upkeep", -1, "upkeep"),
        Arguments.of("categories.ships.ironclad.size", 0, "size must be > 0"),
        Arguments.of("categories.ships.ironclad.per-person", 0, "per-person must be >= 1"),
        Arguments.of(
            "categories.aircraft.IRONCLAD",
            Map.of("size", 1, "upkeep", 5),
            "duplicate vehicle type id"));
  }

  @Test
  void replacementUsesConfiguredDefaultsAndExposesImmutableCaseInsensitiveCatalogs()
      throws Exception {
    YamlConfiguration replacement = new YamlConfiguration();
    replacement.set("default-upkeep", 7.5);
    replacement.set("categories.SHIPS.show-on-upcoming-battle-icon", true);
    replacement.set("categories.SHIPS.RAFT.size", 1);
    replacement.set("categories.SHIPS.RAFT.show-on-upcoming-battle-icon", false);
    replacement.set("categories.SHIPS.BARGE.size", 2);
    replacement.set("categories.SHIPS.BARGE.upkeep", 0);
    replacement.set("categories.aircraft.display-name", "  ");
    Path file = write(replacement);
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));

      VehiclesConfigLoader.load(file.toFile());

      assertEquals(1, VehiclesConfigLoader.getPersonalSlotLimit());
      assertEquals(1, VehiclesConfigLoader.getDefaultPerPerson());
      assertEquals(20, VehiclesConfigLoader.getMaintenanceHourlyDamagePercent());
      assertEquals(3, VehiclesConfigLoader.getMaintenanceMinHealthPercent());
      assertEquals(72000, VehiclesConfigLoader.getMaintenanceIntervalTicks());
      assertEquals(7.5, VehiclesConfigLoader.getUpkeep("RaFt"));
      assertEquals(1, VehiclesConfigLoader.getPerPersonLimit("RAFT"));
      assertFalse(VehiclesConfigLoader.ignoresPersonalSlotLimit("RAFT"));
      assertFalse(VehiclesConfigLoader.showsOnUpcomingBattleIcon("RAFT"));
      assertTrue(VehiclesConfigLoader.showsOnUpcomingBattleIcon("BARGE"));
      assertEquals("ships", VehiclesConfigLoader.getCategoryId("BARGE").orElseThrow());
      assertNull(VehiclesConfigLoader.getCategoryDisplayName("aircraft"));
      assertFalse(VehiclesConfigLoader.isFeeExcludedCategory("AIRCRAFT"));
      assertThrows(
          UnsupportedOperationException.class, () -> VehiclesConfigLoader.getCategoryIds().clear());
      assertThrows(
          UnsupportedOperationException.class,
          () -> VehiclesConfigLoader.getTypesInCategory("SHIPS").clear());
    } finally {
      Locale.setDefault(previous);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "unknown"})
  void unavailableCatalogEntriesUseDocumentedDefaults(String unknown) {
    for (String id : java.util.Arrays.asList(null, unknown)) {
      assertFalse(VehiclesConfigLoader.isKnownType(id));
      assertFalse(VehiclesConfigLoader.ignoresPersonalSlotLimit(id));
      assertFalse(VehiclesConfigLoader.showsOnUpcomingBattleIcon(id));
      assertEquals(0, VehiclesConfigLoader.getUpkeep(id));
      assertEquals(0, VehiclesConfigLoader.getSize(id));
      assertEquals(3, VehiclesConfigLoader.getPerPersonLimit(id));
      assertTrue(VehiclesConfigLoader.getCategoryId(id).isEmpty());
      assertTrue(VehiclesConfigLoader.getTypesInCategory(id).isEmpty());
      assertNull(VehiclesConfigLoader.getCategoryDisplayName(id));
      assertFalse(VehiclesConfigLoader.isFeeExcludedCategory(id));
    }
  }

  @Test
  void validationStillReportsAnErrorBeforeBukkitExists() throws Exception {
    when(Bukkit.getServer()).thenReturn(null);
    Path file = write(new YamlConfiguration());
    assertThrows(IllegalStateException.class, () -> VehiclesConfigLoader.load(file.toFile()));
    assertOriginalConfiguration();
    verifyNoInteractions(logger);
  }

  private void assertOriginalConfiguration() {
    assertEquals(2, VehiclesConfigLoader.getPersonalSlotLimit());
    assertEquals(3, VehiclesConfigLoader.getDefaultPerPerson());
    assertEquals(15, VehiclesConfigLoader.getMaintenanceHourlyDamagePercent());
    assertEquals(7, VehiclesConfigLoader.getMaintenanceMinHealthPercent());
    assertEquals(90, VehiclesConfigLoader.getMaintenanceIntervalTicks());
    assertEquals(0.15, VehiclesConfigLoader.getMaintenanceHourlyDamageFraction());
    assertEquals(0.07, VehiclesConfigLoader.getMaintenanceMinHealthFraction());
    assertTrue(VehiclesConfigLoader.isFeeExcludedCategory("AIRCRAFT"));
    assertFalse(VehiclesConfigLoader.isFeeExcludedCategory("ships"));
    assertEquals(Set.of("ships", "aircraft"), VehiclesConfigLoader.getCategoryIds());
    assertEquals("Ships", VehiclesConfigLoader.getCategoryDisplayName("SHIPS"));
    assertEquals(20, VehiclesConfigLoader.getUpkeep("IRONCLAD"));
    assertEquals(2, VehiclesConfigLoader.getSize("ironclad"));
    assertEquals(4, VehiclesConfigLoader.getPerPersonLimit("ironclad"));
    assertTrue(VehiclesConfigLoader.ignoresPersonalSlotLimit("ironclad"));
    assertTrue(VehiclesConfigLoader.showsOnUpcomingBattleIcon("ironclad"));
    assertEquals(Set.of("ironclad"), VehiclesConfigLoader.getTypesInCategory("ships").keySet());
  }

  private YamlConfiguration valid() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("personal-slot-limit", 2);
    config.set("default-per-person", 3);
    config.set("maintenance-hourly-damage-percent", 15);
    config.set("maintenance-min-health-percent", 7);
    config.set("maintenance-interval-ticks", 90);
    config.set("fee-excluded-categories", List.of("aircraft"));
    config.set("categories.ships.display-name", "Ships");
    config.set("categories.ships.show-on-upcoming-battle-icon", true);
    config.set("categories.ships.ironclad.upkeep", 20);
    config.set("categories.ships.ironclad.size", 2);
    config.set("categories.ships.ironclad.per-person", 4);
    config.set("categories.ships.ironclad.ignore-limit", true);
    config.createSection("categories.aircraft");
    return config;
  }

  private Path write(YamlConfiguration config) throws Exception {
    Path file = temporary.resolve("vehicles.yml");
    config.save(file.toFile());
    return file;
  }
}
