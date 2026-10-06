package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;

class OfficePersistenceTest {
    @Test void failedRemovalRestoresTheOfficeAndDoesNotClaimItWasRevoked() {
        var faction = mock(Faction.class);
        var actor = mock(Player.class);
        var state = new EspionageState();
        var holder = holder("Spy");
        state.appoint(holder, 80);
        when(faction.getEspionage()).thenReturn(state);
        when(actor.getName()).thenReturn("Leader");
        when(faction.isLeader("Leader")).thenReturn(true);
        when(faction.isMemberIgnoreCase("Spy")).thenReturn(true);
        try (var databases = mockConstruction(Database.class)) {
            assertFalse(EspionageService.remove(actor, faction));
            assertSame(holder, state.getSpymaster());
            assertEquals(1, state.appointmentCount(SpecialPosition.SPYMASTER));
            verify(actor).sendMessage(contains("could not be saved"));
            verify(actor, never()).sendMessage(contains("dismissed"));
        }
        try (var databases = mockConstruction(Database.class, (database, context) -> when(database.saveFactionChecked(faction)).thenReturn(true))) {
            assertTrue(EspionageService.remove(actor, faction));
            assertNull(state.getSpymaster());
            assertEquals(1, state.appointmentCount(SpecialPosition.SPYMASTER));
            verify(actor).sendMessage(contains("returns to you"));
        }
    }

    @Test void leaderCannotDismissTheirOwnDefaultHolding() {
        var faction = mock(Faction.class);
        var actor = mock(Player.class);
        var state = new EspionageState();
        var leader = holder("Leader");
        state.assignFounder(SpecialPosition.SPYMASTER, leader, 80);
        when(faction.getEspionage()).thenReturn(state);
        when(faction.getLeader()).thenReturn("Leader");
        when(actor.getName()).thenReturn("Leader");
        when(faction.isLeader("Leader")).thenReturn(true);
        try (var databases = mockConstruction(Database.class)) {
            assertFalse(EspionageService.remove(actor, faction));
            assertSame(leader, state.getSpymaster());
            assertTrue(databases.constructed().isEmpty());
            verify(actor).sendMessage(contains("already rests with you"));
        }
    }

    @Test void failedPaidAppointmentRefundsTreasuryAndRestoresOfficeHistoryAndUnrest() {
        var faction = mock(Faction.class);
        var bank = mock(Bank.class);
        var state = new EspionageState();
        var previous = holder("OldSpy");
        state.appoint(previous, 90);
        var balance = new AtomicReference<>(1000.0);
        when(faction.getEspionage()).thenReturn(state);
        when(faction.getBank()).thenReturn(bank);
        when(bank.getWealth()).thenAnswer(ignored -> balance.get());
        doAnswer(call -> { balance.updateAndGet(value -> value - (Double) call.getArgument(0)); return null; }).when(bank).withdraw(anyDouble());
        doAnswer(call -> { balance.updateAndGet(value -> value + (Double) call.getArgument(0)); return null; }).when(bank).deposit(anyDouble());
        try (var databases = mockConstruction(Database.class)) {
            assertFalse(EspionageService.completeAndSaveAppointment(faction, holder("NewSpy"), 70));
            assertSame(previous, state.getSpymaster());
            assertEquals(1000.0, balance.get());
            assertEquals(1, state.appointmentCount(SpecialPosition.SPYMASTER));
            assertTrue(state.unrestModifiers(System.currentTimeMillis()).isEmpty());
        }
        try (var databases = mockConstruction(Database.class, (database, context) -> when(database.saveFactionChecked(faction)).thenReturn(true))) {
            assertTrue(EspionageService.completeAndSaveAppointment(faction, holder("NewSpy"), 70));
            assertEquals(750.0, balance.get());
            assertEquals("NewSpy", state.getSpymaster().playerName);
            assertEquals(2, state.appointmentCount(SpecialPosition.SPYMASTER));
            assertEquals(-10.0, state.unrestModifiers(System.currentTimeMillis()).getFirst().getModifier(), 0.001);
        }
    }

