package net.tfminecraft.simplefactions.war.campaign.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;

class BattleWindowServiceTest {
	@BeforeEach
	void setUp() {
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 24;
	}

	@Test
	void listValidHours_excludesWindowEnd() {
		assertEquals(List.of(21, 22, 23), BattleWindowService.listValidHours());
	}

	@Test
	void isValidHour_excludesWindowEnd() {
		assertTrue(BattleWindowService.isValidHour(21));
		assertTrue(BattleWindowService.isValidHour(23));
		assertFalse(BattleWindowService.isValidHour(24));
		assertFalse(BattleWindowService.isValidHour(20));
		assertFalse(BattleWindowService.isValidHour(25));
	}

	@Test
	void computeScheduledBattleAt_hour21_usesBattleDayInScheduleZone() {
		LocalDate battleDay = LocalDate.of(2026, 8, 21);
		Instant instant = BattleWindowService.computeScheduledBattleAt(battleDay, 21);
		assertEquals(battleDay.atTime(21, 0).atZone(BattleWindowService.SCHEDULE_ZONE).toInstant(), instant);
	}

	@Test
	void computeScheduledBattleAt_hour24_isNotAStartTime() {
		assertNull(BattleWindowService.computeScheduledBattleAt(LocalDate.of(2026, 8, 21), 24));
	}

	@Test
	void resolveScheduleHour_mapsMidnightNextDayToHour24() {
		LocalDate battleDay = LocalDate.of(2026, 8, 21);
		Instant instant = BattleWindowService.atScheduleHour(battleDay, 24);
		assertEquals(24, BattleWindowService.resolveScheduleHour(battleDay, instant));
	}

	@Test
	void computeScheduledBattleAt_rejectsInvalidHour() {
		assertNull(BattleWindowService.computeScheduledBattleAt(LocalDate.of(2026, 8, 21), 20));
		LocalDate missingDay = null;
		assertNull(BattleWindowService.computeScheduledBattleAt(missingDay, 21));
	}

	@Test
	void lateWindow_listsHoursPastMidnight() {
		Cache.warBattleWindowEndHour = 26;
		assertEquals(List.of(21, 22, 23, 24, 25), BattleWindowService.listValidHours());
		assertTrue(BattleWindowService.isValidHour(25));
		assertFalse(BattleWindowService.isValidHour(26));
	}

	@Test
	void lateWindow_hour25_isOneAmNextDateCet() {
		Cache.warBattleWindowEndHour = 26;
		LocalDate battleDay = LocalDate.of(2026, 11, 6);
		Instant instant = BattleWindowService.computeScheduledBattleAt(battleDay, 25);
		assertEquals(
				LocalDate.of(2026, 11, 7).atTime(1, 0).atZone(BattleWindowService.SCHEDULE_ZONE).toInstant(),
				instant);
		assertEquals(25, BattleWindowService.resolveScheduleHour(battleDay, instant));
	}

	@Test
	void normalizeInputHour_mapsEarlyMorningOnlyWhenWindowAllowsIt() {
		assertEquals(21, BattleWindowService.normalizeInputHour(21));
		assertNull(BattleWindowService.normalizeInputHour(0));
		Cache.warBattleWindowEndHour = 26;
		assertEquals(24, BattleWindowService.normalizeInputHour(0));
		assertEquals(25, BattleWindowService.normalizeInputHour(1));
		assertEquals(25, BattleWindowService.normalizeInputHour(25));
		assertNull(BattleWindowService.normalizeInputHour(2));
		assertNull(BattleWindowService.normalizeInputHour(20));
	}

	@Test
	void rollover_keepsEarlyMorningOnPreviousBattleDay() {
		assertEquals(0, BattleWindowService.rolloverHour());
		Cache.warBattleWindowEndHour = 26;
		assertEquals(2, BattleWindowService.rolloverHour());
		Instant oneThirtyAm = LocalDate.of(2026, 11, 7).atTime(1, 30)
				.atZone(BattleWindowService.SCHEDULE_ZONE).toInstant();
		assertEquals(LocalDate.of(2026, 11, 6), BattleWindowService.scheduleDate(oneThirtyAm));
		assertEquals(25, BattleWindowService.scheduleHour(oneThirtyAm));
		Instant twoAm = LocalDate.of(2026, 11, 7).atTime(2, 0)
				.atZone(BattleWindowService.SCHEDULE_ZONE).toInstant();
		assertEquals(LocalDate.of(2026, 11, 7), BattleWindowService.scheduleDate(twoAm));
		assertEquals(2, BattleWindowService.scheduleHour(twoAm));
		assertEquals(twoAm, BattleWindowService.campaignDayStart(LocalDate.of(2026, 11, 7)));
	}
}
