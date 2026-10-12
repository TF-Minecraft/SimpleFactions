package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import net.tfminecraft.simplefactions.espionage.OfficeOptionalIntegrationsTest.IdentityFixture;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CharacterNameResolutionTest {
    @org.junit.jupiter.api.BeforeEach void guardEverything() { net.tfminecraft.simplefactions.testsupport.EspionageModes.guardEverything(); }
    @org.junit.jupiter.api.AfterEach void resetEspionage() { net.tfminecraft.simplefactions.testsupport.EspionageModes.reset(); }

    @Test void foreignFallbackHidesAccountsOnlyWhenSharedIdentityIsAvailable() {
        var account = "NoActiveCharacterAccount";
        var viewer = player("ForeignViewer");
        var faction = mock(net.tfminecraft.simplefactions.objects.Faction.class);
        EspionageTestFixtures.protect(faction);
        try (var f = new IdentityFixture(account)) {
            assertEquals("Unknown", CharacterNames.forForeign(account));
            assertEquals("Unknown", CharacterNames.display(viewer, account, faction));
            assertEquals(account, CharacterNames.of(account), "Invitations keep account fallback");
            when(faction.isMember("ForeignViewer")).thenReturn(true);
            assertEquals(account, CharacterNames.display(viewer, account, faction));
            when(viewer.hasPermission(EspionageService.BYPASS_PERMISSION)).thenReturn(true);
            assertEquals(account + " \u00a77(" + account + ")", CharacterNames.display(viewer, account, faction));
            f.registered = false;
            assertEquals(account, CharacterNames.forForeign(account), "Servers without a provider keep account names");
        }
    }

    @Test void onlineNamesUseLiveAliasesWithoutReadingCanonicalDirectoryNames() {
        try (var f = new IdentityFixture()) {
            var alice = f.activate(UUID.randomUUID());
            f.alias = "A Hooded Stranger";
            assertEquals(f.alias, CharacterNames.of(f.account));
            assertEquals(f.alias, CharacterNames.forForeign(f.account));
            f.activate(UUID.randomUUID());
            f.alias = "Bob's Mask";
            assertEquals(f.alias, CharacterNames.forForeign(f.account));
            f.activate(alice.ref().characterId());
            f.alias = "Alice's New Mask";
            assertEquals(f.alias, CharacterNames.of(f.account));
            verify(f.provider, never()).info(any());
            verify(f.provider, never()).lastPlayed(any());
        }
    }

    @Test void onlineNamesDropRpCharactersColouringSoMenusAndReportsKeepTheirOwn() {
        try (var f = new IdentityFixture()) {
            f.activate(UUID.randomUUID());
            // RPCharacters' white reset followed by a two-colour hex gradient.
            f.alias = "\u00a7f\u00a7x\u00a7f\u00a74\u00a7e\u00a74\u00a7a\u00a7aLa\u00a7x\u00a7c\u00a72\u00a7b\u00a7e\u00a7a\u00a77dy \u00a7oRaven";
            assertEquals("Lady Raven", CharacterNames.of(f.account));
            assertEquals("Lady Raven", CharacterNames.forForeign(f.account));
            var viewer = player("Staff");
            when(viewer.hasPermission(EspionageService.BYPASS_PERMISSION)).thenReturn(true);
            assertEquals("Lady Raven \u00a77(" + f.account + ")", CharacterNames.display(viewer, f.account));
        }
    }

    @Test void providerArrivalAndRemovalTakeEffectWithoutANameCache() {
        try (var f = new IdentityFixture()) {
            f.registered = false;
            assertEquals(f.account, CharacterNames.forForeign(f.account));
            f.registered = true;
            assertEquals("Unknown", CharacterNames.forForeign(f.account));
            assertEquals(f.account, CharacterNames.of(f.account));
            f.activate(UUID.randomUUID());
            assertEquals(f.alias, CharacterNames.forForeign(f.account));
            f.registered = false;
            assertEquals(f.account, CharacterNames.forForeign(f.account));
            assertEquals(f.account, CharacterNames.of(f.account));
            assertEquals("Unknown", CharacterNames.forForeign(null));
        }
    }

    @Test void pendingOrWrongOwnerOnlineSessionCannotFallBackToLastPlayedName() {
        try (var f = new IdentityFixture()) {
            UUID characterId = UUID.randomUUID();
            f.lastPlayed.put(f.owner, OfficeOptionalIntegrationsTest.info(new CharacterRef(f.owner, characterId), CharacterStatus.ALIVE));
            assertEquals("Unknown", CharacterNames.forForeign(f.account));
            assertEquals(f.account, CharacterNames.of(f.account));
            f.token = new CharacterSession(new CharacterRef(UUID.randomUUID(), characterId), 1);
            assertEquals("Unknown", CharacterNames.forForeign(f.account));
            verify(f.provider, never()).displayName(any());
            verify(f.provider, never()).lastPlayed(any());
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"switch-back", "provider-lost", "reconnect", "offline"})
    void aliasCallbackCannotPublishANameFromAnEndedSession(String change) {
        try (var f = new IdentityFixture()) {
            var original = f.activate(UUID.randomUUID());
            when(f.provider.displayName(f.player)).thenAnswer(call -> {
                switch (change) {
                    case "switch-back" -> {
                        f.activate(UUID.randomUUID());
                        f.activate(original.ref().characterId());
                    }
                    case "provider-lost" -> f.registered = false;
                    case "reconnect" -> {
                        Player replacement = mock(Player.class);
                        when(replacement.getUniqueId()).thenReturn(f.owner);
                        when(replacement.isOnline()).thenReturn(true);
                        f.connection = replacement;
                    }
                    case "offline" -> when(f.player.isOnline()).thenReturn(false);
                    default -> throw new AssertionError(change);
                }
                return "Old Mask";
            });
            assertEquals("Unknown", CharacterNames.forForeign(f.account));
            verify(f.provider, never()).info(any());
            verify(f.provider, never()).lastPlayed(any());
        }
    }

    @Test void cachedOfflineIdentityUsesOwnerCheckedLastPlayedWithoutNetworkLookup() {
        try (var f = new IdentityFixture()) {
            f.connection = null;
            assertEquals("Unknown", CharacterNames.forForeign(f.account));
            assertEquals(f.account, CharacterNames.of(f.account));
            verify(f.provider, never()).lastPlayed(any());
            OfflinePlayer cached = mock(OfflinePlayer.class);
            when(cached.getUniqueId()).thenReturn(f.owner);
            f.bukkit.when(() -> Bukkit.getOfflinePlayerIfCached(f.account)).thenReturn(cached);
            assertEquals("Unknown", CharacterNames.forForeign(f.account));
            var ref = new CharacterRef(f.owner, UUID.randomUUID());
            f.lastPlayed.put(f.owner, OfficeOptionalIntegrationsTest.info(ref, CharacterStatus.DEAD));
            assertEquals("Canonical Secret", CharacterNames.forForeign(f.account));
            assertEquals("Canonical Secret", CharacterNames.of(f.account));
            f.lastPlayed.put(f.owner, OfficeOptionalIntegrationsTest.info(new CharacterRef(UUID.randomUUID(), ref.characterId()), CharacterStatus.ALIVE));
            assertEquals("Unknown", CharacterNames.forForeign(f.account));
            assertEquals(f.account, CharacterNames.of(f.account));
            f.bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
            verify(f.provider, never()).info(any());
            verify(f.provider, never()).displayName(any());
        }
    }
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
        assertEquals(java.util.List.of("Lady Raven"), CharacterNames.inviteCompletions(
                java.util.List.of("\u00a76Lady \u00a7eRaven"), new String[]{"invite", "La"}));
        assertEquals(java.util.List.of("Raven"), CharacterNames.inviteCompletions(
                java.util.List.of("\u00a76Lady \u00a7eRaven"), new String[]{"invite", "Lady", "R"}));
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
