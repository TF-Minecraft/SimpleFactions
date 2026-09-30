package net.tfminecraft.simplefactions.inactivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.inactivity.DecayClock.Event;

class InactivityRulesTest {

	@Test
	void memberIsInactiveAfterTwentyOneDaysAndNotBefore() {
		assertFalse(InactivityRules.isInactive(20));
		assertTrue(InactivityRules.isInactive(21));
	}

	@Test
	void dummyMembersAreNotTracked() {
		assertFalse(InactivityRules.isTrackedMember("dummy_guard"));
		assertFalse(InactivityRules.isTrackedMember(" "));
		assertTrue(InactivityRules.isTrackedMember("Ada"));
	}

	@Test
	void fullInactivityNeedsEveryRealMember() {
		assertFalse(InactivityRules.isFullyInactive(0, 0));
		assertFalse(InactivityRules.isFullyInactive(2, 1));
		assertTrue(InactivityRules.isFullyInactive(2, 2));
	}

	@Test
	void outputFadesOnePercentAtATimeAndStopsAtNothing() {
		assertEquals(1.0, InactivityRules.outputFactor(0));
		assertEquals(0.99, InactivityRules.outputFactor(1));
		assertEquals(0.0, InactivityRules.outputFactor(100));
		assertEquals(0.0, InactivityRules.outputFactor(140));
	}

	@Test
	void provincesAreLostOldestFirstAndTheCapitalGoesLast() {
		assertEquals(Integer.valueOf(2), InactivityRules.nextProvinceToLose(List.of(1, 2, 3), 1));
		assertEquals(Integer.valueOf(3), InactivityRules.nextProvinceToLose(List.of(3), 1));
		assertEquals(Integer.valueOf(1), InactivityRules.nextProvinceToLose(List.of(1), 1));
		assertNull(InactivityRules.nextProvinceToLose(List.of(), 1));
	}

	@Test
	void capitalGoesWhenPrestigeNoLongerCoversIt() {
		assertFalse(InactivityRules.hasTooManyProvinces(500, 3, 50, false));
		assertTrue(InactivityRules.hasTooManyProvinces(40, 3, 50, true));
		assertFalse(InactivityRules.hasTooManyProvinces(80, 1, 50, false));
		assertTrue(InactivityRules.hasTooManyProvinces(0, 1, 50, false));
	}

	@Test
	void clockWaitsFourHoursThenStepsAndCaps() {
		DecayClock clock = new DecayClock();
		long start = 1_000_000L;
		assertEquals(Event.ARMED, clock.tick(start, true));
		assertEquals(0, clock.getPercent());
		assertEquals(Event.NONE, clock.tick(start + InactivityRules.DECAY_INTERVAL_MILLIS - 1, true));
		assertEquals(Event.STEPPED, clock.tick(start + InactivityRules.DECAY_INTERVAL_MILLIS, true));
		assertEquals(1, clock.getPercent());

		DecayClock capped = new DecayClock(100, start);
		assertEquals(Event.STEPPED, capped.tick(start, true));
		assertEquals(100, capped.getPercent());
		assertEquals(start + InactivityRules.DECAY_INTERVAL_MILLIS, capped.getNextAt());
	}

	@Test
	void loginClearsTheClockAndAnActiveMemberStopsDecay() {
		DecayClock clock = new DecayClock(12, 50L);
		assertEquals(Event.CLEARED, clock.tick(50L, false));
		assertFalse(clock.isSet());

		clock = new DecayClock(4, 50L);
		clock.clear();
		assertEquals(0, clock.getPercent());
		assertEquals(0L, clock.getNextAt());
	}

	@Test
	void missedTimeAppliesASingleStep() {
		DecayClock clock = new DecayClock(3, 1L);
		assertEquals(Event.STEPPED, clock.tick(1L + InactivityRules.DECAY_INTERVAL_MILLIS * 10, true));
		assertEquals(4, clock.getPercent());
	}
}
