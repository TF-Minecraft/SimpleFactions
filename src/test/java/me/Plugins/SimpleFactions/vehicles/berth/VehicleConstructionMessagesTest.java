package me.Plugins.SimpleFactions.vehicles.berth;


import me.Plugins.SimpleFactions.vehicles.berth.VehicleConstructionMessages;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;

class VehicleConstructionMessagesTest {
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-construction-messages-");
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              land_vehicles:
                horse_cart:
                  upkeep: 5
                  size: 1
                  per-person: 3
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
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
    void totalLimit_usesLockedCopy() {
        String message = VehicleConstructionMessages.forResult(CanBuildResult.TOTAL_LIMIT, "ironclad");
        assertEquals("§cYou have reached your personal vehicle limit (3).", message);
        assertFalse(message.contains("—"));
    }

    @Test
    void perTypeLimit_usesLockedCopy() {
        String message = VehicleConstructionMessages.forResult(
                CanBuildResult.PER_TYPE_LIMIT,
                "horse_cart");
        assertEquals(
                "§cYou already have the maximum number of horse_cart vehicles (3).",
                message);
        assertFalse(message.contains("—"));
    }

    @Test
    void unknownType_usesLockedCopy() {
        String message = VehicleConstructionMessages.forResult(CanBuildResult.UNKNOWN_TYPE, "unknown");
        assertEquals("§cThis vehicle type is not registered for faction upkeep.", message);
        assertFalse(message.contains("—"));
    }

    @Test
    void ok_returnsNull() {
        assertNull(VehicleConstructionMessages.forResult(CanBuildResult.OK, "ironclad"));
    }

    @Test
    void noEmDash() {
        assertFalse(VehicleConstructionMessages.forResult(CanBuildResult.TOTAL_LIMIT, "ironclad").contains("—"));
        assertFalse(VehicleConstructionMessages.forResult(CanBuildResult.PER_TYPE_LIMIT, "horse_cart").contains("—"));
        assertFalse(VehicleConstructionMessages.forResult(CanBuildResult.UNKNOWN_TYPE, "unknown").contains("—"));
    }
}
