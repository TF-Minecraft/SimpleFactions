package me.Plugins.SimpleFactions.vehicles.registry;


import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.registry.OwnershipMode;
import me.Plugins.SimpleFactions.vehicles.registry.VehicleOwnershipQueries;
import me.Plugins.SimpleFactions.vehicles.registry.VehicleRegistryClaimService;
import me.Plugins.SimpleFactions.vehicles.berth.InstallationVehicleService.TryRegisterResult;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;

class VehicleRegistryClaimServiceTest {
    private Path tempDir;
    private PlayerVehicleRegistry registry;
    private VehicleRegistryClaimService service;
    private UUID playerUuid;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-claim-service-");
        loadConfig("""
            personal-slot-limit: 2
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft:
                monoplane:
                  upkeep: 8
                  size: 1
            """);
        registry = new PlayerVehicleRegistry();
        service = new VehicleRegistryClaimService(registry);
        playerUuid = UUID.randomUUID();
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

    @Test
    void tryRegisterOnClaim_allowsWhenUnderLimitWithoutWritingRegistry() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getName()).thenReturn("Alice");

        var result = service.tryRegisterOnClaim(player, target("veh-1", "monoplane"));

        assertEquals(TryRegisterResult.ALLOWED, result.status());
        assertTrue(registry.getByVehicleUuid("veh-1").isEmpty());
    }

    @Test
    void tryRegisterOnClaim_skipsWhenInstallationRowExists() {
        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(),
                "veh-1",
                "monoplane",
                OwnershipMode.INSTALLATION,
                "airport-1"));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getName()).thenReturn("Alice");

        var result = service.tryRegisterOnClaim(player, target("veh-1", "monoplane"));

        assertEquals(TryRegisterResult.SKIP, result.status());
        assertEquals(1, registry.getAll().size());
    }

    @Test
    void tryRegisterOnClaim_unknownType() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getName()).thenReturn("Alice");

        var result = service.tryRegisterOnClaim(player, target("veh-1", "unknown-type"));

        assertEquals(TryRegisterResult.FAIL_UNKNOWN_TYPE, result.status());
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    void tryRegisterOnClaim_slotLimit() {
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory()
                .add("veh-a", "ironclad", "player_Alice")
                .add("veh-b", "ironclad", "player_Alice"));

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getName()).thenReturn("Alice");

        var result = service.tryRegisterOnClaim(player, target("veh-3", "monoplane"));

        assertEquals(TryRegisterResult.FAIL_SLOT_LIMIT, result.status());
        assertEquals(CanBuildResult.TOTAL_LIMIT, result.buildFailure());
        assertTrue(registry.getByVehicleUuid("veh-3").isEmpty());
    }

    private static VehicleRegistryClaimService.VehicleClaimTarget target(
            String vehicleUuid,
            String vehicleTypeId) {
        return new VehicleRegistryClaimService.VehicleClaimTarget() {
            @Override
            public String getVehicleUuid() {
                return vehicleUuid;
            }

            @Override
            public String getVehicleTypeId() {
                return vehicleTypeId;
            }
        };
    }

    private void loadConfig(String yaml) throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, yaml);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
    }
}
