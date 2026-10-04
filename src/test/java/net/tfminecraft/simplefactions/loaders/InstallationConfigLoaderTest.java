package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.installation.InstallationKind;

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
        assertEquals(5.0, InstallationConfigLoader.getDailyUpkeep(InstallationKind.TRAIN_STATION));
        assertEquals(80, InstallationConfigLoader.getRadius(InstallationKind.TRAIN_STATION));
        assertEquals(259200, InstallationConfigLoader.getConstructionTimeSeconds(InstallationKind.TRAIN_STATION));
        assertEquals(java.util.Map.of("static_emplacements", 2),
                InstallationConfigLoader.getCategorySlots(InstallationKind.TRAIN_STATION));
        assertEquals(3, InstallationConfigLoader.getMaximumLevel(InstallationKind.TRAIN_STATION));
    }

    @Test
    void load_usesTrainStationDefaultsWhenSectionIsMissing() throws IOException {
        InstallationConfigLoader.load(writeInstallationsFixture().toFile());

        assertEquals(80, InstallationConfigLoader.getRadius(InstallationKind.TRAIN_STATION));
        assertEquals(5.0, InstallationConfigLoader.getDailyUpkeep(InstallationKind.TRAIN_STATION));
        assertEquals(259200, InstallationConfigLoader.getConstructionTimeSeconds(InstallationKind.TRAIN_STATION));
        assertEquals(java.util.Map.of("static_emplacements", 2),
                InstallationConfigLoader.getCategorySlots(InstallationKind.TRAIN_STATION));
        assertEquals(0, InstallationConfigLoader.getCategorySlotCapacity(InstallationKind.TRAIN_STATION, "aircraft"));
        assertEquals(0, InstallationConfigLoader.getCategorySlotCapacity(InstallationKind.TRAIN_STATION, "land_vehicles"));
        assertEquals(0, InstallationConfigLoader.getCategorySlotCapacity(InstallationKind.TRAIN_STATION, "ships"));
        assertEquals(0, InstallationConfigLoader.getCategorySlotCapacity(InstallationKind.TRAIN_STATION, "train"));
        assertEquals(3, InstallationConfigLoader.getMaximumLevel(InstallationKind.TRAIN_STATION));
        assertEquals(35.0, InstallationConfigLoader.getDailyUpkeep(InstallationKind.TRAIN_STATION, 2));
        assertEquals(100.0, InstallationConfigLoader.getDailyUpkeep(InstallationKind.TRAIN_STATION, 3));
        assertEquals(4, InstallationConfigLoader.getCategorySlotCapacity(InstallationKind.TRAIN_STATION, 3, "static_emplacements"));
    }

    @Test
    void load_bundledInstallationUpkeep() {
        InstallationConfigLoader.load(Path.of("src/main/resources/installations.yml").toFile());

        assertEquals(30.0, InstallationConfigLoader.getDailyUpkeep(InstallationKind.FORT));
        assertEquals(15.0, InstallationConfigLoader.getDailyUpkeep(InstallationKind.PORT));
        assertEquals(20.0, InstallationConfigLoader.getDailyUpkeep(InstallationKind.AIRPORT));
        assertEquals(5.0, InstallationConfigLoader.getDailyUpkeep(InstallationKind.TRAIN_STATION));
    }

    @Test
    void load_readsHigherLevelsAndRejectsGaps() throws IOException {
        Path path = writeInstallationsFixture();
        Files.writeString(path, Files.readString(path) + """
            train_station:
              radius: 80
              daily-upkeep: 10
              construction-time: 259200
              slots:
                static_emplacements: 2
              levels:
                2:
                  daily-upkeep: 35
                  construction-time: 259200
                  slots:
                    static_emplacements: 3
                3:
                  daily-upkeep: 100
                  construction-time: 432000
                  slots:
                    static_emplacements: 4
            """);
        InstallationConfigLoader.load(path.toFile());
        assertEquals(35.0, InstallationConfigLoader.getDailyUpkeep(InstallationKind.TRAIN_STATION, 2));
        assertEquals(432000, InstallationConfigLoader.getConstructionTimeSeconds(InstallationKind.TRAIN_STATION, 3));
        assertEquals(3, InstallationConfigLoader.getCategorySlotCapacity(InstallationKind.TRAIN_STATION, 2, "static_emplacements"));

        String validConfig = Files.readString(path);
        Files.writeString(path, validConfig.replace("2:\n", "4:\n"));
        assertThrows(IllegalStateException.class, () -> InstallationConfigLoader.load(path.toFile()));
        Files.writeString(path, validConfig.replace("2:\n", "1:\n"));
        assertThrows(IllegalStateException.class, () -> InstallationConfigLoader.load(path.toFile()));
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
              train: {}
              static_emplacements: {}
              aircraft: {}
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
    }
}
