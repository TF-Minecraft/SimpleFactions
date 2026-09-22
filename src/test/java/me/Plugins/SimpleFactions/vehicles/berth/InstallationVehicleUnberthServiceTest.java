package me.Plugins.SimpleFactions.vehicles.berth;


import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.registry.OwnershipMode;
import me.Plugins.SimpleFactions.vehicles.berth.InstallationVehicleUnberthService;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleInstallationLockService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;

class InstallationVehicleUnberthServiceTest {
    private PlayerVehicleRegistry registry;
    private List<String> cleared;
    private InstallationVehicleUnberthService service;
    private Faction faction;
    private Installation port;

    @BeforeEach
    void setUp() {
        registry = new PlayerVehicleRegistry();
        cleared = new ArrayList<>();
        service = new InstallationVehicleUnberthService(registry, cleared::add);
        faction = mock(Faction.class);
        when(faction.getLeader()).thenReturn("Leader");
        port = new Installation("port-1", "Harbour", InstallationKind.PORT, 1, 0, 0, 0L);
    }

    @Test
    void unberth_successClearsRegistry() {
        String vehicleUuid = UUID.randomUUID().toString();
        registry.register(
                new PlayerVehicleRecord(
                        UUID.randomUUID(),
                        vehicleUuid,
                        "cloudskimmer",
                        OwnershipMode.INSTALLATION,
                        port.getId()));

        SimpleFactions plugin = mock(SimpleFactions.class);
        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(plugin);

            InstallationVehicleUnberthService.UnberthResult result =
                    service.unberth(faction, "Leader", port, vehicleUuid);

            assertEquals(InstallationVehicleUnberthService.UnberthResult.OK, result);
            assertFalse(registry.getByVehicleUuid(vehicleUuid).isPresent());
            assertEquals(List.of(vehicleUuid), cleared);
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

        InstallationVehicleUnberthService.UnberthResult result =
                service.unberth(faction, "Other", port, vehicleUuid);

        assertEquals(InstallationVehicleUnberthService.UnberthResult.NOT_LEADER, result);
        assertTrue(registry.getByVehicleUuid(vehicleUuid).isPresent());
        assertTrue(cleared.isEmpty());
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

        InstallationVehicleUnberthService.UnberthResult result =
                service.unberth(faction, "Leader", port, vehicleUuid);

        assertEquals(InstallationVehicleUnberthService.UnberthResult.NOT_BERTHED, result);
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

            InstallationVehicleUnberthService.UnberthResult result =
                    service.unberth(faction, "Leader", port, vehicleUuid);

            assertEquals(InstallationVehicleUnberthService.UnberthResult.EMBARGO, result);
            assertTrue(registry.getByVehicleUuid(vehicleUuid).isPresent());
            assertTrue(cleared.isEmpty());
        }
    }
}
