package me.Plugins.SimpleFactions.vehicles.registry;


import me.Plugins.SimpleFactions.vehicles.registry.VehicleRegistryClaimService;
import me.Plugins.SimpleFactions.vehicles.registry.VehicleRegistryClaimListener;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import net.tfminecraft.VehicleFramework.Events.VehicleOwnerClaimedEvent;

class VehicleRegistryClaimListenerTest {
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-claim-listener-");
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 2
            default-upkeep: 4

            categories:
              ships: {}
              static_emplacements: {}
              aircraft: {}
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
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
    void handleClaimResult_cancelsOnSlotLimit() {
        Player player = mock(Player.class);
        VehicleOwnerClaimedEvent event =
                new VehicleOwnerClaimedEvent(player, null, "none", "player_Test");

        VehicleRegistryClaimListener.handleClaimResult(
                event,
                player,
                "monoplane",
                VehicleRegistryClaimService.ClaimRegisterResult.failSlotLimit(CanBuildResult.TOTAL_LIMIT));

        assertTrue(event.isCancelled());
        verify(player).sendMessage("§cYou have reached your personal vehicle limit (2).");
    }

    @Test
    void handleClaimResult_allowsRegistered() {
        Player player = mock(Player.class);
        VehicleOwnerClaimedEvent event =
                new VehicleOwnerClaimedEvent(player, null, "none", "player_Test");

        VehicleRegistryClaimListener.handleClaimResult(
                event,
                player,
                "monoplane",
                VehicleRegistryClaimService.ClaimRegisterResult.allowed());

        assertFalse(event.isCancelled());
    }
}
