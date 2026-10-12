package net.tfminecraft.simplefactions.prestige;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
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
import net.tfminecraft.tlibs.character.Characters;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;

class MemberPlaytimeTest {

	private static final long HOUR = 3600L;

	/** Isolates the prestige formula from identity lookup. */
	private static final class FakeProbe implements MemberPlaytime.Probe {
		private final Map<String, Integer> seconds = new HashMap<>();

		void known(String player, long secondsOnline) {
			seconds.put(player.toLowerCase(Locale.ROOT), (int) secondsOnline);
		}

		@Override
		public Integer secondsFor(String player) {
			return seconds.get(player.toLowerCase(Locale.ROOT));
		}
	}

	private double previousCap;

	@BeforeEach
	void setUp() {
		previousCap = Cache.maxPlaytimePrestigeExponent;
		Cache.maxPlaytimePrestigeExponent = 5;
		MemberPlaytime.reset();
	}

	@AfterEach
	void tearDown() {
		Cache.maxPlaytimePrestigeExponent = previousCap;
		MemberPlaytime.reset();
	}

	/** A pass remembers every answer, unknown members included, and forgets them afterwards. */
	@Test
	void withinPass_asksOncePerMemberAndForgetsAfterwards() {
		Map<String, Integer> lookups = new HashMap<>();
		MemberPlaytime.setProbe(player -> {
			lookups.merge(player, 1, Integer::sum);
			return player.equals("Alice") ? 7200 : null;
		});
		MemberPlaytime.withinPass(() -> {
			MemberPlaytime.totalFor(List.of("Alice", "Bob"));
			MemberPlaytime.withinPass(() -> MemberPlaytime.totalFor(List.of("Alice", "Bob")));
			assertEquals(7200, MemberPlaytime.secondsFor("Alice"));
			assertNull(MemberPlaytime.secondsFor("Bob"));
		});
		assertEquals(Map.of("Alice", 1, "Bob", 1), lookups);
		assertThrows(IllegalStateException.class, () -> MemberPlaytime.withinPass(() -> {
			MemberPlaytime.secondsFor("Alice");
			throw new IllegalStateException("pass failed");
		}));
		MemberPlaytime.secondsFor("Alice");
		assertEquals(3, lookups.get("Alice"), "A failed pass does not leave answers behind");
	}

	/** Without a playtime source the term leaves prestige unchanged. */
	@Test
	void defaultProbe_contributesNothing() {
		assertFalse(MemberPlaytime.isTracked());
		assertNull(MemberPlaytime.secondsFor("Alice"));
		assertEquals(0.0, MemberPlaytime.totalFor(List.of("Alice", "Bob")), 1e-9);
	}

	@Test
	void totalFor_sumsEveryMember() {
		FakeProbe probe = new FakeProbe();
		probe.known("Alice", 500 * HOUR);
		probe.known("Bob", 100 * HOUR);
		probe.known("Cara", 0);
		MemberPlaytime.setProbe(probe);

		assertTrue(MemberPlaytime.isTracked());
		assertEquals(35.0, MemberPlaytime.totalFor(List.of("Alice", "Bob", "Cara")), 1e-9);
	}

	/**
	 * A member the index has never seen is skipped, not counted as fresh. Otherwise a
	 * roster of names that never held a character would still earn prestige.
	 */
	@Test
	void totalFor_skipsUnknownMembers() {
		FakeProbe probe = new FakeProbe();
		probe.known("Alice", 200 * HOUR);
		MemberPlaytime.setProbe(probe);

		assertEquals(4.0, MemberPlaytime.totalFor(List.of("Alice", "Ghost", "Nobody")), 1e-9);
	}

	@Test
	void totalFor_handlesEmptyAndNullRosters() {
		MemberPlaytime.setProbe(new FakeProbe());
		assertEquals(0.0, MemberPlaytime.totalFor(null), 1e-9);
		assertEquals(0.0, MemberPlaytime.totalFor(List.of()), 1e-9);
	}

