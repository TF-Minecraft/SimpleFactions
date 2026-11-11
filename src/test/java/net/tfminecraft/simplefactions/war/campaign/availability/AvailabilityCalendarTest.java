package net.tfminecraft.simplefactions.war.campaign.availability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityCalendar.DateState;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;

class AvailabilityCalendarTest {
	private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 20);

	private int[] saved;
	private War war;

	@BeforeEach
	void setUp() {
		saved = new int[] {Cache.warBattleWindowStartHour, Cache.warBattleWindowEndHour, Cache.warAvailabilityDayLockHours,
				Cache.warAvailabilityTimeLockHours, Cache.warAvailabilityDaysShown};
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 26;
		Cache.warAvailabilityDayLockHours = 48;
		Cache.warAvailabilityTimeLockHours = 24;
		Cache.warAvailabilityDaysShown = 7;
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		war = new War(3, attacker, defender);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
	}

	@AfterEach
	void tearDown() {
		Cache.warBattleWindowStartHour = saved[0];
		Cache.warBattleWindowEndHour = saved[1];
		Cache.warAvailabilityDayLockHours = saved[2];
		Cache.warAvailabilityTimeLockHours = saved[3];
		Cache.warAvailabilityDaysShown = saved[4];
	}

	@Test
	void aDateLocksTwoDaysAndOneDayBeforeItsWindowOpens() {
		assertEquals("Fri 20/11", AvailabilityCalendar.label(FRIDAY));
		assertEquals(Instant.parse("2026-11-18T20:00:00Z"), AvailabilityCalendar.dayLockAt(FRIDAY));
		assertEquals(Instant.parse("2026-11-19T20:00:00Z"), AvailabilityCalendar.timeLockAt(FRIDAY));
		// A 21:00-to-02:00 window closes at 02:00 the next morning.
		assertEquals(Instant.parse("2026-11-21T01:00:00Z"), AvailabilityCalendar.windowClosesAt(FRIDAY));
	}

	@Test
	void locksStayAt21CetAcrossTheDaylightSavingChange() {
		// Summer time ends on Sunday 25 October 2026: Monday's locks fall on either side of it.
		LocalDate monday = LocalDate.of(2026, 10, 26);
		assertEquals(Instant.parse("2026-10-24T19:00:00Z"), AvailabilityCalendar.dayLockAt(monday), "21:00 CEST");
		assertEquals(Instant.parse("2026-10-25T20:00:00Z"), AvailabilityCalendar.timeLockAt(monday), "21:00 CET");
		LocalDate sunday = LocalDate.of(2026, 10, 25);
		assertEquals(Instant.parse("2026-10-23T19:00:00Z"), AvailabilityCalendar.dayLockAt(sunday));
		assertEquals(Instant.parse("2026-10-24T19:00:00Z"), AvailabilityCalendar.timeLockAt(sunday));
	}

	@Test
	void theGridStartsAtTheFirstDateWhoseTimeIsStillOpen() {
		Instant wednesdayNoon = Instant.parse("2026-11-18T11:00:00Z");
		assertEquals(LocalDate.of(2026, 11, 19), AvailabilityCalendar.firstVisible(war, wednesdayNoon),
				"Today's time locked yesterday; tomorrow's locks tonight");
		war.getAvailability().setEarliestBattleDay(LocalDate.of(2026, 11, 1));
		assertEquals(LocalDate.of(2026, 11, 19), AvailabilityCalendar.firstVisible(war, wednesdayNoon), "Never a past day");
		war.getAvailability().setEarliestBattleDay(FRIDAY.plusDays(5));
		List<LocalDate> dates = AvailabilityCalendar.visibleDates(war, wednesdayNoon);
		assertEquals(7, dates.size());
		assertEquals(FRIDAY.plusDays(5), dates.get(0), "A buildup starts the grid a week ahead");
		assertEquals(FRIDAY.plusDays(11), dates.get(6));
		Cache.warAvailabilityDaysShown = 3;
		assertEquals(3, AvailabilityCalendar.visibleDates(war, wednesdayNoon).size());
	}

	@Test
	void datesAreOpenThenBattleOrNoBattle() {
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRIDAY, false));
		war.getAvailability().setLastDayLockProcessed(FRIDAY.plusDays(1));
		assertEquals(DateState.BATTLE, AvailabilityCalendar.state(war, FRIDAY));
		assertEquals(DateState.NO_BATTLE, AvailabilityCalendar.state(war, FRIDAY.plusDays(1)));
		assertEquals(DateState.OPEN, AvailabilityCalendar.state(war, FRIDAY.plusDays(2)));
		assertEquals("no_battle", DateState.NO_BATTLE.toJson());

		Instant beforeTimeLock = Instant.parse("2026-11-19T19:59:00Z");
		assertTrue(AvailabilityCalendar.editable(war, FRIDAY, beforeTimeLock));
		assertFalse(AvailabilityCalendar.editable(war, FRIDAY, Instant.parse("2026-11-19T20:00:00Z")));
		assertFalse(AvailabilityCalendar.editable(war, FRIDAY.plusDays(1), beforeTimeLock));
		assertTrue(AvailabilityCalendar.editable(war, FRIDAY.plusDays(2), beforeTimeLock));
		assertFalse(AvailabilityCalendar.editable(war, FRIDAY.plusDays(2), Instant.parse("2026-11-20T20:00:00Z")),
				"Its day lock passed: it is being decided");
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRIDAY, false).withHour(22, false));
		assertFalse(AvailabilityCalendar.editable(war, FRIDAY, beforeTimeLock), "Staff already set its time");
	}

	@Test
	void theNextLockIsTheEarliestOneStillAhead() {
		Instant thursdayMorning = Instant.parse("2026-11-19T08:00:00Z");
		war.getAvailability().setEarliestBattleDay(FRIDAY);
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRIDAY, false));
		war.getAvailability().setLastDayLockProcessed(FRIDAY.plusDays(1));
		assertEquals(AvailabilityCalendar.timeLockAt(FRIDAY), AvailabilityCalendar.nextLockAt(war, thursdayMorning));
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRIDAY, false).withHour(21, false));
		assertEquals(AvailabilityCalendar.dayLockAt(FRIDAY.plusDays(2)), AvailabilityCalendar.nextLockAt(war, thursdayMorning));
		war.getAvailability().setLastDayLockProcessed(FRIDAY.plusDays(40));
		assertNull(AvailabilityCalendar.nextLockAt(war, thursdayMorning));
	}
}
