package net.tfminecraft.simplefactions.vehicles.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.managers.CommandManager;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.utils.TabCompletion;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferSessionManager;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferSessionManager.VehicleTransferSession;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.PaymentSource;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;

class VehicleMaintenanceBankCommandTest {
    @Test
    void ordinaryPlayerCanArmBankPayment() {
        Player player = mock(Player.class);
        UUID payer = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(payer);
        when(player.getName()).thenReturn("OrdinaryPlayer");
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("faction");
        SimpleFactions plugin = mock(SimpleFactions.class);
        var sessions = new VehicleMaintenancePaySessionManager();
        var transfers = new VehicleTransferSessionManager();
        transfers.put(payer, new VehicleTransferSession("port", System.currentTimeMillis() + 60_000));
        when(plugin.getVehicleMaintenancePaySessionManager()).thenReturn(sessions);
        when(plugin.getVehicleTransferSessionManager()).thenReturn(transfers);

        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class);
                MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(plugin);
            var commands = new CommandManager();
            assertTrue(commands.onCommand(player, command, "faction",
                    new String[] {"vehicle", "maintenance", "pay", "bank"}));
            assertEquals(PaymentSource.BANK, sessions.get(payer).getPaymentSource());
            assertNull(transfers.get(payer));
            factions.verifyNoInteractions();
            verify(player).sendMessage(VehicleMaintenanceMessages.payArmed(PaymentSource.BANK));
        }
    }

    @Test
    void ordinaryPlayerStillCannotArmLegacyPouchPayment() {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("OrdinaryPlayer");
        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
            net.tfminecraft.simplefactions.vehicles.VehicleFactionCommands.armMaintenancePay(player);
            verify(player).sendMessage(VehicleMaintenanceMessages.notLeader());
        }
    }

    @Test
    void bankPaymentAppearsInTabCompletionForOrdinaryPlayer() {
        Player player = mock(Player.class);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("faction");
        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
            List<String> rootCompletions = new TabCompletion().onTabComplete(
                    player, command, "faction", new String[] {"v"});
            assertTrue(rootCompletions.contains("vehicle"));
            assertFalse(rootCompletions.contains("transfervehicle"));
            assertEquals(List.of("bank"), new TabCompletion().onTabComplete(player, command, "faction",
                    new String[] {"vehicle", "maintenance", "pay", "b"}));
        }
    }

    @Test
    void expiredBankSessionCannotCharge() {
        Player player = mock(Player.class);
        UUID payer = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(payer);
        var sessions = new VehicleMaintenancePaySessionManager();
        sessions.put(payer, new VehicleMaintenancePaySessionManager.VehicleMaintenancePaySession(0, PaymentSource.BANK));
        var service = mock(VehicleMaintenancePayService.class);
        var click = new VehiclePreInteractEvent(player, null);
        new VehicleMaintenancePayListener(sessions, service).onVehiclePreInteract(click);
        assertFalse(click.isCancelled());
        assertNull(sessions.get(payer));
        verifyNoInteractions(service);
    }
}
