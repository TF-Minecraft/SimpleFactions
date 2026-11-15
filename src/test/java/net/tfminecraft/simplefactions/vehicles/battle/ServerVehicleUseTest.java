package net.tfminecraft.simplefactions.vehicles.battle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import net.tfminecraft.simplefactions.vehicles.registry.*;
import net.tfminecraft.simplefactions.war.battle.engine.core.*;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandVehicleRules;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;
import net.tfminecraft.vehicleframework.data.OwnerData;
import net.tfminecraft.vehicleframework.events.ServerVehicleUseEvent;
import java.util.UUID;

class ServerVehicleUseTest {
    @Test void optionalRegistration() {
        PluginManager plugins = mock(PluginManager.class);
        Plugin plugin = mock(Plugin.class);
        ServerVehicleUseRegistration.register(plugins, plugin, "absent.Event");
        verifyNoInteractions(plugins);
        ServerVehicleUseRegistration.register(plugins, plugin);
        verify(plugins).registerEvents(any(ServerVehicleUseListener.class), eq(plugin));
        assertThrows(IllegalStateException.class, () -> ServerVehicleUseRegistration.register(
                plugins, plugin, "java.lang.String", "absent.Listener"));
    }

    @Test void serverBattleRulesAndEventAgree() {
        Player player = mock(Player.class);
        UUID account = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(account);
        ActiveVehicle vehicle = mock(ActiveVehicle.class);
        when(vehicle.getUUID()).thenReturn("server-vehicle");
        OwnerData owner = new OwnerData(); owner.setOwner("SeRvEr");
        when(vehicle.getOwnerData()).thenReturn(owner);
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        // Even a stale upkeep row must not deny server vehicle use.
        registry.register(new PlayerVehicleRecord(null, "server-vehicle", "ship", OwnershipMode.UPKEEP, null, "red"));
        Battle battle = mock(Battle.class);
        try (var battles = mockStatic(BattleManager.class); var warbands = mockStatic(WarbandVehicleRules.class)) {
            assertFalse(BattleVehicleEligibilityService.mayUseServer(player, vehicle));
            assertEquals(BattleVehicleEligibilityService.BattleVehicleEligibilityResult.NOT_CAMPAIGN_BATTLE,
                    BattleVehicleEligibilityService.check(player, vehicle, registry));
            battles.when(() -> BattleManager.getBattleByMemberId(account)).thenReturn(battle);
            assertFalse(BattleVehicleEligibilityService.mayUseServer(player, vehicle));
            when(battle.getWarId()).thenReturn(7);
            var denied = BattleVehicleEligibilityService.check(player, vehicle, registry);
            assertEquals(BattleVehicleEligibilityService.BattleVehicleEligibilityResult.DENIED_SERVER_UNASSIGNED, denied);
            assertTrue(denied.isDenied());
            assertEquals("§cStaff have not assigned this server vehicle to a side in this battle.",
                    BattleVehicleEligibilityService.Messages.forResult(denied));
            ServerVehicleUseListener listener = new ServerVehicleUseListener();
            ServerVehicleUseEvent event = new ServerVehicleUseEvent(player, vehicle);
            listener.onUse(event); assertFalse(event.isAllowed());
            when(battle.isAllVehiclesUsable()).thenReturn(true);
            listener.onUse(event); assertTrue(event.isAllowed());
            when(battle.isAllVehiclesUsable()).thenReturn(false);
            when(battle.getForcedVehicleSide("server-vehicle")).thenReturn("attack");
            assertFalse(BattleVehicleEligibilityService.mayUseServer(player, vehicle));
            BattleSide side = mock(BattleSide.class);
            when(side.getId()).thenReturn("defend");
            when(battle.getSideByMemberId(account)).thenReturn(side);
            assertFalse(BattleVehicleEligibilityService.mayUseServer(player, vehicle));
            when(side.getId()).thenReturn("ATTACK");
            assertTrue(BattleVehicleEligibilityService.mayUseServer(player, vehicle));
            warbands.when(() -> WarbandVehicleRules.blocksVehicleEntry(player)).thenReturn(true);
            assertFalse(BattleVehicleEligibilityService.mayUseServer(player, vehicle));
            assertEquals(BattleVehicleEligibilityService.BattleVehicleEligibilityResult.DENIED_PRE_BATTLE_WARBAND,
                    BattleVehicleEligibilityService.check(player, vehicle, registry));
        }
    }
}
