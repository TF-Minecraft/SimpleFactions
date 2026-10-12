package net.tfminecraft.simplefactions.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.map.MapSystem;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterSessionStartEvent;

class LeaderCharactersTest {

    @AfterEach
    void resetProbe() {
        LeaderCharacters.reset();
    }

    private static void online(Map<String, String> active) {
        LeaderCharacters.setProbe(player -> active.get(player));
    }

    @Test
    void readsTheActiveCharacterOfAnOnlineLeader() {
        online(Map.of("rushork", "Grunk the Bold"));
        LeaderCharacters.Remembered result = LeaderCharacters.resolve("rushork", null, null);
        assertEquals("Grunk the Bold", result.name());
        assertEquals("rushork", result.player());
    }

    @Test
    void keepsTheRememberedNameWhileTheLeaderIsOffline() {
        online(Map.of());
        LeaderCharacters.Remembered result =
                LeaderCharacters.resolve("rushork", "Grunk the Bold", "RUSHORK");
        assertEquals("Grunk the Bold", result.name());
    }

    @Test
    void anOnlineLeaderOverridesAnOldName() {
        online(Map.of("rushork", "Grunk the Second"));
        assertEquals("Grunk the Second",
                LeaderCharacters.resolve("rushork", "Grunk the Bold", "rushork").name());
    }

    @Test
    void aNewLeaderNeverInheritsTheOldLeadersCharacter() {
        online(Map.of());
        LeaderCharacters.Remembered result =
                LeaderCharacters.resolve("newking", "Grunk the Bold", "rushork");
        assertNull(result.name());
        assertNull(result.player());
    }

    @Test
    void stripsColourCodesAndBlankNames() {
        online(Map.of("a", "§x§a§3§a§1§8§4§lAelin", "b", "   ", "c", "§6Brann"));
        assertEquals("Aelin", LeaderCharacters.resolve("a", null, null).name());
        assertNull(LeaderCharacters.resolve("b", null, null).name());
        assertEquals("Brann", LeaderCharacters.resolve("c", null, null).name());
    }

    @Test
    void noLeaderNoName() {
        assertNull(LeaderCharacters.resolve(null, "x", "y").name());
        assertNull(LeaderCharacters.resolve(" ", "x", "y").name());
    }

    @Test
    void withoutRpCharactersNothingIsKnown() {
        assertNull(LeaderCharacters.resolve("rushork", null, null).name());
    }

    @Test
    void withoutRpCharactersARememberedNameIsDropped() {
        assertNull(LeaderCharacters.resolve("rushork", "Grunk the Bold", "rushork").name());
        assertNull(LeaderCharacters.resolve("rushork", "Grunk the Bold", "rushork").player());
    }

    @Test
    void anUnavailableProbeIsNotMistakenForAnOfflineLeader() {
        LeaderCharacters.setProbe(new LeaderCharacters.Probe() {
            @Override
            public String activeCharacterName(String player) {
                return null;
            }

            @Override
            public boolean available() {
                return false;
            }
        });
        assertNull(LeaderCharacters.resolve("rushork", "Grunk the Bold", "rushork").name());
    }

    @Test
    void refreshingARealmReportsOnlyARealChange() {
        online(Map.of("rushork", "Grunk the Second"));
        Faction faction = mock(Faction.class);
        when(faction.getLeader()).thenReturn("rushork");
        when(faction.getLeaderCharacter()).thenReturn("Grunk the Bold");
        when(faction.getLeaderCharacterOf()).thenReturn("rushork");
        assertTrue(LeaderCharacters.refresh(faction));
        verify(faction).rememberLeaderCharacter("Grunk the Second", "rushork");

        when(faction.getLeaderCharacter()).thenReturn("Grunk the Second");
        assertFalse(LeaderCharacters.refresh(faction));
    }

    @Test
    void refreshingLeavesARealmsOwnGuildToTheRealm() {
        online(Map.of("rushork", "Grunk the Second"));
        Guild base = mock(Guild.class);
        when(base.isBase()).thenReturn(true);
        assertFalse(LeaderCharacters.refresh(base));
        verify(base, never()).rememberLeaderCharacter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        Guild guild = mock(Guild.class);
        when(guild.getLeader()).thenReturn("rushork");
        assertTrue(LeaderCharacters.refresh(guild));
        verify(guild).rememberLeaderCharacter("Grunk the Second", "rushork");
    }