	@Test
	void secondsFor_ignoresBlankNames() {
		FakeProbe probe = new FakeProbe();
		probe.known("Alice", HOUR);
		MemberPlaytime.setProbe(probe);

		assertNull(MemberPlaytime.secondsFor(null));
		assertNull(MemberPlaytime.secondsFor("  "));
	}

	@Test
	void reset_restoresTheDefaultProbe() {
		FakeProbe probe = new FakeProbe();
		probe.known("Alice", 300 * HOUR);
		MemberPlaytime.setProbe(probe);
		assertEquals(8.0, MemberPlaytime.totalFor(List.of("Alice")), 1e-9);

		MemberPlaytime.reset();
		assertFalse(MemberPlaytime.isTracked());
		assertEquals(0.0, MemberPlaytime.totalFor(List.of("Alice")), 1e-9);
	}

	@Test
	void setProbe_nullFallsBackToTheDefault() {
		MemberPlaytime.setProbe(new FakeProbe());
		MemberPlaytime.setProbe(null);
		assertFalse(MemberPlaytime.isTracked());
	}

    @Test
    void readyOnlineTimeNeverFallsBackToAnEarlierCharacterWhilePending() {
        try (var fixture = new SharedFixture()) {
            Player alice = fixture.player("Alice");
            UUID owner = alice.getUniqueId();
            fixture.lastPlayed.put(owner, snapshot(owner, UUID.randomUUID(), CharacterStatus.ALIVE, 500 * HOUR));
            assertNull(MemberPlaytime.secondsFor("Alice"));
            assertEquals(0.0, MemberPlaytime.totalFor(List.of("Alice")), 1e-9);
            fixture.activate(alice, UUID.randomUUID(), 2 * HOUR);
            assertEquals(7200, MemberPlaytime.secondsFor("Alice"));
            fixture.sessions.remove(owner);
            assertNull(MemberPlaytime.secondsFor("Alice"));
            verify(fixture.provider, never()).lastPlayed(any());
        }
    }

    @Test
    void sharedLookupTracksProviderArrivalLossAndAbsentServerWithoutReinstallation() {
        try (var fixture = new SharedFixture()) {
            Player alice = fixture.player("Alice");
            fixture.activate(alice, UUID.randomUUID(), HOUR);
            fixture.unregister();
            assertNull(MemberPlaytime.secondsFor("Alice"));
            fixture.register();
            assertEquals(3600, MemberPlaytime.secondsFor("Alice"));
            fixture.unregister();
            assertNull(MemberPlaytime.secondsFor("Alice"));
            fixture.register();
            fixture.bukkit.when(Bukkit::getServer).thenReturn(null);
            assertNull(MemberPlaytime.secondsFor("Alice"));
            fixture.bukkit.when(Bukkit::getServer).thenReturn(fixture.server);
            assertEquals(3600, MemberPlaytime.secondsFor("Alice"));
        }
    }

    @Test
    void offlineTimeUsesCachedOwnersLastPlayedIncludingDeadAndHoldsUnknown() {
        try (var fixture = new SharedFixture()) {
            assertNull(MemberPlaytime.secondsFor("Uncached"));
            verify(fixture.provider, never()).lastPlayed(any());
            UUID owner = fixture.cached("Alice");
            assertNull(MemberPlaytime.secondsFor("Alice"));
            fixture.lastPlayed.put(owner, snapshot(UUID.randomUUID(), UUID.randomUUID(), CharacterStatus.DEAD, HOUR));
            assertNull(MemberPlaytime.secondsFor("Alice"));
            fixture.lastPlayed.put(owner, snapshot(owner, UUID.randomUUID(), CharacterStatus.DEAD, 2 * HOUR));
            assertEquals(7200, MemberPlaytime.secondsFor("Alice"));
            fixture.bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
            verify(fixture.plugin, never()).getDataFolder();
        }
    }

