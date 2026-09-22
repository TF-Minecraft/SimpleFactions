package me.Plugins.SimpleFactions.War.campaign.admin;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleTickService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignDurationParser;
import me.Plugins.TLibs.Utils.TimeFormatter;

public final class CampaignTimeCommandService {
	private static final DateTimeFormatter SCHEDULE_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final String INVALID_DURATION =
			"§cInvalid duration. Example: §e/war admin time add 1h 31m";

	private CampaignTimeCommandService() {}

	public static List<String> statusLines() {
		Instant scheduleNow = CampaignClock.now();
		List<String> lines = new ArrayList<>();
		lines.add("§7Offset: §e" + formatOffset(CampaignClock.getOffset()));
		lines.add("§7Schedule time (Paris): §e"
				+ scheduleNow.atZone(BattleWindowService.SCHEDULE_ZONE).format(SCHEDULE_FORMAT));
		lines.add("§7Paris battle-day hour: §e" + BattleScheduleService.battleDayHour(scheduleNow));
		lines.add("§7Real time (UTC): §e"
				+ Instant.now().atZone(ZoneOffset.UTC).format(SCHEDULE_FORMAT));
		lines.add("§7Spoofed: §e" + (CampaignClock.isSpoofed() ? "yes" : "no"));
		lines.add("§7Active wars: §e" + WarManager.getActive().size());
		return lines;
	}

	public static CampaignTimeResult add(String... durationTokens) {
		Duration duration;
		try {
			duration = CampaignDurationParser.parse(durationTokens);
		} catch (IllegalArgumentException ex) {
			return CampaignTimeResult.error(INVALID_DURATION);
		}
		CampaignClock.add(duration);
		int ticked = applyClockChange();
		String paris = CampaignClock.now().atZone(BattleWindowService.SCHEDULE_ZONE).format(SCHEDULE_FORMAT);
		String message = "§aCampaign time advanced by §e" + formatOffset(duration)
				+ "§a. Paris: §e" + paris + "§a.";
		if (ticked > 0) {
			message += " §7(" + ticked + " war(s) updated)";
		}
		return CampaignTimeResult.ok(message);
	}

	public static CampaignTimeResult reset() {
		CampaignClock.reset();
		applyClockChange();
		return CampaignTimeResult.ok("§aCampaign time reset to real time.");
	}

	/**
	 * Jumps campaign schedule to 00:00 Paris on {@code war.getBattleDay()}.
	 */
	public static CampaignTimeResult skipToBattleDay(War war) {
		if (war == null) {
			return CampaignTimeResult.error("§cUnknown war id.");
		}
		if (war.getBattleDay() == null) {
			return CampaignTimeResult.error("§cWar has no battle day set.");
		}
		Instant target = war.getBattleDay().atStartOfDay(BattleWindowService.SCHEDULE_ZONE).toInstant();
		Duration delta = Duration.between(Instant.now(), target);
		CampaignClock.reset();
		CampaignClock.add(delta);
		int ticked = applyClockChange();

		if (!BattleScheduleService.battleDayDate(CampaignClock.now()).equals(war.getBattleDay())) {
			return CampaignTimeResult.error("§cCould not align campaign clock to war battle day.");
		}

		String paris = CampaignClock.now().atZone(BattleWindowService.SCHEDULE_ZONE).format(SCHEDULE_FORMAT);
		String message = "§aCampaign time set to battle day §e" + war.getBattleDay()
				+ "§a (war §e" + war.getId() + "§a). Paris: §e" + paris + "§a.";
		if (ticked > 0) {
			message += " §7(" + ticked + " war(s) updated)";
		}
		return CampaignTimeResult.ok(message);
	}

	private static int applyClockChange() {
		BattleScheduleTickService.onClockOffsetChanged();
		return BattleScheduleTickService.tick(CampaignClock.now());
	}

	private static String formatOffset(Duration duration) {
		if (duration == null || duration.isZero()) {
			return "real time";
		}
		long seconds = duration.getSeconds();
		String formatted = TimeFormatter.formatTime((int) Math.abs(seconds));
		return seconds < 0 ? "-" + formatted : "+" + formatted;
	}
}
