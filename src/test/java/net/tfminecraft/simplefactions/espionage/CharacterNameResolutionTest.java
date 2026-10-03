package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class CharacterNameResolutionTest {
    @org.junit.jupiter.api.Test
    void invitationCompletionReturnsOnlyCurrentTokenAndGuardsPastEndOfName() {
        var names = java.util.List.of("Lady Raven", "Lady Rose", "Lady", "Account", "Lady Raven");
        assertEquals(java.util.List.of("Lady Raven", "Lady Rose", "Lady"),
                CharacterNames.inviteCompletions(names, new String[]{"invite", "La"}));
        assertEquals(java.util.List.of("Raven", "Rose"),
                CharacterNames.inviteCompletions(names, new String[]{"invite", "Lady", "R"}));
        assertEquals(java.util.List.of("Raven", "Rose"),
                CharacterNames.inviteCompletions(names, new String[]{"invite", "lady", ""}));
        assertTrue(CharacterNames.inviteCompletions(names, new String[]{"invite", "Lady", "Raven", ""}).isEmpty());
        assertTrue(CharacterNames.inviteCompletions(names, new String[]{"invite", "Account", "", ""}).isEmpty());
        assertEquals(java.util.List.of("Account"), CharacterNames.inviteCompletions(names, new String[]{"invite", "acc"}));
    }
    private Player player(String name) {
        var player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        return player;
    }
    @Test
    void resolvesFullCharacterNamesAndExactAccountsWithoutPrefixMatches() {
        var first = player("FirstAccount");
        var second = player("SecondAccount");
        var players = List.of(first, second);
        java.util.function.Function<Player, String> names = player -> player == first ? "\u00a7aLady Rowan Ash" : "Lord Vale";
        assertSame(first, CharacterNames.resolve("lady rowan ash", players, names).player());
        assertSame(second, CharacterNames.resolve("secondaccount", players, names).player());
        assertNull(CharacterNames.resolve("Lady Rowan", players, names).player());
        assertNull(CharacterNames.resolve("First", players, names).player());
    }
    @Test
    void duplicateCharactersRequireAnAccountAndAccountsTakePrecedence() {
        var first = player("Rowan");
        var second = player("OtherAccount");
        var players = List.of(first, second);
        assertTrue(CharacterNames.resolve("Shared Name", players, ignored -> "Shared Name").ambiguous());
        assertNull(CharacterNames.resolve("Shared Name", players, ignored -> "Shared Name").player());
        assertSame(first, CharacterNames.resolve("Rowan", players, ignored -> "Rowan").player());
    }
    @Test
    void bypassDisplaysBothNamesWhileNormalViewsDisplayCharacterOnly() {
        var viewer = player("Viewer");
        try (var names = mockStatic(CharacterNames.class, CALLS_REAL_METHODS)) {
            names.when(() -> CharacterNames.of("Account")).thenReturn("Lady Rowan Ash");
            assertEquals("Lady Rowan Ash", CharacterNames.display(viewer, "Account"));
            when(viewer.hasPermission(EspionageService.BYPASS_PERMISSION)).thenReturn(true);
            assertEquals("Lady Rowan Ash \u00a77(Account)", CharacterNames.display(viewer, "Account"));
        }
    }
}