    @Test
    void onlineTimeRejectsForeignSessionOwnerAndWrongOrMissingInfo() {
        try (var fixture = new SharedFixture()) {
            Player alice = fixture.player("Alice");
            UUID owner = alice.getUniqueId();
            UUID characterId = UUID.randomUUID();
            CharacterSession session = fixture.activate(alice, characterId, HOUR);
            fixture.sessions.put(owner, new CharacterSession(new CharacterRef(UUID.randomUUID(), characterId), 88));
            assertNull(MemberPlaytime.secondsFor("Alice"));
            fixture.sessions.put(owner, session);
            fixture.infos.put(characterId, snapshot(UUID.randomUUID(), characterId, CharacterStatus.ALIVE, HOUR));
            assertNull(MemberPlaytime.secondsFor("Alice"));
            fixture.infos.put(characterId, snapshot(owner, UUID.randomUUID(), CharacterStatus.ALIVE, HOUR));
            assertNull(MemberPlaytime.secondsFor("Alice"));
            fixture.infos.remove(characterId);
            assertNull(MemberPlaytime.secondsFor("Alice"));
        }
    }

    @Test
    void snapshotFromOriginalACannotContributeAfterAToBToA() {
        try (var fixture = new SharedFixture()) {
            Player alice = fixture.player("Alice");
            UUID characterId = UUID.randomUUID();
            CharacterSession original = fixture.activate(alice, characterId, HOUR);
            CharacterInfo oldInfo = fixture.infos.get(characterId);
            when(fixture.provider.info(characterId)).thenAnswer(call -> {
                fixture.activate(alice, UUID.randomUUID(), 2 * HOUR);
                fixture.activate(alice, characterId, 3 * HOUR);
                return Optional.of(oldInfo);
            });
            assertNull(MemberPlaytime.secondsFor("Alice"));
            assertFalse(Characters.isCurrent(original));
            CharacterInfo fresh = fixture.infos.get(characterId);
            doReturn(Optional.of(fresh)).when(fixture.provider).info(characterId);
            assertEquals(10800, MemberPlaytime.secondsFor("Alice"));
        }
    }

    @Test
    void providerCallbackCannotContributeForDisconnectedOrReplacedPlayer() {
        for (boolean disconnect : new boolean[] {true, false}) {
            try (var fixture = new SharedFixture()) {
                Player alice = fixture.player("Alice");
                UUID owner = alice.getUniqueId();
                UUID characterId = UUID.randomUUID();
                CharacterSession original = fixture.activate(alice, characterId, HOUR);
                CharacterInfo info = fixture.infos.get(characterId);
                when(fixture.provider.info(characterId)).thenAnswer(call -> {
                    if (disconnect) when(alice.isOnline()).thenReturn(false);
                    else fixture.connections.put(owner, mock(Player.class));
                    return Optional.of(info);
                });
                assertNull(MemberPlaytime.secondsFor("Alice"));
                assertTrue(Characters.isCurrent(original));
                when(alice.isOnline()).thenReturn(true);
                fixture.connections.put(owner, alice);
                doReturn(Optional.of(info)).when(fixture.provider).info(characterId);
                assertEquals(3600, MemberPlaytime.secondsFor("Alice"));
            }
        }
    }

    @Test
    void sharedSecondsSaturateBeforeConvertingToInteger() {
        try (var fixture = new SharedFixture()) {
            Player alice = fixture.player("Alice");
            fixture.activate(alice, UUID.randomUUID(), Long.MAX_VALUE);
            assertEquals(Integer.MAX_VALUE, MemberPlaytime.secondsFor("Alice"));
            fixture.activate(alice, UUID.randomUUID(), 0);
            assertEquals(0, MemberPlaytime.secondsFor("Alice"));
            assertEquals(1.0, MemberPlaytime.totalFor(List.of("Alice")), 1e-9);
        }
    }

