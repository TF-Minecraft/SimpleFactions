package net.tfminecraft.simplefactions.vehicles;



import net.tfminecraft.simplefactions.vehicles.VehicleFactionCommands.VehicleTabCompletions;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceMessages;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class VehicleTabCompletionsTest {
    @Test
    void subcommands_listsTransferAndMaintenance() {
        List<String> completions = VehicleTabCompletions.subcommands("");
        assertTrue(completions.contains("transfer"));
        assertTrue(completions.contains("take"));
        assertTrue(completions.contains("give"));
        assertTrue(completions.contains("maintenance"));
    }

    @Test
    void subcommands_filtersPrefix() {
        List<String> completions = VehicleTabCompletions.subcommands("tr");
        assertEquals(List.of("transfer"), completions);
    }

    @Test
    void maintenanceActions_pay() {
        assertEquals(List.of("pay"), VehicleTabCompletions.maintenanceActions(""));
        assertEquals(List.of("pay"), VehicleTabCompletions.maintenanceActions("p"));
        assertTrue(VehicleTabCompletions.maintenanceActions("x").isEmpty());
    }

    @Test
    void playerFacingMaintenanceCopy_hasNoEmDash() {
        assertFalse(VehicleMaintenanceMessages.repairBlocked().contains("—"));
        assertFalse(VehicleMaintenanceMessages.payArmed().contains("—"));
        assertFalse(VehicleMaintenanceMessages.vehicleUsage().contains("—"));
    }
}
