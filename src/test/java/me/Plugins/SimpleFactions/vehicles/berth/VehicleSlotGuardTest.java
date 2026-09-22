package me.Plugins.SimpleFactions.vehicles.berth;



import me.Plugins.SimpleFactions.vehicles.registry.FakeOwnedInventory;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.registry.OwnershipMode;
import me.Plugins.SimpleFactions.vehicles.registry.VehicleOwnershipQueries;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleSlotGuard;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;

class VehicleSlotGuardTest {
    private Path tempDir;
    private PlayerVehicleRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-vehicle-slot-guard-");
        registry = new PlayerVehicleRegistry();
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory());
    }

    @AfterEach
    void tearDown() throws IOException {
        VehicleOwnershipQueries.setSourceForTests(null);
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> path.toFile().delete());
        }
    }

    private void loadConfig(String yaml) throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, yaml);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
    }

    private static String playerName(UUID player) {
        return "p-" + player.toString().substring(0, 8);
    }

    @Test
    void checkCanBuild_okWhenUnderLimits() throws IOException {
        loadConfig("""
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        assertEquals(
            CanBuildResult.OK,
            VehicleSlotGuard.checkCanBuild(playerName(player), "ironclad", registry));
    }

    @Test
    void checkCanBuild_unknownType() throws IOException {
        loadConfig("""
            personal-slot-limit: 1

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        assertEquals(
            CanBuildResult.UNKNOWN_TYPE,
            VehicleSlotGuard.checkCanBuild(playerName(player), "unknown", registry));
    }

    @Test
    void checkCanBuild_perTypeLimit() throws IOException {
        loadConfig("""
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              aircraft:
                cloudskimmer:
                  size: 1
              ships: {}
              static_emplacements: {}
            """);

        UUID player = UUID.randomUUID();
        String name = playerName(player);
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("vehicle-1", "cloudskimmer", "player_" + name));

        assertEquals(
            CanBuildResult.PER_TYPE_LIMIT,
            VehicleSlotGuard.checkCanBuild(name, "cloudskimmer", registry));
    }

    @Test
    void checkCanBuild_landPerPersonThree() throws IOException {
        loadConfig("""
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              land_vehicles:
                horse_cart:
                  upkeep: 5
                  size: 1
                  per-person: 3
              ships: {}
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        String name = playerName(player);
        FakeOwnedInventory inventory = new FakeOwnedInventory()
                .add("vehicle-1", "horse_cart", "player_" + name)
                .add("vehicle-2", "horse_cart", "player_" + name);
        VehicleOwnershipQueries.setSourceForTests(inventory);

        assertEquals(
            CanBuildResult.OK,
            VehicleSlotGuard.checkCanBuild(name, "horse_cart", registry));

        inventory.add("vehicle-3", "horse_cart", "player_" + name);
        assertEquals(
            CanBuildResult.PER_TYPE_LIMIT,
            VehicleSlotGuard.checkCanBuild(name, "horse_cart", registry));
    }

    @Test
    void checkCanBuild_totalLimit() throws IOException {
        loadConfig("""
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
                  per-person: 3
                gunboat:
                  upkeep: 10
                  size: 1
                  per-person: 3
                cruiser:
                  upkeep: 40
                  size: 2
                  per-person: 3
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        String name = playerName(player);
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory()
                .add("vehicle-1", "ironclad", "player_" + name)
                .add("vehicle-2", "gunboat", "player_" + name)
                .add("vehicle-3", "cruiser", "player_" + name));

        assertEquals(
            CanBuildResult.TOTAL_LIMIT,
            VehicleSlotGuard.checkCanBuild(name, "ironclad", registry));
    }

    @Test
    void checkCanBuild_ignoreLimitSkipsTotal() throws IOException {
        loadConfig("""
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
                gunboat:
                  upkeep: 10
                  size: 1
                cruiser:
                  upkeep: 40
                  size: 2
              train:
                coal_car:
                  upkeep: 1
                  size: 1
                  ignore-limit: true
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        String name = playerName(player);
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory()
                .add("vehicle-1", "ironclad", "player_" + name)
                .add("vehicle-2", "gunboat", "player_" + name)
                .add("vehicle-3", "cruiser", "player_" + name));

        assertEquals(
            CanBuildResult.OK,
            VehicleSlotGuard.checkCanBuild(name, "coal_car", registry));
    }

    @Test
    void checkCanBuild_ignoreLimitStillPerType() throws IOException {
        loadConfig("""
            personal-slot-limit: 3
            default-upkeep: 4

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

        UUID player = UUID.randomUUID();
        String name = playerName(player);
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory()
                .add("vehicle-1", "coal_car", "player_" + name)
                .add("vehicle-2", "coal_car", "player_" + name)
                .add("vehicle-3", "coal_car", "player_" + name));

        assertEquals(
            CanBuildResult.PER_TYPE_LIMIT,
            VehicleSlotGuard.checkCanBuild(name, "coal_car", registry));
    }

    @Test
    void checkCanBuild_unlimitedTotalWhenLimitZero() throws IOException {
        loadConfig("""
            personal-slot-limit: 0
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
                  per-person: 3
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        String name = playerName(player);
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory()
                .add("vehicle-1", "ironclad", "player_" + name)
                .add("vehicle-2", "ironclad", "player_" + name));

        assertEquals(
            CanBuildResult.OK,
            VehicleSlotGuard.checkCanBuild(name, "ironclad", registry));
    }

    @Test
    void checkCanBuild_perTypeCheckedBeforeTotal() throws IOException {
        loadConfig("""
            personal-slot-limit: 1
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        String name = playerName(player);
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory()
                .add("vehicle-1", "ironclad", "player_" + name));

        assertEquals(
            CanBuildResult.PER_TYPE_LIMIT,
            VehicleSlotGuard.checkCanBuild(name, "ironclad", registry));
    }

    @Test
    void checkCanBuild_excludesBerthedFromPersonalCount() throws IOException {
        loadConfig("""
            personal-slot-limit: 1
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
                  per-person: 3
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        String name = playerName(player);
        registry.register(new PlayerVehicleRecord(
                player, "vehicle-1", "ironclad", OwnershipMode.INSTALLATION, "port-1"));
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory()
                .add("vehicle-1", "ironclad", "player_" + name));

        assertEquals(
            CanBuildResult.OK,
            VehicleSlotGuard.checkCanBuild(name, "ironclad", registry));
    }
}
