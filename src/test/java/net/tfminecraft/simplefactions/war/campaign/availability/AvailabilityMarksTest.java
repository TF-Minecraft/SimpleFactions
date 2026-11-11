package net.tfminecraft.simplefactions.war.campaign.availability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;

class AvailabilityMarksTest {
	/** Wednesday 18 November, morning: Thursday is the first date whose time is still open. */
	private static final Instant NOW = Instant.parse("2026-11-18T09:00:00Z");
	private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 20);

	private int[] saved;
	private War war;
	private Faction attacker;
	private final UUID ann = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		saved = new int[] {Cache.warBattleWindowStartHour, Cache.warBattleWindowEndHour, Cache.warAvailabilityDaysShown};
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 26;
		Cache.warAvailabilityDaysShown = 7;
		attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		war = new War(6, attacker, defender);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
	}

	@AfterEach
	void tearDown() {
		Cache.warBattleWindowStartHour = saved[0];
		Cache.warBattleWindowEndHour = saved[1];
		Cache.warAvailabilityDaysShown = saved[2];
	}

	@Test
	void aDateTakesOnlyValidHoursOnAnOpenDateFromAnAccountThatFights() {
		assertEquals(AvailabilityMarks.NOT_ELIGIBLE, AvailabilityMarks.setDate(war, ann, null, FRIDAY, Set.of(21), NOW));
		assertEquals(AvailabilityMarks.NOT_SHOWN, AvailabilityMarks.setDate(war, ann, attacker, FRIDAY.minusDays(2), Set.of(21), NOW));
		assertEquals(AvailabilityMarks.NOT_SHOWN, AvailabilityMarks.setDate(war, ann, attacker, FRIDAY.plusDays(9), Set.of(21), NOW));
		assertEquals(AvailabilityMarks.BAD_HOUR, AvailabilityMarks.setDate(war, ann, attacker, FRIDAY, Set.of(21, 26), NOW));
		assertTrue(war.getAvailability().marks().isEmpty());

		assertNull(AvailabilityMarks.setDate(war, ann, attacker, FRIDAY, Set.of(24, 25), NOW));
		assertEquals(Set.of(24, 25), war.getAvailability().marksOn(ann, FRIDAY), "Midnight and 01:00 are hours 24 and 25");

		war.getAvailability().setLastDayLockProcessed(FRIDAY);
		assertEquals(AvailabilityMarks.LOCKED, AvailabilityMarks.setDate(war, ann, attacker, FRIDAY, Set.of(), NOW));
		war.setBattleSchedulePhase(BattleSchedulePhase.IDLE);
		assertEquals(AvailabilityMarks.NO_SCHEDULE, AvailabilityMarks.setDate(war, ann, attacker, FRIDAY, Set.of(21), NOW));
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.end(net.tfminecraft.simplefactions.war.enums.WarEndReason.WHITE_PEACE);
		assertEquals(AvailabilityMarks.NO_SCHEDULE, AvailabilityMarks.setDate(war, ann, attacker, FRIDAY.plusDays(1), Set.of(21), NOW));
	}

	@Test
	void aBattleDayKeepsTakingMarksUntilItsTimeLock() {
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRIDAY, false));
		war.getAvailability().setLastDayLockProcessed(FRIDAY);
		assertNull(AvailabilityMarks.setDate(war, ann, attacker, FRIDAY, Set.of(22), NOW));
		assertEquals(AvailabilityMarks.NOT_SHOWN, AvailabilityMarks.setDate(war, ann, attacker, FRIDAY, Set.of(23),
				AvailabilityCalendar.timeLockAt(FRIDAY)), "Gone from the grid once its time is decided");
	}

	@Test
	void togglingAnHourOrAWholeDateAddsOrRemovesIt() {
		assertNull(AvailabilityMarks.toggleHour(war, ann, attacker, FRIDAY, 22, NOW));
		assertNull(AvailabilityMarks.toggleHour(war, ann, attacker, FRIDAY, 21, NOW));
		assertEquals(Set.of(21, 22), war.getAvailability().marksOn(ann, FRIDAY));
		assertNull(AvailabilityMarks.toggleHour(war, ann, attacker, FRIDAY, 22, NOW));
		assertEquals(Set.of(21), war.getAvailability().marksOn(ann, FRIDAY));
		assertEquals(AvailabilityMarks.BAD_HOUR, AvailabilityMarks.toggleHour(war, ann, attacker, FRIDAY, 20, NOW));

		assertNull(AvailabilityMarks.toggleDate(war, ann, attacker, FRIDAY, NOW));
		assertEquals(Set.of(21, 22, 23, 24, 25), war.getAvailability().marksOn(ann, FRIDAY), "Some marked: mark all");
		assertNull(AvailabilityMarks.toggleDate(war, ann, attacker, FRIDAY, NOW));
		assertTrue(war.getAvailability().marksOn(ann, FRIDAY).isEmpty(), "All marked: clear");
	}

	@Test
	void noneOfTheseDaysClearsWhatCanChangeUntilSomethingIsMarkedAgain() {
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRIDAY, false).withHour(22, false));
		war.getAvailability().setMarks(ann, FRIDAY, Set.of(22));
		assertNull(AvailabilityMarks.setDate(war, ann, attacker, FRIDAY.plusDays(1), Set.of(21), NOW));

		assertEquals(AvailabilityMarks.NOT_ELIGIBLE, AvailabilityMarks.setNone(war, ann, null, NOW));
		assertNull(AvailabilityMarks.setNone(war, ann, attacker, NOW));

		assertTrue(war.getAvailability().marksOn(ann, FRIDAY.plusDays(1)).isEmpty());
		assertEquals(Set.of(22), war.getAvailability().marksOn(ann, FRIDAY), "A day already decided keeps its marks");
		LocalDate lastShown = AvailabilityCalendar.visibleDates(war, NOW).get(6);
		assertEquals(lastShown, war.getAvailability().noneUntil(ann));
		assertTrue(AvailabilityMarks.saidNone(war, ann, lastShown));
		assertFalse(AvailabilityMarks.saidNone(war, ann, lastShown.plusDays(1)));
		assertFalse(AvailabilityMarks.saidNone(war, UUID.randomUUID(), FRIDAY));

		assertNull(AvailabilityMarks.setDate(war, ann, attacker, FRIDAY.plusDays(2), Set.of(), NOW));
		assertTrue(AvailabilityMarks.saidNone(war, ann, FRIDAY), "Clearing a date is not marking anything");
		assertNull(AvailabilityMarks.setDate(war, ann, attacker, FRIDAY.plusDays(2), Set.of(23), NOW));
		assertNull(war.getAvailability().noneUntil(ann));

		assertNull(AvailabilityMarks.setNone(war, ann, attacker, NOW));
		AvailabilityMarks.clearNone(war, ann);
		assertNull(war.getAvailability().noneUntil(ann));
		war.setBattleSchedulePhase(BattleSchedulePhase.IDLE);
		assertEquals(AvailabilityMarks.NO_SCHEDULE, AvailabilityMarks.setNone(war, ann, attacker, NOW));
	}
}
