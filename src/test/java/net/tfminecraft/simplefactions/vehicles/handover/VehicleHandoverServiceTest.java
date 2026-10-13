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
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.owners.FakeOwnerApi;
import net.tfminecraft.tlibs.character.CharacterRef;

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
        api = FakeOwnerApi.install();
    }

    private FakeOwnerApi api;
    private final CharacterRef alice = new CharacterRef(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());
    private final java.util.UUID bob = java.util.UUID.randomUUID();

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }

    @Test
    void ownerCanHandOverTheirOwnVehicle() {
        api.stored("v1", "sloop", "char_" + alice.characterId());

        VehicleHandoverService.Outcome outcome = service.evaluate(alice, bob, "v1");

        assertEquals(Status.OK, outcome.status());
        assertEquals("sloop", outcome.vehicleTypeId());
    }

    @Test
    void someoneElsesVehicleIsRefused() {
        api.stored("v1", "sloop", "char_" + java.util.UUID.randomUUID());

        assertEquals(Status.NOT_OWNER, service.evaluate(alice, bob, "v1").status());
        // Another character of the same account is a stranger too.
        assertEquals(Status.NOT_OWNER, service.evaluate(
                new CharacterRef(alice.ownerId(), java.util.UUID.randomUUID()), bob, "v1").status());
        assertEquals(Status.NOT_OWNER, service.evaluate(null, bob, "v1").status());
        assertEquals(Status.NOT_OWNER, service.evaluate(alice, bob, "missing").status());
        assertEquals(Status.NOT_OWNER, service.evaluate(alice, bob, null).status());
    }

    @Test
    void factionVehiclesOwnedByTheLeaderAreRefused() {
        api.stored("v1", "sloop", "char_" + alice.characterId());
        registry.register(new PlayerVehicleRecord(
                java.util.UUID.randomUUID(), "v1", "sloop", OwnershipMode.POOL, null, "rome"));

        assertEquals(Status.NOT_OWNER, service.evaluate(alice, bob, "v1").status());
    }

    @Test
    void recipientWithoutRoomIsRefused() {
        api.stored("v1", "sloop", "char_" + alice.characterId());
        registry.register(new PlayerVehicleRecord(
                java.util.UUID.randomUUID(), "v2", "sloop", OwnershipMode.PERSONAL, null, null, bob));

        VehicleHandoverService.Outcome outcome = service.evaluate(alice, bob, "v1");

        assertEquals(Status.NO_ROOM, outcome.status());
    }
}
