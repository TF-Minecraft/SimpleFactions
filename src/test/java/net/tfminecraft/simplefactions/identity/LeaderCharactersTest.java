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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

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
}
