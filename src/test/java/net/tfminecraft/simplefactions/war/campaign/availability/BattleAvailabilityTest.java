package net.tfminecraft.simplefactions.war.campaign.availability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

class BattleAvailabilityTest {
	private static final LocalDate DAY = LocalDate.of(2026, 11, 20);
	private final UUID ann = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private final UUID dee = UUID.fromString("00000000-0000-0000-0000-00000000000d");

	@Test
	void marksAreKeptPerAccountAndDateAndClearedWhenEmpty() {
		BattleAvailability availability = new BattleAvailability();
		assertTrue(availability.marksOn(ann, DAY).isEmpty());
		availability.setMarks(ann, DAY, Set.of(22, 21));
		availability.setMarks(ann, DAY.plusDays(1), Set.of(23));
		assertEquals(Set.of(21, 22), availability.marksOn(ann, DAY));
		assertTrue(availability.hasMarksOn(ann, DAY));
		assertFalse(availability.hasMarksOn(dee, DAY));
		assertThrows(UnsupportedOperationException.class, () -> availability.marksOn(ann, DAY).clear());
		assertThrows(UnsupportedOperationException.class, () -> availability.marks().clear());

		availability.setMarks(ann, DAY, Set.of());
		assertFalse(availability.hasMarksOn(ann, DAY));
		availability.setMarks(ann, DAY.plusDays(1), null);
		assertTrue(availability.marks().isEmpty(), "No empty account is left behind");
		availability.setMarks(dee, DAY, Set.of());
		assertTrue(availability.marks().isEmpty());
	}

	@Test
	void noneAnswersRememberTheLastDateTheyCovered() {
		BattleAvailability availability = new BattleAvailability();
		availability.setNone(ann, DAY);
		assertEquals(DAY, availability.noneUntil(ann));
		assertEquals(Map.of(ann, DAY), availability.none());
		availability.setNone(ann, null);
		assertNull(availability.noneUntil(ann));
	}

	@Test
	void battleDaysStayInDateOrderOneEntryPerDate() {
		BattleAvailability availability = new BattleAvailability();
		assertNull(availability.head());
		availability.putBattleDay(LockedBattleDay.open(DAY.plusDays(2), false));
		availability.putBattleDay(LockedBattleDay.open(DAY, true));
		availability.putBattleDay(LockedBattleDay.open(DAY, true).withHour(22, true));

		assertEquals(List.of(DAY, DAY.plusDays(2)), availability.battleDays().stream().map(LockedBattleDay::date).toList());
		assertEquals(new LockedBattleDay(DAY, 22, true, true), availability.head());
		assertEquals(22, availability.battleDay(DAY).hour());
		assertNull(availability.battleDay(DAY.plusDays(1)));
		assertTrue(availability.removeBattleDay(DAY));
		assertFalse(availability.removeBattleDay(DAY));
		availability.clearBattleDays();
		assertTrue(availability.battleDays().isEmpty());
	}

	@Test
	void theStreakNeverGoesNegativeAndRemindersAreRecordedOnce() {
		BattleAvailability availability = new BattleAvailability();
		availability.setNoBattleStreak(-4);
		assertEquals(0, availability.getNoBattleStreak());
		assertTrue(availability.markReminderSent("time_lock_soon|" + DAY));
		assertFalse(availability.markReminderSent("time_lock_soon|" + DAY));
		assertEquals(Set.of("time_lock_soon|" + DAY), availability.remindersSent());
	}

	@Test
	void pruningForgetsOnlyWhatConcernedPastDates() {
		BattleAvailability availability = new BattleAvailability();
		availability.setMarks(ann, DAY.minusDays(1), Set.of(21));
		availability.setMarks(ann, DAY, Set.of(21));
		availability.setMarks(dee, DAY.minusDays(2), Set.of(21));
		availability.setNone(ann, DAY.minusDays(1));
		availability.setNone(dee, DAY);
		availability.markReminderSent("mark_availability|" + DAY.minusDays(1));
		availability.markReminderSent("mark_availability|" + DAY);

		assertTrue(availability.pruneBefore(DAY));

		assertEquals(Map.of(ann, Map.of(DAY, Set.of(21))), availability.marks());
		assertEquals(Map.of(dee, DAY), availability.none());
		assertEquals(Set.of("mark_availability|" + DAY), availability.remindersSent());
		assertFalse(availability.pruneBefore(DAY), "Nothing left to forget");
	}