    @Test
    void sharedRosterKeepsPrestigeFormulaAndSkipsPendingOrUnknownAccounts() {
        try (var fixture = new SharedFixture()) {
            Player alice = fixture.player("Alice");
            fixture.activate(alice, UUID.randomUUID(), 500 * HOUR);
            UUID bob = fixture.cached("Bob");
            fixture.lastPlayed.put(bob, snapshot(bob, UUID.randomUUID(), CharacterStatus.DEAD, 100 * HOUR));
            Player cara = fixture.player("Cara");
            fixture.activate(cara, UUID.randomUUID(), 0);
            Player pending = fixture.player("Pending");
            UUID pendingOwner = pending.getUniqueId();
            fixture.lastPlayed.put(pendingOwner,
                    snapshot(pendingOwner, UUID.randomUUID(), CharacterStatus.ALIVE, Long.MAX_VALUE));
            assertEquals(35.0, MemberPlaytime.totalFor(List.of("Alice", "Bob", "Cara", "Pending", "Ghost")), 1e-9);
            verify(fixture.provider, never()).lastPlayed(pendingOwner);
        }
    }

    private static CharacterInfo snapshot(UUID owner, UUID characterId, CharacterStatus status, long seconds) {
        return new CharacterInfo(new CharacterRef(owner, characterId), "Character", status,
                null, Set.of(), seconds, Instant.EPOCH);
    }

    private static final class SharedFixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final SimpleServicesManager services = new SimpleServicesManager();
        final Server server = mock(Server.class);
        final Plugin plugin = mock(Plugin.class);
        final CharacterIdentity provider = mock(CharacterIdentity.class);
        final Map<UUID, CharacterSession> sessions = new HashMap<>();
        final Map<UUID, CharacterInfo> infos = new HashMap<>();
        final Map<UUID, CharacterInfo> lastPlayed = new HashMap<>();
        final Map<String, Player> online = new HashMap<>();
        final Map<UUID, Player> connections = new HashMap<>();
        final Map<String, OfflinePlayer> cached = new HashMap<>();
        private long generation;

        SharedFixture() {
            PluginManager plugins = mock(PluginManager.class);
            when(server.getPluginManager()).thenReturn(plugins);
            when(server.getServicesManager()).thenReturn(services);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenAnswer(call -> online.get(call.getArgument(0)));
            bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> connections.get(call.getArgument(0)));
            bukkit.when(() -> Bukkit.getOfflinePlayerIfCached(anyString())).thenAnswer(call -> cached.get(call.getArgument(0)));
            when(provider.currentSession(any())).thenAnswer(call -> {
                Player player = call.getArgument(0);
                return Optional.ofNullable(sessions.get(player.getUniqueId()));
            });
            when(provider.isCurrent(any())).thenAnswer(call -> {
                CharacterSession session = call.getArgument(0);
                return session != null && session.equals(sessions.get(session.ref().ownerId()));
            });
            when(provider.info(any())).thenAnswer(call -> Optional.ofNullable(infos.get(call.getArgument(0))));
            when(provider.lastPlayed(any())).thenAnswer(call -> Optional.ofNullable(lastPlayed.get(call.getArgument(0))));
            register();
            MemberPlaytime.useSharedIdentity();
        }

        Player player(String name) {
            Player player = mock(Player.class);
            UUID owner = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(owner);
            when(player.getName()).thenReturn(name);
            when(player.isOnline()).thenReturn(true);
            online.put(name, player);
            connections.put(owner, player);
            return player;
        }

        UUID cached(String name) {
            UUID owner = UUID.randomUUID();
            OfflinePlayer player = mock(OfflinePlayer.class);
            when(player.getUniqueId()).thenReturn(owner);
            cached.put(name, player);
            return owner;
        }

        CharacterSession activate(Player player, UUID characterId, long seconds) {
            UUID owner = player.getUniqueId();
            CharacterSession session = new CharacterSession(new CharacterRef(owner, characterId), ++generation);
            sessions.put(owner, session);
            infos.put(characterId, snapshot(owner, characterId, CharacterStatus.ALIVE, seconds));
            return session;
        }

        void register() {
            services.register(CharacterIdentity.class, provider, plugin, ServicePriority.Normal);
        }

        void unregister() {
            services.unregister(CharacterIdentity.class, provider);
        }

        @Override public void close() {
            try {
                bukkit.when(Bukkit::getServer).thenReturn(server);
                unregister();
                MemberPlaytime.reset();
            } finally {
                bukkit.close();
            }
        }
    }
}
