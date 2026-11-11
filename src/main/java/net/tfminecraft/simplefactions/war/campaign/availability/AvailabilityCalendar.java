package net.tfminecraft.simplefactions.war.campaign.availability;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.core.War;

/**
 * When each date locks and which dates players can mark. Dates are battle days
 * ({@link BattleWindowService#scheduleDate}) and lock times are counted on the schedule's wall
 * clock, so "48 hours before" is 21:00 CET two days before even across a DST change.
 */
public final class AvailabilityCalendar {
	private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.ENGLISH);

	/** What a date is to players. */
	public enum DateState {
		/** Not decided yet: any mark can change. */
		OPEN("open"),
		/** A battle day; its start time is open until the time lock. */
		BATTLE("battle"),
		/** Decided: no battle that day. */
		NO_BATTLE("no_battle");

		private final String json;

		DateState(String json) {
			this.json = json;
		}

		public String toJson() {
			return json;
		}
	}

	private AvailabilityCalendar() {
	}

	/** {@code Fri 20/11}. */
	public static String label(LocalDate date) {
		return LABEL.format(date);
	}

	public static Instant dayLockAt(LocalDate date) {
		return beforeWindow(date, Cache.warAvailabilityDayLockHours);
	}

	public static Instant timeLockAt(LocalDate date) {
		return beforeWindow(date, Cache.warAvailabilityTimeLockHours);
	}

	/** When the date's battle window closes: no battle can start on it after this. */
	public static Instant windowClosesAt(LocalDate date) {
		return BattleWindowService.atScheduleHour(date, Cache.warBattleWindowEndHour);
	}

	private static Instant beforeWindow(LocalDate date, int hours) {
		return date.atStartOfDay().plusHours(Cache.warBattleWindowStartHour).minusHours(hours)
				.atZone(BattleWindowService.SCHEDULE_ZONE).toInstant();
	}

	/**
	 * The first date players can still mark: on or after the war's earliest battle day, never a
	 * past date, and its time lock still ahead.
	 */
	public static LocalDate firstVisible(War war, Instant now) {
		LocalDate today = BattleWindowService.scheduleDate(now);
		LocalDate earliest = war.getAvailability().getEarliestBattleDay();
		LocalDate date = earliest == null || earliest.isBefore(today) ? today : earliest;
		while (!timeLockAt(date).isAfter(now)) {
			date = date.plusDays(1);
		}
		return date;
	}

	/** {@code days_shown} consecutive dates from {@link #firstVisible}. */
	public static List<LocalDate> visibleDates(War war, Instant now) {
		LocalDate first = firstVisible(war, now);
		List<LocalDate> dates = new ArrayList<>();
		for (int i = 0; i < Cache.warAvailabilityDaysShown; i++) {
			dates.add(first.plusDays(i));
		}
		return dates;
	}

	public static DateState state(War war, LocalDate date) {
		BattleAvailability availability = war.getAvailability();
		if (availability.battleDay(date) != null) {
			return DateState.BATTLE;
		}
		LocalDate decided = availability.getLastDayLockProcessed();
		return decided != null && !date.isAfter(decided) ? DateState.NO_BATTLE : DateState.OPEN;
	}

	/** Whether marks on the date can still change: an open date before its day lock, a battle day before its time lock. */
	public static boolean editable(War war, LocalDate date, Instant now) {
		return switch (state(war, date)) {
			case OPEN -> now.isBefore(dayLockAt(date));
			case BATTLE -> war.getAvailability().battleDay(date).hour() == null && now.isBefore(timeLockAt(date));
			case NO_BATTLE -> false;
		};
	}

	/** The lock that comes next among the visible dates; null when none is ahead. */
	public static Instant nextLockAt(War war, Instant now) {
		Instant next = null;
		for (LocalDate date : visibleDates(war, now)) {
			Instant lock = switch (state(war, date)) {
				case OPEN -> dayLockAt(date);
				case BATTLE -> war.getAvailability().battleDay(date).hour() == null ? timeLockAt(date) : null;
				case NO_BATTLE -> null;
			};
			if (lock != null && lock.isAfter(now) && (next == null || lock.isBefore(next))) {
				next = lock;
			}
		}
		return next;
	}
}