	@Test
	void theSavedFormRoundTripsThroughJson() {
		BattleAvailability availability = new BattleAvailability();
		availability.setMarks(ann, DAY, Set.of(25, 21));
		availability.setNone(dee, DAY.plusDays(6));
		availability.putBattleDay(LockedBattleDay.open(DAY, true).withHour(25, true));
		availability.putBattleDay(LockedBattleDay.open(DAY.plusDays(1), false));
		availability.setNoBattleStreak(2);
		availability.setLastDayLockProcessed(DAY.plusDays(1));
		availability.setEarliestBattleDay(DAY.minusDays(3));
		availability.markReminderSent("battle_day_locked|" + DAY);

		Gson gson = new Gson();
		BattleAvailability.Data data = gson.fromJson(gson.toJson(availability.toData()), BattleAvailability.Data.class);
		BattleAvailability loaded = BattleAvailability.fromData(data);

		assertEquals(availability.marks(), loaded.marks());
		assertEquals(availability.none(), loaded.none());
		assertEquals(availability.battleDays(), loaded.battleDays());
		assertEquals(2, loaded.getNoBattleStreak());
		assertEquals(DAY.plusDays(1), loaded.getLastDayLockProcessed());
		assertEquals(DAY.minusDays(3), loaded.getEarliestBattleDay());
		assertEquals(availability.remindersSent(), loaded.remindersSent());
		assertEquals(List.of(21, 25), data.marks.get(ann.toString()).get(DAY.toString()));
	}

	@Test
	void unreadableSavedRowsAreSkippedWithoutLosingTheRest() {
		assertTrue(BattleAvailability.fromData(null).marks().isEmpty());
		BattleAvailability.Data empty = new BattleAvailability.Data();
		BattleAvailability blank = BattleAvailability.fromData(empty);
		assertTrue(blank.battleDays().isEmpty());
		assertNull(blank.getEarliestBattleDay());

		BattleAvailability.Data data = new BattleAvailability.Data();
		data.marks = new HashMap<>();
		Map<String, List<Integer>> dates = new HashMap<>();
		dates.put(DAY.toString(), List.of(21));
		dates.put("someday", List.of(21));
		dates.put(DAY.plusDays(1).toString(), null);
		data.marks.put(ann.toString(), dates);
		data.marks.put("not-an-account", Map.of(DAY.toString(), List.of(22)));
		data.marks.put(dee.toString(), null);
		data.marks.put(null, Map.of());
		data.none = new HashMap<>();
		data.none.put(ann.toString(), DAY.toString());
		data.none.put(dee.toString(), "never");
		data.none.put("bad", DAY.toString());
		BattleAvailability.DayData good = new BattleAvailability.DayData();
		good.date = DAY.toString();
		good.hour = 22;
		BattleAvailability.DayData bad = new BattleAvailability.DayData();
		bad.date = "friday";
		data.battleDays = new ArrayList<>(Arrays.asList(good, null, bad));
		data.lastDayLockProcessed = "yesterday";
		data.remindersSent = List.of("mark_availability|" + DAY);

		BattleAvailability loaded = BattleAvailability.fromData(data);

		assertEquals(Map.of(ann, Map.of(DAY, Set.of(21))), loaded.marks());
		assertEquals(Map.of(ann, DAY), loaded.none());
		assertEquals(List.of(new LockedBattleDay(DAY, 22, false, false)), loaded.battleDays());
		assertNull(loaded.getLastDayLockProcessed());
		assertEquals(Set.of("mark_availability|" + DAY), loaded.remindersSent());
	}
}
