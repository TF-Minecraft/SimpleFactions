package me.Plugins.SimpleFactions.vehicles.berth;


import me.Plugins.SimpleFactions.vehicles.berth.VehicleCategoryRules;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Loaders.InstallationConfigLoader;
import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;

class VehicleCategoryRulesTest {
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-category-rules-");
        writeVehiclesFixture();
        InstallationConfigLoader.load(writeInstallationsFixture().toFile());
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
    void isBerthableCategory_train_false() {
        assertFalse(VehicleCategoryRules.isBerthableCategory("train"));
    }

    @Test
    void isBerthableCategory_ships_true() {
        assertTrue(VehicleCategoryRules.isBerthableCategory("ships"));
    }

    @Test
    void isBerthableCategory_aircraft_true() {
        assertTrue(VehicleCategoryRules.isBerthableCategory("aircraft"));
    }

    @Test
    void isBerthableCategory_landVehicles_true() {
        assertTrue(VehicleCategoryRules.isBerthableCategory("land_vehicles"));
    }

    @Test
    void isBerthableCategory_staticEmplacements_true() {
        assertTrue(VehicleCategoryRules.isBerthableCategory("static_emplacements"));
    }

    @Test
    void isBerthableCategory_unknown_false() {
        assertFalse(VehicleCategoryRules.isBerthableCategory("unknown"));
    }

    @Test
    void isBerthableCategory_null_false() {
        assertFalse(VehicleCategoryRules.isBerthableCategory(null));
        assertFalse(VehicleCategoryRules.isBerthableCategory(""));
    }

    @Test
    void isBerthableType_coalCar_false() {
        assertFalse(VehicleCategoryRules.isBerthableType("coal_car"));
    }

    @Test
    void isBerthableType_cloudskimmer_true() {
        assertTrue(VehicleCategoryRules.isBerthableType("cloudskimmer"));
    }

    @Test
    void isBerthableType_unknown_false() {
        assertFalse(VehicleCategoryRules.isBerthableType("unknown"));
    }

    private Path writeInstallationsFixture() throws IOException {
        Path installationsYaml = tempDir.resolve("installations.yml");
        Files.writeString(installationsYaml, """
            consent-proximity-blocks: 20
            transfer-request-timeout-seconds: 60

            fort:
              radius: 80
              daily-upkeep: 50
              construction-time: 10
              slots:
                static_emplacements: 8
                land_vehicles: 2
            port:
              radius: 80
              daily-upkeep: 20
              construction-time: 10
              slots:
                ships: 8
            airport:
              radius: 80
              daily-upkeep: 35
              construction-time: 10
              slots:
                aircraft: 10
            """);
        return installationsYaml;
    }

    private void writeVehiclesFixture() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              land_vehicles:
                horse_cart:
                  upkeep: 5
                  size: 1
              train:
                coal_car:
                  upkeep: 1
                  size: 1
                  ignore-limit: true
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements:
                cannon:
                  upkeep: 10
                  size: 1
              aircraft:
                cloudskimmer:
                  upkeep: 8
                  size: 1
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
    }
}
