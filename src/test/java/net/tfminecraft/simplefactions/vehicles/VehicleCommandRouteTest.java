package net.tfminecraft.simplefactions.vehicles;


import net.tfminecraft.simplefactions.vehicles.VehicleFactionCommands.VehicleCommandRoute;
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
    @Test
    void bankPaymentRouteIsExplicitAndRejectsInvalidSources() {
        assertTrue(VehicleCommandRoute.isMaintenancePay(new String[] {"vehicle", "maintenance", "pay", "bank"}));
        assertEquals(
                net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.PaymentSource.BANK,
                VehicleCommandRoute.maintenancePaymentSource(new String[] {"VEHICLE", "MAINTENANCE", "PAY", "BANK"}));
        assertFalse(VehicleCommandRoute.isMaintenancePay(new String[] {"vehicle", "maintenance", "pay", "bnak"}));
    }

    @Test
    void extraWordsAfterBankStillPayFromBank() {
        String[][] typed = {
            {"vehicle", "maintenance", "pay", "bank", "100"},
            {"vehicle", "maintenance", "pay", "bank", "Alice", "100"},
            {"vehicle", "maintenance", "pay", "100", "bank"},
            {"vehicle", "maintenance", "pay", "Alice", "bank"},
            {"vehicle", "maintenance", "pay", "100", "Alice", "bank"},
        };
        for (String[] args : typed) {
            assertTrue(VehicleCommandRoute.isMaintenancePay(args));
            assertEquals(
                    net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.PaymentSource.BANK,
                    VehicleCommandRoute.maintenancePaymentSource(args));
        }
    }

    @Test
    void extraWordsWithoutBankAreRejected() {
        assertFalse(VehicleCommandRoute.isMaintenancePay(new String[] {"vehicle", "maintenance", "pay", "100", "bnak"}));
        assertFalse(VehicleCommandRoute.isMaintenancePay(new String[] {"vehicle", "maintenance", "pay", "Alice", "100"}));
    }

    @Test
    void anAmountWithoutBankPaysFromPouch() {
        String[] args = {"vehicle", "maintenance", "pay", "100"};
        assertTrue(VehicleCommandRoute.isMaintenancePay(args));
        assertEquals(
                net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.PaymentSource.POUCH,
                VehicleCommandRoute.maintenancePaymentSource(args));
    }

}
