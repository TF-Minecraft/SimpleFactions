package net.tfminecraft.simplefactions.vehicles.berth;


import net.tfminecraft.simplefactions.vehicles.registry.FakeOwnedInventory;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleUnberthService;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleInstallationLockService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;

class InstallationVehicleUnberthServiceTest {
    private Path tempDir;
    private PlayerVehicleRegistry registry;
    private List<String> assigned;
    private InstallationVehicleUnberthService service;
    private Faction faction;
    private Installation port;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-unberth-");
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 2
            default-upkeep: 1
            default-per-person: 1

            categories:
              aircraft:
                cloudskimmer:
                  size: 1
                  upkeep: 1
                  per-person: 1
              ships: {}
              static_emplacements: {}
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory());
        registry = new PlayerVehicleRegistry();
        assigned = new ArrayList<>();
        service = new InstallationVehicleUnberthService(
                registry, (uuid, name) -> {
                    assigned.add(uuid + ":" + name);
                    return true;
                });
        faction = mock(Faction.class);
        when(faction.getLeader()).thenReturn("Leader");
        port = new Installation("port-1", "Harbour", InstallationKind.PORT, 1, 0, 0, 0L);
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
    void unberth_successAssignsLeaderAndClearsRegistry() {
        String vehicleUuid = UUID.randomUUID().toString();
        registry.register(
                new PlayerVehicleRecord(
                        UUID.randomUUID(),
                        vehicleUuid,
                        "cloudskimmer",
                        OwnershipMode.INSTALLATION,
                        port.getId()));

        SimpleFactions plugin = savingPlugin();
        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(plugin);

            InstallationVehicleUnberthService.UnberthOutcome outcome =
                    service.unberth(faction, "Leader", port, vehicleUuid);

            assertEquals(InstallationVehicleUnberthService.UnberthResult.OK, outcome.result());
            assertFalse(registry.getByVehicleUuid(vehicleUuid).isPresent());
            assertEquals(List.of(vehicleUuid + ":Leader"), assigned);
            verify(plugin).saveVehicleRegistry();
        }
    }

    @Test
    void unberth_rejectsNonLeader() {
        String vehicleUuid = UUID.randomUUID().toString();
        registry.register(
                new PlayerVehicleRecord(
                        UUID.randomUUID(),
                        vehicleUuid,
                        "cloudskimmer",
                        OwnershipMode.INSTALLATION,
                        port.getId()));

        InstallationVehicleUnberthService.UnberthOutcome outcome =
                service.unberth(faction, "Other", port, vehicleUuid);

        assertEquals(InstallationVehicleUnberthService.UnberthResult.NOT_LEADER, outcome.result());
        assertTrue(registry.getByVehicleUuid(vehicleUuid).isPresent());
        assertTrue(assigned.isEmpty());
    }

    @Test
    void unberth_rejectsPersonalVehicle() {
        String vehicleUuid = UUID.randomUUID().toString();
        registry.register(
                new PlayerVehicleRecord(
                        UUID.randomUUID(),
                        vehicleUuid,
                        "cloudskimmer",
                        OwnershipMode.PERSONAL,
                        null));

        InstallationVehicleUnberthService.UnberthOutcome outcome =
                service.unberth(faction, "Leader", port, vehicleUuid);

        assertEquals(InstallationVehicleUnberthService.UnberthResult.NOT_BERTHED, outcome.result());
    }

    @Test
    void unberth_rejectsWhenInstallationLocked() {
        String vehicleUuid = UUID.randomUUID().toString();
        registry.register(
                new PlayerVehicleRecord(
                        UUID.randomUUID(),
                        vehicleUuid,
                        "cloudskimmer",
                        OwnershipMode.INSTALLATION,
                        port.getId()));

        try (MockedStatic<VehicleInstallationLockService> lock = mockStatic(VehicleInstallationLockService.class)) {
            lock.when(() -> VehicleInstallationLockService.isVehicleLocked(eq(port.getId()), any()))
                    .thenReturn(true);

            InstallationVehicleUnberthService.UnberthOutcome outcome =
                    service.unberth(faction, "Leader", port, vehicleUuid);

            assertEquals(InstallationVehicleUnberthService.UnberthResult.EMBARGO, outcome.result());
            assertTrue(registry.getByVehicleUuid(vehicleUuid).isPresent());
            assertTrue(assigned.isEmpty());
        }
    }

    @Test
    void unberth_rejectsWhenPersonalLimitIsFull() {
        String vehicleUuid = UUID.randomUUID().toString();
        registry.register(
                new PlayerVehicleRecord(
                        UUID.randomUUID(),
                        vehicleUuid,
                        "cloudskimmer",
                        OwnershipMode.INSTALLATION,
                        port.getId()));
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("other", "cloudskimmer", "player_Leader"));

        InstallationVehicleUnberthService.UnberthOutcome outcome =
                service.unberth(faction, "Leader", port, vehicleUuid);

        assertEquals(InstallationVehicleUnberthService.UnberthResult.NO_PERSONAL_ROOM, outcome.result());
        assertTrue(registry.getByVehicleUuid(vehicleUuid).isPresent());
        assertTrue(assigned.isEmpty());
        assertTrue(InstallationVehicleUnberthService.messageFor(outcome).contains("cloudskimmer"));
    }

    /** A plugin mock whose registry saves succeed. */
    private static SimpleFactions savingPlugin() {
        // A default answer rather than when(), so it is safe inside another stubbing call.
        return org.mockito.Mockito.mock(SimpleFactions.class, invocation ->
                invocation.getMethod().getReturnType() == boolean.class
                        ? Boolean.TRUE
                        : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation));
    }
}
