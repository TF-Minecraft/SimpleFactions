package me.Plugins.SimpleFactions.War.campaign.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CampaignClockTest {
	@BeforeEach
	void setUp() {
		CampaignClock.resetForTests();
	}

	@AfterEach
	void tearDown() {
		CampaignClock.resetForTests();
	}

	@Test
	void initialStateUsesRealTime() {
		assertFalse(CampaignClock.isSpoofed());
		assertEquals(Duration.ZERO, CampaignClock.getOffset());

		Instant before = Instant.now();
		Instant clockNow = CampaignClock.now();
		Instant after = Instant.now();

		assertFalse(clockNow.isBefore(before));
		assertFalse(clockNow.isAfter(after));
	}

	@Test
	void addIncreasesOffsetAndNow() {
		CampaignClock.add(Duration.ofHours(1));

		assertTrue(CampaignClock.isSpoofed());
		assertEquals(Duration.ofHours(1), CampaignClock.getOffset());

		Instant expectedMin = Instant.now().plus(Duration.ofHours(1)).minusSeconds(1);
		Instant expectedMax = Instant.now().plus(Duration.ofHours(1)).plusSeconds(1);
		Instant clockNow = CampaignClock.now();
		assertFalse(clockNow.isBefore(expectedMin));
		assertFalse(clockNow.isAfter(expectedMax));
	}

	@Test
	void addIsCumulative() {
		CampaignClock.add(Duration.ofHours(1));
		CampaignClock.add(Duration.ofMinutes(31));

		assertEquals(Duration.ofHours(1).plusMinutes(31), CampaignClock.getOffset());
	}

	@Test
	void resetClearsOffset() {
		CampaignClock.add(Duration.ofDays(1));
		CampaignClock.reset();

		assertFalse(CampaignClock.isSpoofed());
		assertEquals(Duration.ZERO, CampaignClock.getOffset());
	}

	@Test
	void addRejectsNull() {
		assertThrows(IllegalArgumentException.class, () -> CampaignClock.add(null));
	}
}
