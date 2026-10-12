package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.SimpleServicesManager;
import org.mockito.MockedStatic;
import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
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

    @Test void failedAppointmentRestoresOfficeHistoryUnrestAndWait() {
        long now = 30 * 86_400_000L;
        var faction = mock(Faction.class);
        var state = new EspionageState();
        var previous = holder("OldSpy");
        state.appoint(previous, 90, now - 3 * 86_400_000L);
        when(faction.getEspionage()).thenReturn(state);
        try (var databases = mockConstruction(Database.class)) {
            assertFalse(EspionageService.completeAndSaveAppointment(faction, holder("NewSpy"), 70, now));
            assertSame(previous, state.getSpymaster());
            assertEquals(1, state.appointmentCount(SpecialPosition.SPYMASTER));
            assertEquals(now - 3 * 86_400_000L, state.lastAppointedAt(SpecialPosition.SPYMASTER));
            assertTrue(state.unrestModifiers(now).isEmpty());
        }
        try (var databases = mockConstruction(Database.class, (database, context) -> when(database.saveFactionChecked(faction)).thenReturn(true))) {
            assertTrue(EspionageService.completeAndSaveAppointment(faction, holder("NewSpy"), 70, now));
            assertEquals(now, state.lastAppointedAt(SpecialPosition.SPYMASTER));
            assertEquals("NewSpy", state.getSpymaster().playerName);
            assertEquals(2, state.appointmentCount(SpecialPosition.SPYMASTER));
            assertEquals(-10.0, state.unrestModifiers(now).getFirst().getModifier(), 0.001);
            verify(faction, never()).getBank();
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
        UUID characterId = UUID.randomUUID();
        String identity = characterId.toString();
        var state = new EspionageState();
        when(faction.getEspionage()).thenReturn(state);
        when(faction.getLeader()).thenReturn("Founder");
        when(faction.isLeader("Founder")).thenReturn(true);
        when(faction.getMembers()).thenReturn(List.of("Founder"));
        when(founder.getUniqueId()).thenReturn(UUID.randomUUID());
        var registry = mock(CharacterAptitudes.class);
        when(registry.aptitude(eq(identity), any())).thenThrow(new java.io.IOException("Test save failure"))
                .thenThrow(new java.io.IOException("Test retry failure")).thenReturn(84);
        var field = EspionageService.class.getDeclaredField("characterAptitudes");
        field.setAccessible(true);
        var previous = field.get(null);
        var previousPlugin = net.tfminecraft.simplefactions.SimpleFactions.plugin;
        var plugin = mock(net.tfminecraft.simplefactions.SimpleFactions.class);
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        try (var ready = new ReadyFounder(founder, characterId, plugin);
             var databases = mockConstruction(Database.class, (database, context) -> when(database.saveFactionChecked(faction)).thenReturn(true))) {
            field.set(null, registry);
            net.tfminecraft.simplefactions.SimpleFactions.plugin = plugin;
            EspionageService.initializeFounder(faction);
            assertNull(state.getSpymaster());
            assertTrue(state.hasPendingFounder());
            assertEquals(0, state.appointmentCount(SpecialPosition.SPYMASTER));
            long boundAt = state.pendingFounderBoundAt(SpecialPosition.SPYMASTER);
            assertTrue(boundAt > 0);
            var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(state), EspionageState.class);
            assertTrue(restored.hasPendingFounder());
            assertEquals(identity, restored.pendingFounderCharacter(SpecialPosition.SPYMASTER));
            assertEquals(boundAt, restored.pendingFounderBoundAt(SpecialPosition.SPYMASTER));
            assertEquals(1, databases.constructed().size());
            verify(databases.constructed().getFirst()).saveFactionChecked(faction);
            ready.bukkit.when(() -> Bukkit.getPlayerExact("Founder")).thenReturn(null);
            EspionageService.initializeFounder(faction);
            assertNull(state.getSpymaster(), "Offline retry must not finalize a zero-aptitude office");
            assertTrue(state.hasPendingFounder());
            assertEquals(1, databases.constructed().size(), "An unchanged pending identity does not need another save");
            ready.bukkit.when(() -> Bukkit.getPlayerExact("Founder")).thenReturn(founder);
            assertNull(EspionageService.spymaster(faction));
            assertEquals(1, databases.constructed().size(), "Retrying the same character does not rewrite the pending identity");
            assertEquals(boundAt, state.pendingFounderBoundAt(SpecialPosition.SPYMASTER));
            assertEquals(84, EspionageService.spymaster(faction).aptitude);
            assertEquals(identity, state.getSpymaster().characterId);
            assertEquals(founder.getUniqueId(), state.getSpymaster().playerId);
            assertTrue(state.getSpymaster().characterBoundAt >= boundAt);
            assertEquals(0, state.getSpymaster().appointedAt);
            assertEquals(0, state.pendingFounderBoundAt(SpecialPosition.SPYMASTER));
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
        UUID characterId = UUID.randomUUID();
        String identity = characterId.toString();
        var registry = mock(CharacterAptitudes.class);
        when(registry.aptitude(eq(identity), any())).thenThrow(new java.io.IOException("Test aptitude save failure"));
        var field = EspionageService.class.getDeclaredField("characterAptitudes");
        field.setAccessible(true);
        var previous = field.get(null);
        var previousPlugin = net.tfminecraft.simplefactions.SimpleFactions.plugin;
        var plugin = mock(net.tfminecraft.simplefactions.SimpleFactions.class);
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        field.set(null, registry);
        net.tfminecraft.simplefactions.SimpleFactions.plugin = plugin;
        try {
            for (String oldIdentity : new String[]{null, UUID.randomUUID().toString()}) {
                var faction = mock(Faction.class);
                var founder = mock(Player.class);
                var state = JsonUtil.GSON.fromJson("{\"pendingFounderBoundAt\":null}", EspionageState.class);
                if (oldIdentity != null) state.pendingFounder(SpecialPosition.SPYMASTER, oldIdentity);
                long oldBoundAt = state.pendingFounderBoundAt(SpecialPosition.SPYMASTER);
                when(faction.getEspionage()).thenReturn(state);
                when(faction.getLeader()).thenReturn("Founder");
                when(faction.isLeader("Founder")).thenReturn(true);
                when(faction.getMembers()).thenReturn(List.of("Founder"));
                when(founder.getUniqueId()).thenReturn(UUID.randomUUID());
                var saves = new java.util.concurrent.atomic.AtomicInteger();
                try (var ready = new ReadyFounder(founder, characterId, plugin);
                     var databases = mockConstruction(Database.class, (database, context) ->
                             when(database.saveFactionChecked(faction)).thenReturn(saves.incrementAndGet() > 1))) {
                    EspionageService.initializeFounder(faction);
                    assertTrue(state.hasPendingFounder(), "Failed saving a new founder must retain the retry intent");
                    assertEquals(oldIdentity, state.pendingFounderCharacter(SpecialPosition.SPYMASTER));
                    assertEquals(oldBoundAt, state.pendingFounderBoundAt(SpecialPosition.SPYMASTER));
                    assertNull(EspionageService.spymaster(faction));
                    assertEquals(identity, state.pendingFounderCharacter(SpecialPosition.SPYMASTER));
                    long newBoundAt = state.pendingFounderBoundAt(SpecialPosition.SPYMASTER);
                    assertTrue(newBoundAt > 0);
                    var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(state), EspionageState.class);
                    assertEquals(identity, restored.pendingFounderCharacter(SpecialPosition.SPYMASTER));
                    assertEquals(newBoundAt, restored.pendingFounderBoundAt(SpecialPosition.SPYMASTER));
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
        var state = JsonUtil.GSON.fromJson("{\"positions\":{},\"appointments\":{},\"pendingFounderBoundAt\":null}", EspionageState.class);
        var previous = state.snapshotOffices();
        state.appoint(holder("Spy"), 70);
        state.restoreOffices(previous);
        assertNull(state.getSpymaster());
        assertEquals(0, state.appointmentCount(SpecialPosition.SPYMASTER));
        assertEquals(0, state.pendingFounderBoundAt(SpecialPosition.SPYMASTER));
        String characterId = UUID.randomUUID().toString();
        state.pendingFounder(SpecialPosition.SPYMASTER, characterId);
        assertEquals(characterId, state.pendingFounderCharacter(SpecialPosition.SPYMASTER));
        assertTrue(state.pendingFounderBoundAt(SpecialPosition.SPYMASTER) > 0);
    }

    private static final class ReadyFounder implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        private final SimpleServicesManager services = new SimpleServicesManager();
        private final CharacterIdentity provider = mock(CharacterIdentity.class);

        ReadyFounder(Player founder, UUID characterId, Plugin plugin) {
            UUID owner = founder.getUniqueId();
            CharacterRef ref = new CharacterRef(owner, characterId);
            CharacterSession session = new CharacterSession(ref, 1);
            CharacterInfo info = new CharacterInfo(ref, "Founder", CharacterStatus.ALIVE,
                    null, Set.of(), 0, Instant.EPOCH);
            Server server = mock(Server.class);
            PluginManager plugins = mock(PluginManager.class);
            when(server.getPluginManager()).thenReturn(plugins);
            when(server.getServicesManager()).thenReturn(services);
            when(founder.isOnline()).thenReturn(true);
            when(founder.getName()).thenReturn("Founder");
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(owner)).thenReturn(founder);
            bukkit.when(() -> Bukkit.getPlayerExact("Founder")).thenReturn(founder);
            when(provider.currentSession(founder)).thenReturn(Optional.of(session));
            when(provider.info(characterId)).thenReturn(Optional.of(info));
            when(provider.isCurrent(session)).thenReturn(true);
            services.register(CharacterIdentity.class, provider, plugin, ServicePriority.Normal);
        }

        @Override public void close() {
            try {
                services.unregister(CharacterIdentity.class, provider);
            } finally {
                bukkit.close();
            }
        }
    }

    private static SpecialPositionAssignment holder(String name) {
        var holder = new SpecialPositionAssignment();
        holder.playerName = name;
        holder.playerId = UUID.randomUUID();
        holder.characterId = name;
        return holder;
    }
}
