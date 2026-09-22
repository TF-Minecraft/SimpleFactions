package me.Plugins.SimpleFactions.prestige;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;

class MemberPlaytimeTest {

	private static final long HOUR = 3600L;

	/** Stands in for the RPCharacters playtime index. */
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

	/** Without RPCharacters the term must vanish, leaving prestige exactly as it was. */
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
}
