package net.tfminecraft.simplefactions.vehicles.registry;


import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryPersistence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;

class PlayerVehicleRegistryTest {
    private Path tempDir;
    private PlayerVehicleRegistry registry;
    private VehicleRegistryPersistence persistence;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-vehicle-registry-");
        registry = new PlayerVehicleRegistry();
        persistence = new VehicleRegistryPersistence(tempDir.toFile(), registry);
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
    void saveAndLoad_skipsPersonalRows() {
        UUID player = UUID.randomUUID();
        registry.register(new PlayerVehicleRecord(
            player, "vehicle-1", "ironclad", OwnershipMode.PERSONAL, null));
        registry.register(new PlayerVehicleRecord(
            player, "vehicle-2", "ironclad", OwnershipMode.INSTALLATION, "port-1"));

        persistence.save();

        PlayerVehicleRegistry loaded = new PlayerVehicleRegistry();
        VehicleRegistryPersistence loadedPersistence =
            new VehicleRegistryPersistence(tempDir.toFile(), loaded);
        loadedPersistence.load();

        assertTrue(loaded.getByVehicleUuid("vehicle-1").isEmpty());
        assertTrue(loaded.getByVehicleUuid("vehicle-2").isPresent());
        assertEquals(OwnershipMode.INSTALLATION,
            loaded.getByVehicleUuid("vehicle-2").get().getMode());
        assertEquals("port-1",
            loaded.getByVehicleUuid("vehicle-2").get().getInstallationId());
    }

    @Test
    void saveAndLoad_keepsPoolFaction() {
        UUID player = UUID.randomUUID();
        registry.register(new PlayerVehicleRecord(
            player, "vehicle-pool", "field_artillery", OwnershipMode.POOL, null, "red"));

        persistence.save();

        PlayerVehicleRegistry loaded = new PlayerVehicleRegistry();
        new VehicleRegistryPersistence(tempDir.toFile(), loaded).load();

        PlayerVehicleRecord record = loaded.getByVehicleUuid("vehicle-pool").orElseThrow();
        assertEquals(OwnershipMode.POOL, record.getMode());
        assertEquals("red", record.getFactionId());
        assertEquals(1, loaded.getPoolVehicles("red").size());
    }

    @Test
    void register_replacesDuplicateVehicleUuid() {
        UUID player = UUID.randomUUID();
        registry.register(new PlayerVehicleRecord(
            player, "vehicle-1", "ironclad", OwnershipMode.INSTALLATION, "port-1"));
        registry.register(new PlayerVehicleRecord(
            UUID.randomUUID(), "vehicle-1", "cruiser", OwnershipMode.INSTALLATION, "port-2"));

        assertEquals(1, registry.getAll().size());
        assertEquals("cruiser", registry.getByVehicleUuid("vehicle-1").get().getVehicleTypeId());
        assertEquals("port-2", registry.getByVehicleUuid("vehicle-1").get().getInstallationId());
    }

    @Test
    void save_emptyRegistryDoesNotCreateFile() {
        persistence.save();
        assertEquals(false, new File(tempDir.toFile(), "vehicles_registry.json").exists());
    }

    @Test
    void getByInstallationId_returnsOnlyMatchingInstallationRows() {
        UUID player = UUID.randomUUID();
        registry.register(new PlayerVehicleRecord(
            player, "vehicle-1", "ironclad", OwnershipMode.INSTALLATION, "port-1"));
        registry.register(new PlayerVehicleRecord(
            player, "vehicle-2", "ironclad", OwnershipMode.INSTALLATION, "port-2"));
        registry.register(new PlayerVehicleRecord(
            player, "vehicle-3", "ironclad", OwnershipMode.PERSONAL, null));

        assertEquals(1, registry.getByInstallationId("port-1").size());
        assertEquals("vehicle-1", registry.getByInstallationId("port-1").get(0).getVehicleUuid());
        assertEquals(0, registry.getByInstallationId(null).size());
        assertTrue(registry.isBerthed("vehicle-1"));
        assertFalse(registry.isBerthed("vehicle-3"));
    }

    @Test
    void usedCategorySize_sumsSizesForInstallationAndCategory() throws IOException {
        Path tempConfigDir = Files.createTempDirectory("sf-registry-used-size-");
        try {
            Path vehiclesYaml = tempConfigDir.resolve("vehicles.yml");
            Files.writeString(vehiclesYaml, """
                personal-slot-limit: 1

                categories:
                  ships:
                    ironclad:
                      upkeep: 20
                      size: 1
                    cruiser:
                      upkeep: 40
                      size: 2
                  static_emplacements: {}
                  aircraft: {}
                """);
            VehiclesConfigLoader.load(vehiclesYaml.toFile());

            UUID player = UUID.randomUUID();
            registry.register(new PlayerVehicleRecord(
                player, "ship-1", "ironclad", OwnershipMode.INSTALLATION, "port-1"));
            registry.register(new PlayerVehicleRecord(
                player, "ship-2", "cruiser", OwnershipMode.INSTALLATION, "port-1"));
            registry.register(new PlayerVehicleRecord(
                player, "ship-3", "ironclad", OwnershipMode.INSTALLATION, "port-2"));
            registry.register(new PlayerVehicleRecord(
                player, "ship-4", "ironclad", OwnershipMode.PERSONAL, null));

            // Another faction's port with the same id does not use this port's space.
            registry.register(new PlayerVehicleRecord(
                player, "ship-5", "cruiser", OwnershipMode.INSTALLATION, "port-1", "blue"));

            assertEquals(3, registry.usedCategorySize("red", "port-1", "ships"));
            assertEquals(0, registry.usedCategorySize("red", "port-1", "aircraft"));
            assertEquals(1, registry.usedCategorySize("red", "port-2", "ships"));
            assertEquals(5, registry.usedCategorySize("blue", "port-1", "ships"));
            assertEquals(2, registry.getByInstallation("red", "port-1").size());
        } finally {
            Files.walk(tempConfigDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> path.toFile().delete());
        }
    }
}
