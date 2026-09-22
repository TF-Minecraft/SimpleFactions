package me.Plugins.SimpleFactions.War.campaign.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;

class BattleWindowServiceTest {
	@BeforeEach
	void setUp() {
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 24;
	}

	@Test
	void listValidHours_defaultWindow() {
		assertEquals(List.of(21, 22, 23, 24), BattleWindowService.listValidHours());
	}

	@Test
	void isValidHour_acceptsWindowBounds() {
		assertTrue(BattleWindowService.isValidHour(21));
		assertTrue(BattleWindowService.isValidHour(24));
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
	void computeScheduledBattleAt_hour24_usesNextDayMidnightInScheduleZone() {
		LocalDate battleDay = LocalDate.of(2026, 8, 21);
		Instant instant = BattleWindowService.computeScheduledBattleAt(battleDay, 24);
		assertEquals(
				battleDay.plusDays(1).atStartOfDay(BattleWindowService.SCHEDULE_ZONE).toInstant(),
				instant);
	}

	@Test
	void resolveScheduleHour_mapsMidnightNextDayToHour24() {
		LocalDate battleDay = LocalDate.of(2026, 8, 21);
		Instant instant = BattleWindowService.computeScheduledBattleAt(battleDay, 24);
		assertEquals(24, BattleWindowService.resolveScheduleHour(battleDay, instant));
	}

	@Test
	void computeScheduledBattleAt_rejectsInvalidHour() {
		assertNull(BattleWindowService.computeScheduledBattleAt(LocalDate.of(2026, 8, 21), 20));
		LocalDate missingDay = null;
		assertNull(BattleWindowService.computeScheduledBattleAt(missingDay, 21));
	}
}
