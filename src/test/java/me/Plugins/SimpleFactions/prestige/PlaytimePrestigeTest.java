package me.Plugins.SimpleFactions.prestige;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;

class PlaytimePrestigeTest {

	private static final long HOUR = 3600L;

	private double previousCap;

	@BeforeEach
	void setUp() {
		previousCap = Cache.maxPlaytimePrestigeExponent;
		Cache.maxPlaytimePrestigeExponent = 5;
	}

	@AfterEach
	void tearDown() {
		Cache.maxPlaytimePrestigeExponent = previousCap;
	}

	@Test
	void contribution_doublesEveryHundredHours() {
		assertEquals(1.0, PlaytimePrestige.contribution(0), 1e-9);
		assertEquals(2.0, PlaytimePrestige.contribution(100 * HOUR), 1e-9);
		assertEquals(4.0, PlaytimePrestige.contribution(200 * HOUR), 1e-9);
		assertEquals(8.0, PlaytimePrestige.contribution(300 * HOUR), 1e-9);
		assertEquals(16.0, PlaytimePrestige.contribution(400 * HOUR), 1e-9);
		assertEquals(32.0, PlaytimePrestige.contribution(500 * HOUR), 1e-9);
	}

	/** The curve has to flatten, or one veteran eventually outweighs a whole nation. */
	@Test
	void contribution_flattensAtTheCap() {
		assertEquals(32.0, PlaytimePrestige.contribution(1000 * HOUR), 1e-9);
		assertEquals(32.0, PlaytimePrestige.contribution(50000 * HOUR), 1e-9);
		assertEquals(32.0, PlaytimePrestige.maxContribution(), 1e-9);
	}

	@Test
	void contribution_treatsMissingTimeAsAFreshCharacter() {
		assertEquals(1.0, PlaytimePrestige.contribution(0), 1e-9);
		assertEquals(1.0, PlaytimePrestige.contribution(-500), 1e-9);
	}

	@Test
	void contribution_risesSmoothlyBetweenDoublings() {
		double half = PlaytimePrestige.contribution(50 * HOUR);
		assertTrue(half > 1.0 && half < 2.0, "expected between 1 and 2 but was " + half);
		assertEquals(Math.sqrt(2.0), half, 1e-9);
	}

	@Test
	void contribution_followsTheConfiguredCap() {
		Cache.maxPlaytimePrestigeExponent = 3;
		assertEquals(8.0, PlaytimePrestige.contribution(500 * HOUR), 1e-9);
		assertEquals(8.0, PlaytimePrestige.maxContribution(), 1e-9);
	}
}
