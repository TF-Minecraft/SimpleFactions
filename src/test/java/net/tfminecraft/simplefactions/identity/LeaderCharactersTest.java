package net.tfminecraft.simplefactions.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
}
