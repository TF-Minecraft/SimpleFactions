package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VehiclesConfigLoaderTest {
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-vehicles-config-");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void load_readsSlotLimitCategoryUpkeepAndSize() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);

        VehiclesConfigLoader.load(vehiclesYaml.toFile());

        assertEquals(1, VehiclesConfigLoader.getPersonalSlotLimit());
        assertEquals(20, VehiclesConfigLoader.getMaintenanceHourlyDamagePercent());
        assertEquals(3, VehiclesConfigLoader.getMaintenanceMinHealthPercent());
        assertEquals(72000L, VehiclesConfigLoader.getMaintenanceIntervalTicks());
        assertEquals(1, VehiclesConfigLoader.getDefaultPerPerson());
        assertEquals(20.0, VehiclesConfigLoader.getUpkeep("ironclad"));
        assertEquals(0.0, VehiclesConfigLoader.getUpkeep("unknown"));
        assertTrue(VehiclesConfigLoader.getCategoryId("ironclad").isPresent());
        assertEquals("ships", VehiclesConfigLoader.getCategoryId("ironclad").get());
        assertEquals(1, VehiclesConfigLoader.getSize("ironclad"));
        assertEquals(1, VehiclesConfigLoader.getPerPersonLimit("ironclad"));
        assertFalse(VehiclesConfigLoader.ignoresPersonalSlotLimit("ironclad"));
        assertTrue(VehiclesConfigLoader.getCategoryIds().contains("ships"));
        assertEquals(1, VehiclesConfigLoader.getTypesInCategory("ships").size());
    }

    @Test
    void load_readsUpcomingBattleIconFromCategory() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1
            default-upkeep: 4

            categories:
              ships:
                show-on-upcoming-battle-icon: true
                ironclad:
                  size: 1
              land_vehicles: {}
              train: {}
              static_emplacements: {}
              aircraft: {}
            """);

        VehiclesConfigLoader.load(vehiclesYaml.toFile());

        assertTrue(VehiclesConfigLoader.showsOnUpcomingBattleIcon("ironclad"));
        assertFalse(VehiclesConfigLoader.showsOnUpcomingBattleIcon("unknown"));
    }

    @Test
    void load_readsDefaultPerPersonAndPerTypeOverrides() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 3
            default-per-person: 1

            categories:
              land_vehicles:
                horse_cart:
                  upkeep: 5
                  size: 1
                  per-person: 3
                small_car:
                  upkeep: 8
                  size: 1
              ships: {}
              static_emplacements: {}
              aircraft: {}
            """);

        VehiclesConfigLoader.load(vehiclesYaml.toFile());

        assertEquals(3, VehiclesConfigLoader.getPersonalSlotLimit());
        assertEquals(1, VehiclesConfigLoader.getDefaultPerPerson());
        assertEquals(3, VehiclesConfigLoader.getPerPersonLimit("horse_cart"));
        assertEquals(1, VehiclesConfigLoader.getPerPersonLimit("small_car"));
    }

    @Test
    void load_readsIgnoreLimit() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 3
            default-per-person: 1

            categories:
              train:
                coal_car:
                  upkeep: 1
                  size: 1
                  ignore-limit: true
                  per-person: 3
              ships: {}
              static_emplacements: {}
              aircraft: {}
            """);

        VehiclesConfigLoader.load(vehiclesYaml.toFile());

        assertTrue(VehiclesConfigLoader.ignoresPersonalSlotLimit("coal_car"));
        assertEquals(3, VehiclesConfigLoader.getPerPersonLimit("coal_car"));
    }

    @Test
    void load_defaultIgnoreLimitFalse() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);

        VehiclesConfigLoader.load(vehiclesYaml.toFile());

        assertFalse(VehiclesConfigLoader.ignoresPersonalSlotLimit("ironclad"));
    }

    @Test
    void load_readsDefaultUpkeepFallback() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1
            default-upkeep: 4

            categories:
              ships:
                foo:
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);

        VehiclesConfigLoader.load(vehiclesYaml.toFile());

        assertEquals(4.0, VehiclesConfigLoader.getUpkeep("foo"));
    }

    @Test
    void load_rejectsMissingUpkeepWithoutDefault() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1

            categories:
              ships:
                foo:
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);

        assertThrows(IllegalStateException.class, () -> VehiclesConfigLoader.load(vehiclesYaml.toFile()));
    }

    @Test
    void load_rejectsInvalidDefaultPerPerson() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1
            default-per-person: 0

            categories:
              ships: {}
              static_emplacements: {}
              aircraft: {}
            """);

        assertThrows(IllegalStateException.class, () -> VehiclesConfigLoader.load(vehiclesYaml.toFile()));
    }

    @Test
    void load_rejectsInvalidPerPerson() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
                  per-person: 0
              static_emplacements: {}
              aircraft: {}
            """);

        assertThrows(IllegalStateException.class, () -> VehiclesConfigLoader.load(vehiclesYaml.toFile()));
    }

    @Test
    void isKnownType_distinguishesKnownAndUnknown() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);

        VehiclesConfigLoader.load(vehiclesYaml.toFile());

        assertTrue(VehiclesConfigLoader.isKnownType("ironclad"));
        assertFalse(VehiclesConfigLoader.isKnownType("unknown"));
        assertEquals(1, VehiclesConfigLoader.getPerPersonLimit("unknown"));
        assertFalse(VehiclesConfigLoader.ignoresPersonalSlotLimit("unknown"));
    }

    @Test
    void load_shippedFixtureValues() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 3
            default-upkeep: 4
            default-per-person: 1

            categories:
              land_vehicles:
                horse_cart:
                  upkeep: 5
                  size: 1
                  per-person: 3
              train:
                coal_car:
                  upkeep: 1
                  size: 1
                  ignore-limit: true
                  per-person: 3
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);

        VehiclesConfigLoader.load(vehiclesYaml.toFile());

        assertEquals(3, VehiclesConfigLoader.getPersonalSlotLimit());
        assertEquals(3, VehiclesConfigLoader.getPerPersonLimit("horse_cart"));
        assertTrue(VehiclesConfigLoader.ignoresPersonalSlotLimit("coal_car"));
        assertEquals(1, VehiclesConfigLoader.getPerPersonLimit("ironclad"));
    }

    @Test
    void load_rejectsLegacyUpkeepBlock() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1

            upkeep:
              ironclad: 20
            """);

        assertThrows(IllegalStateException.class, () -> VehiclesConfigLoader.load(vehiclesYaml.toFile()));
    }
}
