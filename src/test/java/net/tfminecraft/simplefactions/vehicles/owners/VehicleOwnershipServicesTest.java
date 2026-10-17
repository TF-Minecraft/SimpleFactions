package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.vehicleframework.api.QuarantinedVehicle;
import net.tfminecraft.vehicleframework.api.VehicleAccessGroups;
import net.tfminecraft.vehicleframework.api.VehicleOwnershipAuthority;
import net.tfminecraft.vehicleframework.api.VehicleQuarantineSource;

class VehicleOwnershipServicesTest {
    @TempDir Path folder;

    @Test
    void theQuarantineSourceListsHeldBackVehiclesByIdForStaff() throws Exception {
        ServicesManager services = mock(ServicesManager.class);
        Plugin plugin = mock(Plugin.class);
        UUID vehicle = UUID.randomUUID();
        FakeOwnerApi.install().stored(vehicle.toString(), "cart", "player_Old");
        VehicleQuarantine quarantine = new VehicleQuarantine(folder);
        quarantine.load(List.of(new VehicleQuarantine.Entry(vehicle.toString(), "player_Old", "renamed")));
        quarantine.setFindings(List.of(new VehicleQuarantine.Entry("not-a-uuid", null, "reconcile: odd row")));

        VehicleOwnershipServices.registerQuarantine(services, plugin, quarantine);

        ArgumentCaptor<VehicleQuarantineSource> source = ArgumentCaptor.forClass(VehicleQuarantineSource.class);
        verify(services).register(eq(VehicleQuarantineSource.class), source.capture(), eq(plugin),
                eq(ServicePriority.Normal));
        assertEquals(List.of(new QuarantinedVehicle(vehicle, "player_Old", "renamed")),
                source.getValue().quarantinedVehicles());
    }

    @Test
    void groupAccessIsRegisteredOnlyWhenVehicleFrameworkHasTheService() {
        ServicesManager services = mock(ServicesManager.class);
        Plugin plugin = mock(Plugin.class);
        java.util.logging.Logger logger = mock(java.util.logging.Logger.class);
        org.mockito.Mockito.when(plugin.getLogger()).thenReturn(logger);
        AtomicBoolean ready = new AtomicBoolean(true);

        assertTrue(VehicleOwnershipServices.registerGroupAccess(services, plugin, new PlayerVehicleRegistry(), ready::get));

        ArgumentCaptor<VehicleAccessGroups> groups = ArgumentCaptor.forClass(VehicleAccessGroups.class);
        verify(services).register(eq(VehicleAccessGroups.class), groups.capture(), eq(plugin), eq(ServicePriority.Normal));
        assertTrue(groups.getValue() instanceof VehicleGroupAccess);
        ready.set(false);
        assertFalse(groups.getValue().ready());

        ServicesManager old = mock(ServicesManager.class);
        assertFalse(VehicleOwnershipServices.registerGroupAccess(old, plugin, new PlayerVehicleRegistry(), ready::get,
                "net.tfminecraft.vehicleframework.api.Missing"));
        org.mockito.Mockito.verifyNoInteractions(old);
        verify(logger).warning(org.mockito.ArgumentMatchers.contains("VehicleFramework 2.15 or newer"));
    }

    @Test
    void theAuthorityAnswersReadyFromSimpleFactionsOnEveryCall() {
        ServicesManager services = mock(ServicesManager.class);
        AtomicBoolean ready = new AtomicBoolean(true);

        VehicleOwnershipServices.registerAuthority(services, mock(Plugin.class), ready::get);

        ArgumentCaptor<VehicleOwnershipAuthority> authority = ArgumentCaptor.forClass(VehicleOwnershipAuthority.class);
        verify(services).register(eq(VehicleOwnershipAuthority.class), authority.capture(), any(Plugin.class),
                eq(ServicePriority.Normal));
        assertTrue(authority.getValue().ready());
        ready.set(false);
        assertFalse(authority.getValue().ready());
    }
}
