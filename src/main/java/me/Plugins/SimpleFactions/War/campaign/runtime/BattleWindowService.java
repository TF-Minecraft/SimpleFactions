package me.Plugins.SimpleFactions.War.campaign.runtime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.core.War;

public final class BattleWindowService {
	/** Config battle-schedule hours and battle days use this zone (CET/CEST). */
	public static final ZoneId SCHEDULE_ZONE = ZoneId.of("Europe/Paris");

	private BattleWindowService() {}

	public static List<Integer> listValidHours() {
		int start = Cache.warBattleWindowStartHour;
		int end = Cache.warBattleWindowEndHour;
		List<Integer> hours = new ArrayList<>();
		for (int hour = start; hour <= end; hour++) {
			hours.add(hour);
		}
		return Collections.unmodifiableList(hours);
	}

	public static boolean isValidHour(int hour) {
		return hour >= Cache.warBattleWindowStartHour && hour <= Cache.warBattleWindowEndHour;
	}

	public static Instant atScheduleHour(LocalDate battleDay, int hour) {
		if (battleDay == null || hour < 0 || hour > 24) {
			return null;
		}
		if (hour == 24) {
			return battleDay.plusDays(1).atStartOfDay(SCHEDULE_ZONE).toInstant();
		}
		return battleDay.atTime(hour, 0).atZone(SCHEDULE_ZONE).toInstant();
	}

	public static int scheduleHour(Instant now) {
		if (now == null) {
			return 0;
		}
		return now.atZone(SCHEDULE_ZONE).getHour();
	}

	public static LocalDate scheduleDate(Instant now) {
		if (now == null) {
			return null;
		}
		return now.atZone(SCHEDULE_ZONE).toLocalDate();
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

	public static Integer resolveScheduleHour(LocalDate battleDay, Instant scheduledAt) {
		if (battleDay == null || scheduledAt == null) {
			return null;
		}
		ZonedDateTime zoned = scheduledAt.atZone(SCHEDULE_ZONE);
		if (zoned.toLocalDate().equals(battleDay.plusDays(1))
				&& zoned.getHour() == 0
				&& zoned.getMinute() == 0) {
			return 24;
		}
		if (zoned.toLocalDate().equals(battleDay)) {
			return zoned.getHour();
		}
		return null;
	}
}
