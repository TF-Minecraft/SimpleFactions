package net.tfminecraft.simplefactions.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.testsupport.SharedIdentity;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;

class MembersTest {

    @Test
    void aPlayerIsTheCharacterOfTheirCurrentSessionOnly() {
        try (var identity = new SharedIdentity()) {
            Player player = identity.connect("Steve");
            CharacterRef ada = identity.character(player.getUniqueId(), "Ada");
            CharacterRef bea = identity.character(player.getUniqueId(), "Bea");
            assertNull(Members.key(player), "no session, no member");
            assertTrue(Members.session(player).isEmpty());

            CharacterSession first = identity.play(player, ada);
            assertEquals(Members.key(ada), Members.key(player));
            assertEquals(ada.characterId().toString(), Members.key(player));
            assertTrue(Members.is(player, Members.key(ada)));
            assertFalse(Members.is(player, Members.key(bea)));
            assertEquals(player, Members.player(Members.key(ada)));
            assertTrue(Members.online(Members.key(bea)).isEmpty(), "the same account playing Ada is not Bea");
            assertEquals("Ada", Members.name(player));
            assertTrue(Members.isCurrent(first));

            identity.play(player, bea);
            assertFalse(Members.isCurrent(first));
            assertEquals(Members.key(bea), Members.key(player));
            assertFalse(Members.isOnline(Members.key(ada)));
            assertTrue(Members.isOnline(Members.key(bea)));
        }
    }

    @Test
    void aSessionOfAnotherAccountIsNotThePlayers() {
        try (var identity = new SharedIdentity()) {
            Player player = identity.connect("Steve");
            CharacterRef foreign = identity.character(UUID.randomUUID(), "Foreign");
            identity.sessions.put(player.getUniqueId(), new CharacterSession(foreign, 9));
            assertNull(Members.key(player));
            assertEquals("Steve", Members.name(player));
            identity.unregister();
            assertNull(Members.key(player));
            assertFalse(Members.isCurrent(new CharacterSession(foreign, 9)));
            assertTrue(Members.info(Members.key(foreign)).isEmpty());
        }
    }

    @Test
    void displayNamesCharactersWithoutLeakingKeys() {
        try (var identity = new SharedIdentity()) {
            CharacterRef ada = identity.character(identity.account("Steve"), "§6Ada");
            CharacterRef blank = identity.character(identity.account("Alex"), "  ");
            assertEquals("Ada", Members.display(Members.key(ada)));
            assertEquals("Unknown", Members.display(Members.key(blank)));
            assertEquals("Unknown", Members.display(Members.key(UUID.randomUUID())));
            assertEquals("Unknown", Members.display((String) null));
            assertEquals("Unknown", Members.display(" "));
            assertEquals("Unresolved member", Members.display(Members.QUARANTINED + "q1"));
            assertEquals("dummy_1", Members.display("dummy_1"));
            assertEquals(List.of("Ada", "Unknown"), Members.display(List.of(Members.key(ada), Members.key(UUID.randomUUID()))));
            assertEquals(List.of(), Members.display((List<String>) null));
            assertEquals(ada, Members.ref(Members.key(ada)).orElseThrow());
            assertEquals(ada.ownerId(), Members.accountId(Members.key(ada)).orElseThrow());
            assertTrue(Members.ref("dummy_1").isEmpty());
            assertTrue(Members.ref(null).isEmpty());
            assertTrue(Members.info(null).isEmpty());
            assertEquals("Unknown", Members.name(null));
        }
    }

    @Test
    void staffSeeWhichAccountPlaysACharacter() {
        try (var identity = new SharedIdentity()) {
            CharacterRef ada = identity.character(identity.account("Steve"), "Ada");
            Player staff = mock(Player.class);
            when(staff.hasPermission(anyString())).thenReturn(true);
            Player player = mock(Player.class);
            assertEquals("Ada §7(Steve)", Members.display(staff, Members.key(ada)));
            assertEquals("Ada", Members.display(player, Members.key(ada)));
            assertEquals("Unknown", Members.display(staff, Members.key(UUID.randomUUID())));
            assertEquals("Steve", Members.accountName(Members.key(ada)));
            assertNull(Members.accountName(Members.key(UUID.randomUUID())));
            identity.bukkit.when(() -> Bukkit.getOfflinePlayer(org.mockito.ArgumentMatchers.any(UUID.class)))
                    .thenThrow(new IllegalStateException("no user cache"));
            assertNull(Members.accountName(Members.key(ada)));
            identity.bukkit.when(Bukkit::getServer).thenReturn(null);
            assertNull(Members.accountName(Members.key(ada)));
            identity.bukkit.when(Bukkit::getServer).thenReturn(identity.server);
        }
    }

