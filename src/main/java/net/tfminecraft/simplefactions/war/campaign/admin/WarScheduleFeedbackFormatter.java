package net.tfminecraft.simplefactions.war.campaign.admin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;

public final class WarScheduleFeedbackFormatter {
	private WarScheduleFeedbackFormatter() {
	}

	public static List<String> format(String subcommand, War war) {
		if (war == null || subcommand == null) {
			return List.of();
		}
		List<String> lines = new ArrayList<>();
		switch (subcommand.toLowerCase(Locale.ROOT)) {
			case "skipday", "lockday", "unlockday", "sethour" -> lines.add(formatBattleDays(war));
			case "setscheduled" -> lines.add(formatSetScheduled(war));
			case "battlecreate" -> lines.add(formatBattleCreate(war));
			case "battledelete" -> lines.add(formatBattleDelete(war));
			case "battlestart" -> lines.add(formatBattleStart(war));
			case "winbattle" -> lines.add(formatWinBattle(war));
			case "battlechoice", "defenderchoice", "pushchoice", "holdchoice" -> lines.add(formatBattleChoice(war));
			default -> {
				return List.of();
			}
		}
		appendCampaignBattleLine(war, lines);
		appendScheduleLines(war, lines);
		return lines;
	}

	private static String formatBattleDays(War war) {
		List<String> days = new ArrayList<>();
		for (LockedBattleDay day : war.getAvailability().battleDays()) {
			days.add(day.date() + (day.hour() == null ? "" : " " + BattleWindowService.formatHourLabel(day.hour())));
		}
		return "§7Battle days: §e" + (days.isEmpty() ? "-" : String.join(", ", days))
				+ " §7· Phase: §e" + formatPhase(war);
	}

	private static String formatSetScheduled(War war) {
		return "§7Scheduled: §e" + formatInstant(war.getScheduledBattleAt())
				+ " §7· Province: §e" + formatProvinceId(war.getScheduledBattleProvinceId())
				+ " §7· Phase: §e" + formatPhase(war);
	}

	private static String formatBattleCreate(War war) {
		Battle battle = BattleManager.getByWarId(war.getId());
		return "§7Province: §e" + formatProvinceId(war.getScheduledBattleProvinceId())
				+ " §7· Phase: §e" + formatPhase(war)
				+ (battle != null ? " §7· Battle: §e" + battle.getId() : "");
	}

	private static String formatBattleDelete(War war) {
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle == null) {
			return "§7No campaign battle";
		}
		return "§7Battle: §e" + battle.getId()
				+ " §7· Started: §e" + battle.hasStarted();
	}

	private static String formatBattleStart(War war) {
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle == null) {
			return "§7No campaign battle";
		}
		return "§7Battle: §e" + battle.getDisplayName()
				+ " §7· Started: §e" + battle.hasStarted();
	}

	private static String formatWinBattle(War war) {
		String line = "§7Initiative: §e"
				+ (war.getInitiativeHolder() != null ? war.getInitiativeHolder().name().toLowerCase(Locale.ROOT) : "attacker")
				+ " §7· Campaign phase: §e"
				+ (war.getCampaignPhase() != null ? war.getCampaignPhase().toJson() : "invasion");
		if (CampaignPostBattleChoiceService.needsAnyChoice(war)) {
			line += " §7· §ePost-battle choice pending";
		}
		return line;
	}

	private static String formatBattleChoice(War war) {
		return "§7Initiative: §e"
				+ CampaignSideService.getInitiativeHolderCoalition(war).toJson()
				+ " §7· Push target: §e"
				+ (war.getPushTarget() != null ? war.getPushTarget().toJson() : "toward_objective");
	}

	private static void appendCampaignBattleLine(War war, List<String> lines) {
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle != null) {
			lines.add("§7Campaign battle: §e" + battle.getId());
		}
	}

	private static void appendScheduleLines(War war, List<String> lines) {
		appendLegScheduleLines(war, lines, CampaignScheduleService.ScheduleLeg.INVASION, "Invasion schedule");
		appendLegScheduleLines(war, lines, CampaignScheduleService.ScheduleLeg.COUNTER, "Counter schedule");
	}

	private static void appendLegScheduleLines(
			War war,
			List<String> lines,
			CampaignScheduleService.ScheduleLeg leg,
			String label) {
		if (!CampaignScheduleService.hasScheduleForLeg(war, leg)) {
			return;
		}
		lines.add("§7" + label + ":");
		List<ScheduledCampaignBattle> schedule = CampaignScheduleService.scheduleListForLeg(war, leg);
		int currentIndex = CampaignScheduleService.scheduleIndexForLeg(war, leg);
		boolean activeLeg = leg == CampaignScheduleService.activeLeg(war);
		for (int index = 0; index < schedule.size(); index++) {
			ScheduledCampaignBattle slot = schedule.get(index);
			String prefix = activeLeg && index == currentIndex ? "§a> " : "§7";
			String fortId = slot.fortInstallationId() != null ? slot.fortInstallationId() : "-";
			String portId = slot.portInstallationId() != null ? slot.portInstallationId() : "-";
			lines.add(prefix
					+ "§7[§e" + index + "§7] province §e" + slot.provinceId()
					+ " §7· kind §e" + slot.kind().toJson()
					+ " §7· required §e" + slot.required()
					+ " §7· fort §e" + fortId
					+ " §7· port §e" + portId
					+ (activeLeg && index == currentIndex ? " §7(current)" : ""));
		}
	}

	private static String formatPhase(War war) {
		return war.getBattleSchedulePhase().toJson();
	}

	private static String formatInstant(Instant instant) {
		if (instant == null) {
			return "-";
		}
		return instant.toString();
	}

	private static String formatProvinceId(Integer provinceId) {
		if (provinceId == null) {
			return "-";
		}
		return String.valueOf(provinceId);
	}
}
