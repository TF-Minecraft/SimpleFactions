package net.tfminecraft.simplefactions.vehicles.handover;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverService.Status;
import net.tfminecraft.simplefactions.vehicles.registry.FakeOwnedInventory;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;

class VehicleHandoverServiceTest {
    private Path tempDir;
    private PlayerVehicleRegistry registry;
    private VehicleHandoverService service;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-vehicle-handover-");
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 2
            categories:
              ships:
                sloop:
                  upkeep: 8
                  size: 1
                  per-person: 1
                gunboat:
                  upkeep: 3
                  size: 1
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
        registry = new PlayerVehicleRegistry();
        service = new VehicleHandoverService(registry, new VehicleFeeStore(), () -> {}, (uuid, name) -> true);
    }

    @AfterEach
    void tearDown() throws IOException {
        VehicleOwnershipQueries.setSourceForTests(null);
        Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }

    @Test
    void ownerCanHandOverTheirOwnVehicle() {
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("v1", "sloop", "player_Alice"));

        VehicleHandoverService.Outcome outcome = service.evaluate("Alice", "Bob", "v1");

        assertEquals(Status.OK, outcome.status());
        assertEquals("sloop", outcome.vehicleTypeId());
    }

    @Test
    void someoneElsesVehicleIsRefused() {
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("v1", "sloop", "player_Carol"));

        assertEquals(Status.NOT_OWNER, service.evaluate("Alice", "Bob", "v1").status());
    }

    @Test
    void factionVehiclesOwnedByTheLeaderAreRefused() {
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("v1", "sloop", "player_Alice"));
        registry.register(new PlayerVehicleRecord(
                java.util.UUID.randomUUID(), "v1", "sloop", OwnershipMode.POOL, null, "rome"));

        assertEquals(Status.NOT_OWNER, service.evaluate("Alice", "Bob", "v1").status());
    }

    @Test
    void recipientWithoutRoomIsRefused() {
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory()
                .add("v1", "sloop", "player_Alice")
                .add("v2", "sloop", "player_Bob"));

        VehicleHandoverService.Outcome outcome = service.evaluate("Alice", "Bob", "v1");

        assertEquals(Status.NO_ROOM, outcome.status());
    }
}