    @Test
    void typedNamesFindMembersByCharacterOrOnlineAccount() {
        try (var identity = new SharedIdentity()) {
            Player steve = identity.connect("Steve");
            CharacterRef ada = identity.character(steve.getUniqueId(), "Ada Lovelace");
            identity.play(steve, ada);
            CharacterRef twinA = identity.character(identity.account("A"), "Twin");
            CharacterRef twinB = identity.character(identity.account("B"), "Twin");
            String adaKey = Members.key(ada);
            Set<String> keys = Set.of(adaKey, Members.key(twinA), Members.key(twinB));

            assertEquals(adaKey, Members.find(keys, "Steve"));
            assertEquals(adaKey, Members.find(keys, "\"ada lovelace\""));
            assertEquals(adaKey, Members.find(keys, adaKey.toUpperCase()));
            assertNull(Members.find(keys, "Twin"), "ambiguous");
            assertNull(Members.find(keys, "Nobody"));
            assertNull(Members.find(Set.of(Members.key(twinA)), "Steve"), "online, but not a member here");
            assertNull(Members.find(null, "x"));
            assertNull(Members.find(keys, null));

            assertEquals(adaKey, Members.findOnline("Steve"));
            assertEquals(adaKey, Members.findOnline("Ada Lovelace"));
            assertEquals(Members.key(twinA), Members.findOnline(Members.key(twinA)));
            assertNull(Members.findOnline("Twin"));
            assertNull(Members.findOnline(null));
            assertEquals(adaKey, Members.ofAccountName("Steve"));
            assertNull(Members.ofAccountName(null));
        }
    }

    @Test
    void findOnlineRefusesAmbiguousCharacterNames() {
        try (var identity = new SharedIdentity()) {
            Player one = identity.connect("One");
            Player two = identity.connect("Two");
            identity.play(one, identity.character(one.getUniqueId(), "Twin"));
            identity.play(two, identity.character(two.getUniqueId(), "Twin"));
            assertNull(Members.findOnline("Twin"));
            Player sender = mock(Player.class);
            assertNull(Members.resolveOnline(sender, "Twin"));
            verify(sender).sendMessage("§cSeveral characters share that name. Use their Minecraft name.");
            assertNull(Members.resolveOnline(sender, "Nobody"));
            verify(sender).sendMessage("§cNo online player found by that character or Minecraft name.");
            assertEquals(one, Members.resolveOnline(sender, "One"));
            assertNull(Members.resolveOnline(null, "Nobody"));
        }
    }

    @Test
    void resolveOnlineFindsAUniqueCharacterName() {
        try (var identity = new SharedIdentity()) {
            Player one = identity.connect("One");
            identity.play(one, identity.character(one.getUniqueId(), "Solo"));
            assertEquals(one, Members.resolveOnline(null, "\"solo\""));
        }
    }

