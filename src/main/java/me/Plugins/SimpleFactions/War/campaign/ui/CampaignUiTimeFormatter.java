package me.Plugins.SimpleFactions.War.campaign.ui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;

public final class CampaignUiTimeFormatter {
	private static final ZoneId CET_ZONE = ZoneId.of("Europe/Paris");
	private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private CampaignUiTimeFormatter() {}

	public static String formatUtcHour(LocalDate battleDay, int scheduleHour) {
		Instant instant = BattleWindowService.atScheduleHour(battleDay, scheduleHour);
		if (instant == null) {
			LocalDate day = battleDay != null ? battleDay : BattleWindowService.scheduleDate(CampaignClock.now());
			instant = BattleWindowService.atScheduleHour(day, Math.min(scheduleHour, 23));
		}
		return formatInstant(instant);
	}

	public static String formatInstant(Instant instant) {
		if (instant == null) {
			return "-";
		}
		return formatZonedUtc(instant.atZone(ZoneOffset.UTC));
	}

	private static String formatZonedUtc(ZonedDateTime utc) {
		ZonedDateTime cet = utc.withZoneSameInstant(CET_ZONE);
		ZonedDateTime est = utc.withZoneSameInstant(EST_ZONE);
		String cetPart = cet.format(TIME_FORMAT) + " CET";
		String estPart = est.format(TIME_FORMAT) + " EST";
		if (!cet.toLocalDate().equals(est.toLocalDate())) {
			estPart += CampaignUiCopy.MUTED + " (" + CampaignUiCopy.formatBattleDay(est.toLocalDate()) + ")";
		}
		return cetPart + " / " + estPart;
	}
}
