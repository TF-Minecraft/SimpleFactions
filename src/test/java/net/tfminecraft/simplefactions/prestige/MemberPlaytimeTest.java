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
import net.tfminecraft.simplefactions.identity.Members;

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
    void memberTimeIsTheirOwnCharactersNotTheAccountsLastPlayed() {
        try (var fixture = new SharedFixture()) {
            UUID owner = UUID.randomUUID();
            String older = fixture.character(owner, CharacterStatus.DEAD, 2 * HOUR);
            UUID lastId = UUID.randomUUID();
            fixture.lastPlayed.put(owner, snapshot(owner, lastId, CharacterStatus.ALIVE, 500 * HOUR));
            assertEquals(7200, MemberPlaytime.secondsFor(older), "Dead or offline characters keep their time");
            assertNull(MemberPlaytime.secondsFor(Members.key(lastId)), "An unknown character contributes nothing");
            assertNull(MemberPlaytime.secondsFor("Alice"), "A name is not a member key");
            verify(fixture.provider, never()).lastPlayed(any());
            fixture.bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
            verify(fixture.plugin, never()).getDataFolder();
        }
    }

    @Test
    void sharedLookupTracksProviderArrivalAndLossWithoutReinstallation() {
        try (var fixture = new SharedFixture()) {
            String alice = fixture.character(UUID.randomUUID(), CharacterStatus.ALIVE, HOUR);
            fixture.unregister();
            assertNull(MemberPlaytime.secondsFor(alice));
            fixture.register();
            assertEquals(3600, MemberPlaytime.secondsFor(alice));
        }
    }

    @Test
    void sharedSecondsSaturateBeforeConvertingToInteger() {
        try (var fixture = new SharedFixture()) {
            String endless = fixture.character(UUID.randomUUID(), CharacterStatus.ALIVE, Long.MAX_VALUE);
            assertEquals(Integer.MAX_VALUE, MemberPlaytime.secondsFor(endless));
            String fresh = fixture.character(UUID.randomUUID(), CharacterStatus.ALIVE, 0);
            assertEquals(0, MemberPlaytime.secondsFor(fresh));
            assertEquals(1.0, MemberPlaytime.totalFor(List.of(fresh)), 1e-9);
        }
    }

    @Test
    void sharedRosterKeepsPrestigeFormulaAndSkipsUnknownMembers() {
        try (var fixture = new SharedFixture()) {
            UUID account = UUID.randomUUID();
            String alice = fixture.character(account, CharacterStatus.ALIVE, 500 * HOUR);
            String bob = fixture.character(UUID.randomUUID(), CharacterStatus.DEAD, 100 * HOUR);
            String cara = fixture.character(account, CharacterStatus.ALIVE, 0);
            String ghost = Members.key(UUID.randomUUID());
            assertEquals(35.0, MemberPlaytime.totalFor(List.of(alice, bob, cara, ghost, "Pending")), 1e-9);
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
        final Map<UUID, CharacterInfo> infos = new HashMap<>();
        final Map<UUID, CharacterInfo> lastPlayed = new HashMap<>();

        SharedFixture() {
            PluginManager plugins = mock(PluginManager.class);
            when(server.getPluginManager()).thenReturn(plugins);
            when(server.getServicesManager()).thenReturn(services);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(provider.info(any())).thenAnswer(call -> Optional.ofNullable(infos.get(call.getArgument(0))));
            when(provider.lastPlayed(any())).thenAnswer(call -> Optional.ofNullable(lastPlayed.get(call.getArgument(0))));
            register();
            Members.useSharedIdentity();
            MemberPlaytime.useSharedIdentity();
        }

        /** A stored character and its member key. */
        String character(UUID owner, CharacterStatus status, long seconds) {
            UUID characterId = UUID.randomUUID();
            infos.put(characterId, snapshot(owner, characterId, status, seconds));
            return Members.key(characterId);
        }

        void register() {
            services.register(CharacterIdentity.class, provider, plugin, ServicePriority.Normal);
        }

        void unregister() {
            services.unregister(CharacterIdentity.class, provider);
        }

        @Override public void close() {
            try {
                unregister();
                MemberPlaytime.reset();
            } finally {
                bukkit.close();
            }
        }
    }
}