    @Test
    void noServerMeansNobodyIsOnline() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            assertNull(Members.findOnline("Steve"));
            assertNull(Members.accountName(Members.key(UUID.randomUUID())));
            bukkit.when(Bukkit::getServer).thenThrow(new IllegalStateException("no server"));
            assertNull(Members.findOnline("Steve"));
        }
    }

    @Test
    void memberAccountsResolveCharactersLegacyNamesAndBlanks() {
        try (var identity = new SharedIdentity();
             var offline = mockStatic(net.tfminecraft.denareconomy.accounts.OfflineModifier.class)) {
            CharacterRef ada = identity.character(identity.account("Steve"), "Ada");
            UUID legacy = UUID.randomUUID();
            offline.when(() -> net.tfminecraft.denareconomy.accounts.OfflineModifier.playerId("Steve"))
                    .thenReturn(legacy);
            assertNull(net.tfminecraft.simplefactions.utils.MemberAccounts.accountId(null));
            assertNull(net.tfminecraft.simplefactions.utils.MemberAccounts.accountId("  "));
            assertEquals(ada.ownerId(), net.tfminecraft.simplefactions.utils.MemberAccounts.accountId(Members.key(ada)));
            assertNull(net.tfminecraft.simplefactions.utils.MemberAccounts.accountId(Members.key(UUID.randomUUID())),
                    "a character nobody owns has no account");
            assertEquals(legacy, net.tfminecraft.simplefactions.utils.MemberAccounts.accountId("Steve"),
                    "a non-character key falls back to the Minecraft name lookup");
        }
    }

    @Test
    void theCharacterIdShortcutAnswersLikeTheFullParse() {
        String canonical = UUID.randomUUID().toString();
        // A UUID's own string is always 36 characters with dashes at 8, 13, 18 and 23, and no
        // UTF-16 character but the dash equals a dash ignoring case, so the round trip refused
        // every key the shortcut now refuses.
        for (int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c++) {
            if (String.valueOf((char) c).equalsIgnoreCase("-") != (c == '-')) fail("U+" + Integer.toHexString(c));
        }
        for (int dash : new int[] {8, 13, 18, 23}) {
            for (char c : "0aA+_ \u2010\u2011\u2212\ufe63\uff0d".toCharArray()) {
                String key = canonical.substring(0, dash) + c + canonical.substring(dash + 1);
                assertEquals(parsedCharacterId(key), Members.characterId(key), key);
            }
        }
        // Every length up to 72, lenient forms that parse but do not round-trip, and case and
        // whitespace variants.
        String longer = canonical + canonical;
        for (int length = 0; length <= longer.length(); length++) {
            assertEquals(parsedCharacterId(longer.substring(0, length)), Members.characterId(longer.substring(0, length)));
        }
        for (String key : List.of("1-1-1-1-1", "0-0-0-0-0", "00000000-0000-0000-0000-0000000000000",
                "+0000000-0000-0000-0000-000000000000", canonical.toUpperCase(), " " + canonical)) {
            assertEquals(parsedCharacterId(key), Members.characterId(key), key);
        }
    }

    /** The parse alone, as {@link Members#characterId} was before it ruled keys out first. */
    private static UUID parsedCharacterId(String key) {
        if (key == null) return null;
        try {
            UUID id = UUID.fromString(key);
            return id.toString().equalsIgnoreCase(key) ? id : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Test
    void keysAreLowerCaseCharacterIds() {
        UUID id = UUID.randomUUID();
        assertEquals(id.toString(), Members.key(id));
        assertNull(Members.key((UUID) null));
        assertNull(Members.key((CharacterRef) null));
        assertNull(Members.key((Player) null));
        assertEquals(id, Members.characterId(id.toString().toUpperCase()));
        assertNull(Members.characterId("1-1-1-1-1"));
        assertNull(Members.characterId("Steve"));
        assertNull(Members.characterId(null));
        // Each dash position on its own, and dashed 36-character non-IDs (one does not parse,
        // one parses through Unicode digits but is not the UUID's own string).
        String canonical = id.toString();
        for (int dash : new int[] {8, 13, 18, 23}) {
            assertNull(Members.characterId(canonical.substring(0, dash) + "0" + canonical.substring(dash + 1)));
        }
        assertNull(Members.characterId("zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz"));
        assertNull(Members.characterId("\uff10" + canonical.substring(1)));
        assertTrue(Members.isQuarantined("quarantine:q1"));
        assertFalse(Members.isQuarantined(null));
        assertTrue(Members.online(null).isEmpty());
        assertTrue(Members.online(" ").isEmpty());
        assertTrue(Members.session(null).isEmpty());
        assertFalse(Members.isCurrent(null));
    }

    @Test
    void completionsOfferTheUntypedRestOfAName() {
        assertEquals(List.of(), Members.completions(List.of("Ada"), new String[] {"invite"}));
        assertEquals(List.of("Lovelace"), Members.completions(java.util.Arrays.asList("Ada Lovelace", null, "Bea"),
                new String[] {"invite", "ada", "lo"}));
        assertEquals(List.of("Ada"), Members.completions(List.of("§6Ada", "Ada"), new String[] {"invite", "a"}));
    }

    @Test
    void theResolverCanBeReplacedAndRestored() {
        Members.Resolver custom = mock(Members.Resolver.class);
        Player player = mock(Player.class);
        when(custom.key(player)).thenReturn("custom");
        Members.setResolver(custom);
        assertEquals("custom", Members.key(player));
        Members.setResolver(null);
        try (var identity = new SharedIdentity()) {
            assertNull(Members.key(player));
        }
    }
}
