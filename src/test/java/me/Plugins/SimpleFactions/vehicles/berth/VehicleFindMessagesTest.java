package me.Plugins.SimpleFactions.vehicles.berth;


import me.Plugins.SimpleFactions.vehicles.berth.VehicleFindMessages;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class VehicleFindMessagesTest {
    @Test
    void resolveInstallation_matchesIdOrName() {
        InstallationHandler handler = Mockito.mock(InstallationHandler.class);
        Installation port = new Installation("port-1", "Harbour", InstallationKind.PORT, 1, 0, 0, 0L);
        List<Installation> installations = new ArrayList<>();
        installations.add(port);
        Mockito.when(handler.getById("port-1")).thenReturn(port);
        Mockito.when(handler.getById("missing")).thenReturn(null);
        Mockito.when(handler.getAll()).thenReturn(installations);

        assertEquals(port, VehicleFindMessages.resolveInstallation(handler, "port-1"));
        assertEquals(port, VehicleFindMessages.resolveInstallation(handler, "harbour"));
        assertNull(VehicleFindMessages.resolveInstallation(handler, "missing"));
    }

    @Test
    void formatLocation_unknownWhenMissing() {
        assertTrue(VehicleFindMessages.formatLocation(Optional.empty()).contains("unknown"));
    }

    @Test
    void formatLocation_includesWorldAndCoords() {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("world");
        Location location = new Location(world, 100.4, 64.2, -20.8);

        String formatted = VehicleFindMessages.formatLocation(Optional.of(location));

        assertTrue(formatted.contains("world"));
        assertTrue(formatted.contains("100"));
        assertTrue(formatted.contains("64"));
        assertTrue(formatted.contains("-21"));
    }
}