    @Test void failedSabotageSaveKeepsThePreviousPrivateChoice() {
        var faction = mock(Faction.class);
        var actor = mock(Player.class);
        var state = new EspionageState();
        var holder = holder("Spy");
        holder.offenseReduction = 25;
        state.appoint(holder, 80);
        when(faction.getEspionage()).thenReturn(state);
        when(actor.getName()).thenReturn("Spy");
        when(actor.getUniqueId()).thenReturn(holder.playerId);
        when(faction.isMemberIgnoreCase("Spy")).thenReturn(true);
        try (var databases = mockConstruction(Database.class)) {
            assertFalse(EspionageService.setSabotage(actor, faction, true, 100));
            assertFalse(EspionageService.setSabotage(actor, faction, false, 75));
            assertEquals(25, holder.offenseReduction);
            assertEquals(0, holder.defenseReduction);
        }
    }

    @Test void failedFounderRollRemainsPendingAndRetriesWithoutUsingTheFreeAppointment() throws Exception {
        var faction = mock(Faction.class);
        var founder = mock(Player.class);
        var player = mock(net.tfminecraft.rpcharacters.objects.PlayerData.class);
        var character = mock(net.tfminecraft.rpcharacters.objects.RPCharacter.class);
        var state = new EspionageState();
        when(faction.getEspionage()).thenReturn(state);
        when(faction.getLeader()).thenReturn("Founder");
        when(faction.isLeader("Founder")).thenReturn(true);
        when(faction.getMembers()).thenReturn(List.of("Founder"));
        when(founder.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getActiveCharacter()).thenReturn(character);
        when(character.getId()).thenReturn("founder-character");
        var registry = mock(CharacterAptitudes.class);
        when(registry.aptitude(eq("founder-character"), any())).thenThrow(new java.io.IOException("Test save failure"))
                .thenThrow(new java.io.IOException("Test retry failure")).thenReturn(84);
        var field = EspionageService.class.getDeclaredField("characterAptitudes");
        field.setAccessible(true);
        var previous = field.get(null);
        var previousPlugin = net.tfminecraft.simplefactions.SimpleFactions.plugin;
        var plugin = mock(net.tfminecraft.simplefactions.SimpleFactions.class);
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class);
             var players = mockStatic(net.tfminecraft.rpcharacters.managers.PlayerManager.class);
             var databases = mockConstruction(Database.class, (database, context) -> when(database.saveFactionChecked(faction)).thenReturn(true))) {
            field.set(null, registry);
            net.tfminecraft.simplefactions.SimpleFactions.plugin = plugin;
            bukkit.when(org.bukkit.Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class));
            var plugins = mock(org.bukkit.plugin.PluginManager.class);
            when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
            bukkit.when(org.bukkit.Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(() -> org.bukkit.Bukkit.getPlayerExact("Founder")).thenReturn(founder);
            players.when(() -> net.tfminecraft.rpcharacters.managers.PlayerManager.get(founder)).thenReturn(player);
            EspionageService.initializeFounder(faction);
            assertNull(state.getSpymaster());
            assertTrue(state.hasPendingFounder());
            assertEquals(0, state.appointmentCount(SpecialPosition.SPYMASTER));
            var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(state), EspionageState.class);
            assertTrue(restored.hasPendingFounder());
            assertEquals("founder-character", restored.pendingFounderCharacter(SpecialPosition.SPYMASTER));
            assertEquals(1, databases.constructed().size());
            verify(databases.constructed().getFirst()).saveFactionChecked(faction);
            bukkit.when(() -> org.bukkit.Bukkit.getPlayerExact("Founder")).thenReturn(null);
            EspionageService.initializeFounder(faction);
            assertNull(state.getSpymaster(), "Offline retry must not finalize a zero-aptitude office");
            assertTrue(state.hasPendingFounder());
            assertEquals(1, databases.constructed().size(), "An unchanged pending identity does not need another save");
            bukkit.when(() -> org.bukkit.Bukkit.getPlayerExact("Founder")).thenReturn(founder);
            assertNull(EspionageService.spymaster(faction));
            assertEquals(1, databases.constructed().size(), "Retrying the same character does not rewrite the pending identity");
            assertEquals(84, EspionageService.spymaster(faction).aptitude);
            assertEquals("founder-character", state.getSpymaster().characterId);
            assertFalse(state.hasPendingFounder());
            assertNull(state.pendingFounderCharacter(SpecialPosition.SPYMASTER));
            assertTrue(state.getSpymaster().automatic);
            assertEquals(0, state.appointmentCount(SpecialPosition.SPYMASTER));
            assertEquals(2, databases.constructed().size(), "The completed appointment is saved after its pending identity");
        } finally {
            field.set(null, previous);
            net.tfminecraft.simplefactions.SimpleFactions.plugin = previousPlugin;
        }
    }

    @Test void failedPendingIdentitySaveRestoresBindingAndRetriesForNewAndExistingFounders() throws Exception {
        var registry = mock(CharacterAptitudes.class);
        when(registry.aptitude(eq("new-character"), any())).thenThrow(new java.io.IOException("Test aptitude save failure"));
        var field = EspionageService.class.getDeclaredField("characterAptitudes");
        field.setAccessible(true);
        var previous = field.get(null);
        var previousPlugin = net.tfminecraft.simplefactions.SimpleFactions.plugin;
        var plugin = mock(net.tfminecraft.simplefactions.SimpleFactions.class);
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        field.set(null, registry);
        net.tfminecraft.simplefactions.SimpleFactions.plugin = plugin;
        try {
            for (String oldIdentity : new String[]{null, "old-character"}) {
                var faction = mock(Faction.class);
                var founder = mock(Player.class);
                var data = mock(net.tfminecraft.rpcharacters.objects.PlayerData.class);
                var character = mock(net.tfminecraft.rpcharacters.objects.RPCharacter.class);
                var state = new EspionageState();
                if (oldIdentity != null) state.pendingFounder(SpecialPosition.SPYMASTER, oldIdentity);
                when(faction.getEspionage()).thenReturn(state);
                when(faction.getLeader()).thenReturn("Founder");
                when(faction.isLeader("Founder")).thenReturn(true);
                when(faction.getMembers()).thenReturn(List.of("Founder"));
                when(founder.getUniqueId()).thenReturn(UUID.randomUUID());
                when(data.getActiveCharacter()).thenReturn(character);
                when(character.getId()).thenReturn("new-character");
                var saves = new java.util.concurrent.atomic.AtomicInteger();
                try (var bukkit = mockStatic(org.bukkit.Bukkit.class);
                     var players = mockStatic(net.tfminecraft.rpcharacters.managers.PlayerManager.class);
                     var databases = mockConstruction(Database.class, (database, context) ->
                             when(database.saveFactionChecked(faction)).thenReturn(saves.incrementAndGet() > 1))) {
                    bukkit.when(org.bukkit.Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class));
                    var plugins = mock(org.bukkit.plugin.PluginManager.class);
                    when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
                    bukkit.when(org.bukkit.Bukkit::getPluginManager).thenReturn(plugins);
                    bukkit.when(() -> org.bukkit.Bukkit.getPlayerExact("Founder")).thenReturn(founder);
                    players.when(() -> net.tfminecraft.rpcharacters.managers.PlayerManager.get(founder)).thenReturn(data);
                    EspionageService.initializeFounder(faction);
                    assertTrue(state.hasPendingFounder(), "Failed saving a new founder must retain the retry intent");
                    assertEquals(oldIdentity, state.pendingFounderCharacter(SpecialPosition.SPYMASTER));
                    assertNull(EspionageService.spymaster(faction));
                    assertEquals("new-character", state.pendingFounderCharacter(SpecialPosition.SPYMASTER));
                    assertEquals(0, state.appointmentCount(SpecialPosition.SPYMASTER));
                    assertEquals(2, databases.constructed().size());
                    for (Database database : databases.constructed()) verify(database).saveFactionChecked(faction);
                    verify(faction, never()).getBank();
                }
            }
        } finally {
            field.set(null, previous);
            net.tfminecraft.simplefactions.SimpleFactions.plugin = previousPlugin;
        }
    }

    @Test void removingAPendingFounderPreventsAutomaticReassignment() {
        var state = new EspionageState();
        state.pendingFounder(SpecialPosition.SPYMASTER);
        state.removeSpymaster();
        assertFalse(state.hasPendingFounder());
        assertNull(state.getSpymaster());
    }

    @Test void emptyLoadedOfficeMapsCanBeSnapshottedAndRestored() {
        var state = JsonUtil.GSON.fromJson("{\"positions\":{},\"appointments\":{}}", EspionageState.class);
        var previous = state.snapshotOffices();
        state.appoint(holder("Spy"), 70);
        state.restoreOffices(previous);
        assertNull(state.getSpymaster());
        assertEquals(0, state.appointmentCount(SpecialPosition.SPYMASTER));
    }

    private static SpecialPositionAssignment holder(String name) {
        var holder = new SpecialPositionAssignment();
        holder.playerName = name;
        holder.playerId = UUID.randomUUID();
        holder.characterId = name;
        return holder;
    }
}
