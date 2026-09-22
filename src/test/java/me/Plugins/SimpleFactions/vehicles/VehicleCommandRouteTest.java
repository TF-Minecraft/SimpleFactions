package me.Plugins.SimpleFactions.vehicles;


import me.Plugins.SimpleFactions.vehicles.VehicleFactionCommands.VehicleCommandRoute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VehicleCommandRouteTest {
    @Test
    void transferInstallationId_nestedCommand() {
        assertEquals(
                "harbour-1",
                VehicleCommandRoute.transferInstallationId(new String[] {"vehicle", "transfer", "harbour-1"}));
    }

    @Test
    void transferInstallationId_alias() {
        assertEquals(
                "harbour-1",
                VehicleCommandRoute.transferInstallationId(new String[] {"transfervehicle", "harbour-1"}));
    }

    @Test
    void transferInstallationId_missingIdIsEmpty() {
        assertEquals("", VehicleCommandRoute.transferInstallationId(new String[] {"vehicle", "transfer"}));
        assertEquals("", VehicleCommandRoute.transferInstallationId(new String[] {"transfervehicle"}));
    }

    @Test
    void transferInstallationId_unrelatedIsNull() {
        assertNull(VehicleCommandRoute.transferInstallationId(new String[] {"claim"}));
    }

    @Test
    void isMaintenancePay() {
        assertTrue(VehicleCommandRoute.isMaintenancePay(new String[] {"vehicle", "maintenance", "pay"}));
        assertFalse(VehicleCommandRoute.isMaintenancePay(new String[] {"vehicle", "maintenance"}));
        assertFalse(VehicleCommandRoute.isMaintenancePay(new String[] {"vehicle", "transfer", "harbour-1"}));
    }
}
