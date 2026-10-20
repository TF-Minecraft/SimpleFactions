package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.tfminecraft.simplefactions.vehicles.maintenance.UnpaidVehicleEffects;

class VehiclesConfigUnpaidEffectsTest {
    private static final String CATEGORIES = """
            categories:
              ships:
                sloop:
                  upkeep: 8
                  size: 1
                ironclad:
                  upkeep: 20
                  size: 1
              aircraft:
                cloudskimmer:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
            """;

    @TempDir Path temporary;

    @Test
    void typesOverrideTheirCategoryWhichOverridesTheDefault() throws Exception {
        load("""
                maintenance-hourly-damage-percent: 50
                unpaid-effects:
                  default:
                    min-health-percent: 5
                    part-min-health-percent:
                      Engine: 10
                  categories:
                    SHIPS:
                      hourly-damage-percent: 12.5
                      keep-afloat: false
                      part-min-health-percent:
                        hull: 40
                  types:
                    Sloop:
                      keep-aloft: false
                      block-repair: false
                      part-min-health-percent:
                        HULL: 60
                        front_gun: 0
                """);

        UnpaidVehicleEffects fallback = new UnpaidVehicleEffects(50, 5, Map.of("engine", 10.0), true, true, true);
        assertEquals(fallback, VehiclesConfigLoader.getUnpaidEffects(null));
        assertEquals(fallback, VehiclesConfigLoader.getUnpaidEffects("unknown"));
        assertEquals(fallback, VehiclesConfigLoader.getUnpaidEffects("CloudSkimmer"));
        assertEquals(
                new UnpaidVehicleEffects(12.5, 5, Map.of("engine", 10.0, "hull", 40.0), false, true, true),
                VehiclesConfigLoader.getUnpaidEffects("ironclad"));
        assertEquals(
                new UnpaidVehicleEffects(12.5, 5, Map.of("engine", 10.0, "hull", 60.0, "front_gun", 0.0),
                        false, false, false),
                VehiclesConfigLoader.getUnpaidEffects("SLOOP"));
    }

    @Test
    void withoutTheSectionEveryTypeUsesTheOldTopLevelKeys() throws Exception {
        load("""
                maintenance-hourly-damage-percent: 15
                maintenance-min-health-percent: 7
                """);

        assertEquals(new UnpaidVehicleEffects(15, 7, Map.of(), true, true, true),
                VehiclesConfigLoader.getUnpaidEffects("sloop"));
    }

    @Test
    void emptyGroupsKeepTheDefault() throws Exception {
        load("""
                unpaid-effects:
                  categories: {}
                  types: {}
                """);

        assertEquals(UnpaidVehicleEffects.DEFAULT, VehiclesConfigLoader.getUnpaidEffects("sloop"));
    }

    @Test
    void effectsDecayOnlyWithAnHourlyDamageAndKeepTheirOwnPartMap() {
        assertTrue(UnpaidVehicleEffects.DEFAULT.decays());
        assertFalse(new UnpaidVehicleEffects(0, 3, null, true, true, true).decays());
        assertEquals(Map.of(), new UnpaidVehicleEffects(0, 3, null, true, true, true).partMinHealthPercent());
    }

    @ParameterizedTest
    @MethodSource("invalidSections")
    void invalidSectionsFailTheLoadAndKeepThePreviousEffects(String unpaid, String message) throws Exception {
        load("""
                unpaid-effects:
                  types:
                    sloop:
                      min-health-percent: 70
                """);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> load(unpaid));

        assertTrue(failure.getMessage().contains(message), failure.getMessage());
        assertEquals(70, VehiclesConfigLoader.getUnpaidEffects("sloop").minHealthPercent());
    }

    private static Stream<Arguments> invalidSections() {
        return Stream.of(
                Arguments.of("unpaid-effects: 5", "unpaid-effects must be a section"),
                Arguments.of("unpaid-effects:\n  defaults: {}", "unpaid-effects.defaults is not default"),
                Arguments.of("unpaid-effects:\n  default: true", "unpaid-effects.default must be a section"),
                Arguments.of("unpaid-effects:\n  categories: []", "unpaid-effects.categories must be a section"),
                Arguments.of("unpaid-effects:\n  default:\n    keep-afloats: true",
                        "unpaid-effects.default.keep-afloats is not an unpaid effect"),
                Arguments.of("unpaid-effects:\n  default:\n    hourly-damage-percent: 101",
                        "hourly-damage-percent must be a number between 0 and 100"),
                Arguments.of("unpaid-effects:\n  default:\n    min-health-percent: -1",
                        "min-health-percent must be a number between 0 and 100"),
                Arguments.of("unpaid-effects:\n  default:\n    min-health-percent: lots",
                        "min-health-percent must be a number between 0 and 100"),
                Arguments.of("unpaid-effects:\n  default:\n    min-health-percent: .nan",
                        "min-health-percent must be a number between 0 and 100"),
                Arguments.of("unpaid-effects:\n  default:\n    part-min-health-percent: 5",
                        "unpaid-effects.default.part-min-health-percent must be a section"),
                Arguments.of("unpaid-effects:\n  default:\n    part-min-health-percent:\n      hull: 200",
                        "unpaid-effects.default.part-min-health-percent.hull must be a number"),
                Arguments.of("unpaid-effects:\n  default:\n    keep-aloft: yes please",
                        "unpaid-effects.default.keep-aloft must be true or false"),
                Arguments.of("unpaid-effects:\n  categories:\n    boats: {}",
                        "unpaid-effects.categories.boats is not a vehicle category"),
                Arguments.of("unpaid-effects:\n  types:\n    schooner: {}",
                        "unpaid-effects.types.schooner is not a vehicle type"),
                Arguments.of("unpaid-effects:\n  categories:\n    static_emplacements: 123",
                        "unpaid-effects.categories.static_emplacements must be a section"),
                Arguments.of("unpaid-effects:\n  categories:\n    static_emplacements:\n      hourly-damage-percent: 200",
                        "unpaid-effects.categories.static_emplacements.hourly-damage-percent must be a number"),
                Arguments.of("unpaid-effects:\n  default:\n    part-min-health-percent:\n      hull: 40\n      hull: 0",
                        "unpaid-effects.default.part-min-health-percent lists hull twice"),
                Arguments.of("unpaid-effects:\n  types:\n    sloop:\n      keep-afloat: true\n    sloop:\n      keep-afloat: false",
                        "unpaid-effects.types lists sloop twice"),
                Arguments.of("unpaid-effects:\n  default:\n    ? [a, b]\n    : 1\n    ? [a, b]\n    : 2",
                        "unpaid-effects.default lists"),
                Arguments.of("unpaid-effects:\n  types:\n    sloop: 1",
                        "unpaid-effects.types.sloop must be a section"),
                Arguments.of("unpaid-effects:\n  types:\n    sloop: {}\n    Sloop: {}",
                        "unpaid-effects.types lists Sloop twice (also as sloop)"),
                Arguments.of("unpaid-effects:\n  categories:\n    ships: {}\n    SHIPS: {}",
                        "unpaid-effects.categories lists SHIPS twice (also as ships)"),
                Arguments.of("unpaid-effects:\n  default:\n    part-min-health-percent:\n      hull: 5\n      HULL: 6",
                        "unpaid-effects.default.part-min-health-percent lists HULL twice"));
    }

    private void load(String unpaid) throws Exception {
        Path file = temporary.resolve("vehicles.yml");
        Files.writeString(file, CATEGORIES + unpaid + "\n");
        VehiclesConfigLoader.load(file.toFile());
    }
}
