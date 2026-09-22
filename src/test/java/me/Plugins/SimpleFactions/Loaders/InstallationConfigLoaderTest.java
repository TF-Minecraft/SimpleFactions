package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.installation.InstallationKind;

class InstallationConfigLoaderTest {
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-installations-config-");
        writeVehiclesFixture();
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
    void load_readsCategorySlotCapacitiesRadiusAndRootKeys() throws IOException {
        InstallationConfigLoader.load(writeInstallationsFixture().toFile());

        assertEquals(50.0, InstallationConfigLoader.getDailyUpkeep(InstallationKind.FORT));
        assertEquals(80, InstallationConfigLoader.getRadius(InstallationKind.PORT));
        assertEquals(20, InstallationConfigLoader.getConsentProximityBlocks());
        assertEquals(60, InstallationConfigLoader.getTransferRequestTimeoutSeconds());
        assertEquals(8, InstallationConfigLoader.getCategorySlotCapacity(InstallationKind.PORT, "ships"));
        assertEquals(8, InstallationConfigLoader.getCategorySlots(InstallationKind.FORT).get("static_emplacements"));
        assertEquals(2, InstallationConfigLoader.getCategorySlotCapacity(InstallationKind.FORT, "land_vehicles"));
        assertEquals(10, InstallationConfigLoader.getCategorySlotCapacity(InstallationKind.AIRPORT, "aircraft"));
    }

    @Test
    void load_rejectsUnknownCategory() throws IOException {
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
            port:
              radius: 80
              daily-upkeep: 20
              construction-time: 10
              slots:
                ship: 8
            airport:
              radius: 80
              daily-upkeep: 35
              construction-time: 10
              slots:
                aircraft: 10
            """);

        assertThrows(
            IllegalStateException.class,
            () -> InstallationConfigLoader.load(installationsYaml.toFile()));
    }

    @Test
    void load_rejectsMissingRadius() throws IOException {
        Path installationsYaml = tempDir.resolve("installations.yml");
        Files.writeString(installationsYaml, """
            consent-proximity-blocks: 20
            transfer-request-timeout-seconds: 60

            fort:
              daily-upkeep: 50
              construction-time: 10
              slots:
                static_emplacements: 8
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

        assertThrows(
            IllegalStateException.class,
            () -> InstallationConfigLoader.load(installationsYaml.toFile()));
    }

    @Test
    void load_rejectsZeroRadius() throws IOException {
        Path installationsYaml = tempDir.resolve("installations.yml");
        Files.writeString(installationsYaml, """
            consent-proximity-blocks: 20
            transfer-request-timeout-seconds: 60

            fort:
              radius: 0
              daily-upkeep: 50
              construction-time: 10
              slots:
                static_emplacements: 8
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

        assertThrows(
            IllegalStateException.class,
            () -> InstallationConfigLoader.load(installationsYaml.toFile()));
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
            personal-slot-limit: 1

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              land_vehicles: {}
              static_emplacements: {}
              aircraft: {}
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
    }
}
