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
import net.tfminecraft.simplefactions.testsupport.SharedIdentity;

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
                LeaderCharacters.resolve("rushork", "Grunk the Bold", "rushork");
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
            public String characterName(String key) {
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
    void sharedStartRefreshesOnlyTheRealmAndGuildThatCharacterLeads() {
        try (var identity = new SharedIdentity()) {
            LeaderCharacters.useSharedIdentity();
            Player leader = identity.connect("rushork");
            CharacterRef grunk = identity.character(leader.getUniqueId(), "Grunk the Bold");
            try (var world = new LeaderWorld(Members.key(grunk))) {
                CharacterSession ready = identity.play(leader, grunk);

                new LeaderCharacterListener().onCharacterStart(new CharacterSessionStartEvent(leader, ready));

                verify(world.realm).rememberLeaderCharacter("Grunk the Bold", Members.key(grunk));
                verify(world.guild).rememberLeaderCharacter("Grunk the Bold", Members.key(grunk));
                verify(world.base, never()).rememberLeaderCharacter(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
                verify(world.unrelated, never()).rememberLeaderCharacter(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
                verify(world.map).markLeaderNamesChanged();
            }
        }
    }

    @Test
    void anotherCharacterOfTheLeadersPlayerRefreshesNothing() {
        try (var identity = new SharedIdentity()) {
            LeaderCharacters.useSharedIdentity();
            Player leader = identity.connect("rushork");
            CharacterRef grunk = identity.character(leader.getUniqueId(), "Grunk");
            CharacterRef other = identity.character(leader.getUniqueId(), "Someone else");
            try (var world = new LeaderWorld(Members.key(grunk))) {
                CharacterSession session = identity.play(leader, other);
                new LeaderCharacterListener().onCharacterStart(new CharacterSessionStartEvent(leader, session));
                verify(world.realm, never()).rememberLeaderCharacter(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
                verify(world.map, never()).markLeaderNamesChanged();
            }
        }
    }

    @Test
    void staleOrForeignStartsRefreshNothing() {
        try (var identity = new SharedIdentity()) {
            LeaderCharacters.useSharedIdentity();
            Player leader = identity.connect("rushork");
            UUID owner = leader.getUniqueId();
            CharacterRef grunk = identity.character(owner, "Grunk");
            try (var world = new LeaderWorld(Members.key(grunk))) {
                LeaderCharacterListener listener = new LeaderCharacterListener();
                CharacterSession stale = identity.play(leader, grunk);
                identity.play(leader, grunk);
                listener.onCharacterStart(new CharacterSessionStartEvent(leader, stale));
                CharacterSession ready = identity.play(leader, grunk);
                when(leader.isOnline()).thenReturn(false);
                listener.onCharacterStart(new CharacterSessionStartEvent(leader, ready));
                when(leader.isOnline()).thenReturn(true);
                identity.connections.put(owner, mock(Player.class));
                listener.onCharacterStart(new CharacterSessionStartEvent(leader, ready));
                identity.connections.put(owner, leader);
                CharacterSession foreign = new CharacterSession(
                        new CharacterRef(UUID.randomUUID(), grunk.characterId()), 123);
                listener.onCharacterStart(new CharacterSessionStartEvent(leader, foreign));
                verify(world.realm, never()).rememberLeaderCharacter(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());

                listener.onCharacterStart(new CharacterSessionStartEvent(leader, ready));
                verify(world.realm).rememberLeaderCharacter("Grunk", Members.key(grunk));
            }
        }
    }

    @Test
    void sharedUnknownPreservesRealmAndGuildNamesButProviderLossClearsBoth() {
        try (var identity = new SharedIdentity()) {
            LeaderCharacters.useSharedIdentity();
            String key = Members.key(UUID.randomUUID());
            try (var world = new LeaderWorld(key)) {
                when(world.realm.getLeaderCharacter()).thenReturn("Remembered ruler");
                when(world.realm.getLeaderCharacterOf()).thenReturn(key);
                when(world.guild.getLeaderCharacter()).thenReturn("Remembered guild leader");
                when(world.guild.getLeaderCharacterOf()).thenReturn(key);

                assertFalse(LeaderCharacters.refresh(world.realm));
                assertFalse(LeaderCharacters.refresh(world.guild));
                verify(world.realm).rememberLeaderCharacter("Remembered ruler", key);
                verify(world.guild).rememberLeaderCharacter("Remembered guild leader", key);

                identity.unregister();
                assertTrue(LeaderCharacters.refresh(world.realm));
                assertTrue(LeaderCharacters.refresh(world.guild));
                verify(world.realm).rememberLeaderCharacter(null, null);
                verify(world.guild).rememberLeaderCharacter(null, null);
            }
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

        LeaderWorld(String leader) {
            when(realm.getLeader()).thenReturn(leader);
            when(guild.getLeader()).thenReturn(leader);
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