    @Test
    void sharedStartImmediatelyRefreshesMatchingRealmAndGuildOnly() {
        try (var identity = new SharedLeaderCharactersTest.Fixture(); var world = new LeaderWorld()) {
            Player leader = identity.player("rushork");
            CharacterSession ready = identity.activate(leader, UUID.randomUUID(), "Grunk the Bold");

            new LeaderCharacterListener().onCharacterStart(new CharacterSessionStartEvent(leader, ready));

            verify(world.realm).rememberLeaderCharacter("Grunk the Bold", "rushork");
            verify(world.guild).rememberLeaderCharacter("Grunk the Bold", "rushork");
            verify(world.base, never()).rememberLeaderCharacter(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
            verify(world.unrelated, never()).rememberLeaderCharacter(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
            verify(world.map).markLeaderNamesChanged();
            identity.bukkit.verify(org.bukkit.Bukkit::getScheduler, never());
        }
    }

    @Test
    void staleStartCannotRefreshNamesAfterAToBToAOrProviderLoss() {
        try (var identity = new SharedLeaderCharactersTest.Fixture(); var world = new LeaderWorld()) {
            Player leader = identity.player("rushork");
            UUID characterA = UUID.randomUUID();
            CharacterSession originalA = identity.activate(leader, characterA, "Original A");
            CharacterSession b = identity.activate(leader, UUID.randomUUID(), "B");
            CharacterSession freshA = identity.activate(leader, characterA, "Fresh A");
            LeaderCharacterListener listener = new LeaderCharacterListener();

            listener.onCharacterStart(new CharacterSessionStartEvent(leader, originalA));
            listener.onCharacterStart(new CharacterSessionStartEvent(leader, b));
            identity.unregister();
            listener.onCharacterStart(new CharacterSessionStartEvent(leader, freshA));
            verify(world.realm, never()).rememberLeaderCharacter(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
            verify(world.map, never()).markLeaderNamesChanged();

            identity.register();
            listener.onCharacterStart(new CharacterSessionStartEvent(leader, freshA));
            verify(world.realm).rememberLeaderCharacter("Fresh A", "rushork");
            verify(world.guild).rememberLeaderCharacter("Fresh A", "rushork");
            verify(world.map).markLeaderNamesChanged();
        }
    }

    @Test
    void startRequiresOnlineOriginalConnectionAndCorrectSessionOwner() {
        try (var identity = new SharedLeaderCharactersTest.Fixture(); var world = new LeaderWorld()) {
            Player leader = identity.player("rushork");
            UUID owner = leader.getUniqueId();
            CharacterSession ready = identity.activate(leader, UUID.randomUUID(), "Grunk");
            LeaderCharacterListener listener = new LeaderCharacterListener();
            when(leader.isOnline()).thenReturn(false);
            listener.onCharacterStart(new CharacterSessionStartEvent(leader, ready));
            when(leader.isOnline()).thenReturn(true);
            identity.connections.put(owner, mock(Player.class));
            listener.onCharacterStart(new CharacterSessionStartEvent(leader, ready));
            identity.connections.put(owner, leader);
            CharacterSession foreign = new CharacterSession(
                    new CharacterRef(UUID.randomUUID(), UUID.randomUUID()), 123);
            identity.sessions.put(foreign.ref().ownerId(), foreign);
            listener.onCharacterStart(new CharacterSessionStartEvent(leader, foreign));
            verify(world.realm, never()).rememberLeaderCharacter(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
            verify(world.map, never()).markLeaderNamesChanged();

            listener.onCharacterStart(new CharacterSessionStartEvent(leader, ready));
            verify(world.realm).rememberLeaderCharacter("Grunk", "rushork");
        }
    }

    @Test
    void sharedUnknownPreservesRealmAndGuildNamesButProviderLossClearsBoth() {
        try (var identity = new SharedLeaderCharactersTest.Fixture(); var world = new LeaderWorld()) {
            when(world.realm.getLeaderCharacter()).thenReturn("Remembered ruler");
            when(world.realm.getLeaderCharacterOf()).thenReturn("RUSHORK");
            when(world.guild.getLeaderCharacter()).thenReturn("Remembered guild leader");
            when(world.guild.getLeaderCharacterOf()).thenReturn("RUSHORK");

            assertFalse(LeaderCharacters.refresh(world.realm));
            assertFalse(LeaderCharacters.refresh(world.guild));
            verify(world.realm).rememberLeaderCharacter("Remembered ruler", "rushork");
            verify(world.guild).rememberLeaderCharacter("Remembered guild leader", "rushork");

            identity.unregister();
            assertTrue(LeaderCharacters.refresh(world.realm));
            assertTrue(LeaderCharacters.refresh(world.guild));
            verify(world.realm).rememberLeaderCharacter(null, null);
            verify(world.guild).rememberLeaderCharacter(null, null);
        }
    }

    private static final class LeaderWorld implements AutoCloseable {
        private final List<Faction> previousFactions = FactionManager.factions;
        private final MapSystem previousMap = FactionManager.map;
        final Faction realm = mock(Faction.class);
        final Guild guild = mock(Guild.class);
        final Guild base = mock(Guild.class);
        final Guild unrelated = mock(Guild.class);
        final MapSystem map = mock(MapSystem.class);

        LeaderWorld() {
            when(realm.getLeader()).thenReturn("rushork");
            when(guild.getLeader()).thenReturn("rushork");
            when(base.isBase()).thenReturn(true);
            when(unrelated.getLeader()).thenReturn("anotheraccount");
            GuildHandler handler = mock(GuildHandler.class);
            when(handler.getGuilds()).thenReturn(Arrays.asList(null, base, guild, unrelated));
            when(realm.getGuildHandler()).thenReturn(handler);
            FactionManager.factions = Arrays.asList(null, realm);
            FactionManager.map = map;
        }

        @Override public void close() {
            FactionManager.factions = previousFactions;
            FactionManager.map = previousMap;
        }
    }
}
