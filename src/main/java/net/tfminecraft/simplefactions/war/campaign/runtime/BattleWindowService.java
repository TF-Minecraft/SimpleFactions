package net.tfminecraft.simplefactions.war.campaign.runtime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.core.War;

/**
 * Battle-day clock. Hours are counted from midnight at the start of the battle day, so they can
 * run past 23: hour 24 is 00:00 and hour 25 is 01:00 on the next calendar day. When the battle
 * window ends after midnight, the battle day keeps running until that end hour so a battle that
 * starts at 01:00 still belongs to the day before.
 */
public final class BattleWindowService {
	/** Config battle-schedule hours and battle days use this zone (CET/CEST). */
	public static final ZoneId SCHEDULE_ZONE = ZoneId.of("Europe/Paris");

	/** Highest {@code window_end_hour} the config accepts (00:00 two days later). */
	public static final int MAX_SCHEDULE_HOUR = 48;

	private BattleWindowService() {}

	/**
	 * Hours a battle may start. {@code window_end_hour} closes the window and is not a start time
	 * (21 to 25 when the window ends at 26). Whether the window is currently open still uses the
	 * end hour as its end.
	 */
	public static List<Integer> listValidHours() {
		int start = Cache.warBattleWindowStartHour;
		int end = Cache.warBattleWindowEndHour;
		List<Integer> hours = new ArrayList<>();
		for (int hour = start; hour < end; hour++) {
			hours.add(hour);
		}
		return Collections.unmodifiableList(hours);
	}

	/** Start hours as staff type them: 21, 22, 23, 0, 1 rather than 24 and 25. */
	public static List<Integer> listValidInputHours() {
		List<Integer> hours = new ArrayList<>();
		for (int hour : listValidHours()) {
			int clock = Math.floorMod(hour, 24);
			if (!hours.contains(clock)) {
				hours.add(clock);
			}
		}
		return Collections.unmodifiableList(hours);
	}

	/** Clock label for a battle-day hour, e.g. 25 becomes "01:00 CET". */
	public static String formatHourLabel(int scheduleHour) {
		return String.format(java.util.Locale.ROOT, "%02d:00 CET", Math.floorMod(scheduleHour, 24));
	}

	/** Valid start hours as clock labels, e.g. "21:00, 22:00, 23:00, 00:00, 01:00 CET". */
	public static String describeValidHours() {
		List<String> labels = new ArrayList<>();
		for (int hour : listValidHours()) {
			labels.add(String.format(java.util.Locale.ROOT, "%02d:00", Math.floorMod(hour, 24)));
		}
		return String.join(", ", labels) + " CET";
	}

	public static boolean isValidHour(int hour) {
		return hour >= Cache.warBattleWindowStartHour && hour < Cache.warBattleWindowEndHour;
	}

	/**
	 * Accepts a typed hour such as {@code 1} for 01:00 and returns the battle-day hour it means
	 * ({@code 25}), or the hour itself when it is already a valid start hour. Returns null when no
	 * valid start hour matches.
	 */
	public static Integer normalizeInputHour(int hour) {
		if (isValidHour(hour)) {
			return hour;
		}
		if (hour >= 0 && hour < 24 && isValidHour(hour + 24)) {
			return hour + 24;
		}
		return null;
	}

	/**
	 * Clock hour at which the next battle day takes over. 0 (midnight) unless the battle window
	 * ends after midnight, in which case the battle day runs until the window closes.
	 */
	public static int rolloverHour() {
		return Math.max(0, Math.min(23, Cache.warBattleWindowEndHour - 24));
	}

	/** When {@code battleDay} begins on the clock: midnight, or the rollover hour. */
	public static Instant campaignDayStart(LocalDate battleDay) {
		if (battleDay == null) {
			return null;
		}
		return battleDay.atTime(rolloverHour(), 0).atZone(SCHEDULE_ZONE).toInstant();
	}

	public static Instant atScheduleHour(LocalDate battleDay, int hour) {
		if (battleDay == null || hour < 0 || hour > MAX_SCHEDULE_HOUR) {
			return null;
		}
		return battleDay.plusDays(hour / 24).atTime(hour % 24, 0).atZone(SCHEDULE_ZONE).toInstant();
	}

	/** Hour of the current battle day (24 and up after midnight until the rollover hour). */
	public static int scheduleHour(Instant now) {
		if (now == null) {
			return 0;
		}
		int hour = now.atZone(SCHEDULE_ZONE).getHour();
		return hour < rolloverHour() ? hour + 24 : hour;
	}

	/** Battle day the clock is in (the previous date until the rollover hour). */
	public static LocalDate scheduleDate(Instant now) {
		if (now == null) {
			return null;
		}
		ZonedDateTime zoned = now.atZone(SCHEDULE_ZONE);
		LocalDate date = zoned.toLocalDate();
		return zoned.getHour() < rolloverHour() ? date.minusDays(1) : date;
	}

	public static Instant computeScheduledBattleAt(LocalDate battleDay, int hour) {
		if (!isValidHour(hour)) {
			return null;
		}
		return atScheduleHour(battleDay, hour);
	}

	public static Instant computeScheduledBattleAt(War war, int hour) {
		if (war == null) {
			return null;
		}
		return computeScheduledBattleAt(war.getBattleDay(), hour);
	}

	/** Battle-day hour of a scheduled start (24 and up on the next morning), or null when it is not on that day. */
	public static Integer resolveScheduleHour(LocalDate battleDay, Instant scheduledAt) {
		if (battleDay == null || scheduledAt == null) {
			return null;
		}
		ZonedDateTime zoned = scheduledAt.atZone(SCHEDULE_ZONE);
		long dayOffset = ChronoUnit.DAYS.between(battleDay, zoned.toLocalDate());
		if (dayOffset < 0 || dayOffset > 1) {
			return null;
		}
		if (dayOffset == 0) {
			return zoned.getHour();
		}
		int nextMorningHour = 24 + zoned.getHour();
		if (nextMorningHour < Cache.warBattleWindowEndHour) {
			return nextMorningHour;
		}
		return zoned.getHour() == 0 && zoned.getMinute() == 0 ? 24 : null;
	}
}
