package net.tfminecraft.simplefactions.inactivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.economy.FrozenReads;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.utils.FactionCleanup;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterSession;

/**
 * Async income previews count member shares (stability, upkeep). Whether an inactive member
 * is online asks TLibs, which refuses other threads, so previews read the set captured with
 * their copy on the main thread (Main, 11-11: every trade, law and favour estimate failed).
 */
class FrozenInactiveMembersTest {
    private static final int AWAY = InactivityRules.DAYS_UNTIL_INACTIVE;

    private final List<Faction> savedFactions = new ArrayList<>();
    private PersistenceFilesFixture disk;
    private Field history;
    private Object savedHistory;
    private Characters characters;

    @BeforeEach
    void setUp() throws Exception {
        disk = new PersistenceFilesFixture();
        history = FactionCleanup.class.getDeclaredField("offlineDays");
        history.setAccessible(true);
        savedHistory = history.get(null);
        // No realms, so capture reads no treasuries and only the inactive members matter here.
        savedFactions.addAll(FactionManager.factions);
        FactionManager.factions.clear();
        characters = new Characters();
        Members.setResolver(characters);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            Members.setResolver(null);
            FactionManager.factions.clear();
            FactionManager.factions.addAll(savedFactions);
            history.set(null, savedHistory);
        } finally {
            disk.close();
        }
    }

    private void days(Map<String, Integer> days) throws Exception {
        disk.write("Cache/logins.json", JsonUtil.GSON.toJson(days));
        history.set(null, null);
    }

    private static MockedStatic<Bukkit> server(Player... online) {
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        Server server = mock(Server.class);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> List.of(online));
        return bukkit;
    }

    @Test
    void previewsReadTheInactiveMembersCapturedOnTheMainThread() throws Exception {
        days(Map.of("bob", AWAY, "carl", AWAY + 10, "dana", AWAY - 1, "dummy_ghost", AWAY + 40));
        Player carl = mock(Player.class);
        characters.playing.put(carl, "carl");
        List<String> realm = List.of("alice", "bob", "carl", "dana", "dummy_ghost");

        FrozenReads frozen;
        try (var bukkit = server(carl)) {
            frozen = FrozenReads.capture();
            // The copy answers as the live check does for each kind of member.
            for (String member : List.of("bob", "carl", "dana", "dummy_ghost", "erin")) {
                assertEquals(InactivityService.isMemberInactive(member),
                        frozen.read(() -> InactivityService.isMemberInactive(member)), member);
            }

            // Off the main thread TLibs refuses the online lookup, as it did on Main.
            characters.refuse = true;
            assertThrows(IllegalStateException.class, () -> InactivityService.isMemberInactive("bob"));

            assertTrue(frozen.read(() -> InactivityService.isMemberInactive("bob")));
            assertFalse(frozen.read(() -> InactivityService.isMemberInactive("carl")), "online at capture");
            assertFalse(frozen.read(() -> InactivityService.isMemberInactive("dana")), "under the threshold");
            assertFalse(frozen.read(() -> InactivityService.isMemberInactive("dummy_ghost")), "not tracked");
            assertFalse(frozen.read(() -> InactivityService.isMemberInactive("erin")), "never counted");
            assertFalse(frozen.read(() -> InactivityService.isMemberInactive(null)));
        }

        // Day counts written after the copy belong to the next preview, not this one.
        days(Map.of());
        List<String> active = List.of("alice", "carl", "dana", "dummy_ghost");
        assertEquals(active, CompletableFuture.supplyAsync(() -> frozen.read(() -> InactivityService.activeOnly(realm))).get());
        assertFalse(InactivityService.isMemberInactive("bob"));
        assertNull(FrozenReads.inactiveMembers());
    }

    @Test
    void noOneAwayNeedsNoCharacterLookup() throws Exception {
        days(Map.of("bob", AWAY - 1));
        characters.refuse = true;
        try (var bukkit = server(mock(Player.class))) {
            assertEquals(Set.of(), InactivityService.captureInactiveMembers());
        }
    }

    @Test
    void playersWithoutACharacterAreNobodyOnline() throws Exception {
        days(Map.of("bob", AWAY));
        try (var bukkit = server(mock(Player.class))) {
            assertEquals(Set.of("bob"), InactivityService.captureInactiveMembers());
        }
    }

    @Test
    void withoutAServerNobodyIsOnline() throws Exception {
        // Raw JSON: Gson leaves null entries out when it writes.
        disk.write("Cache/logins.json", "{\"bob\":" + AWAY + ",\"carl\":null}");
        history.set(null, null);
        assertEquals(List.of("carl"), FactionCleanup.membersOffline(days -> days == 0));
        assertEquals(Set.of("bob"), InactivityService.captureInactiveMembers());
    }

    @Test
    void unreadableDayCountsMarkNobodyInactive() throws Exception {
        disk.write("Cache/logins.json", "{");
        history.set(null, null);
        assertEquals(List.of(), FactionCleanup.membersOffline(days -> true));
        assertEquals(Set.of(), InactivityService.captureInactiveMembers());
    }

    /** Plays the given characters and, once told to, refuses lookups like TLibs off the main thread. */
    private static final class Characters implements Members.Resolver {
        private final Map<Player, String> playing = new HashMap<>();
        private volatile boolean refuse;

        private void guard() {
            if (refuse) throw new IllegalStateException("Character identity must be used on the main thread");
        }

        @Override
        public String key(Player player) {
            guard();
            return playing.get(player);
        }

        @Override
        public Optional<Player> online(String key) {
            guard();
            return playing.entrySet().stream().filter(entry -> entry.getValue().equals(key)).map(Map.Entry::getKey).findFirst();
        }

        @Override
        public Optional<CharacterInfo> info(String key) {
            guard();
            return Optional.empty();
        }

        @Override
        public Optional<CharacterSession> session(Player player) {
            return Optional.empty();
        }

        @Override
        public boolean isCurrent(CharacterSession session) {
            return false;
        }
    }
}
