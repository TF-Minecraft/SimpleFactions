package me.Plugins.SimpleFactions.War.campaign.admin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.campaign.vote.BattleQuorumService;

public final class WarScheduleFeedbackFormatter {
	private WarScheduleFeedbackFormatter() {
	}

	public static List<String> format(String subcommand, War war) {
		return format(subcommand, war, null);
	}

	public static List<String> format(String subcommand, War war, Integer castVoteHour) {
		if (war == null || subcommand == null) {
			return List.of();
		}
		List<String> lines = new ArrayList<>();
		switch (subcommand.toLowerCase()) {
			case "opencvote" -> lines.add(formatOpenVote(war));
			case "closevote" -> lines.add(formatCloseVote(war));
			case "castvote" -> lines.add(formatCastVote(war, castVoteHour));
			case "skipday" -> lines.add(formatSkipDay(war));
			case "forcequorum" -> lines.add("§7Next close bypasses quorum");
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

	private static String formatOpenVote(War war) {
		return "§7Phase: §e" + formatPhase(war)
				+ " §7· Battle day: §e" + formatBattleDay(war);
	}

	private static String formatCloseVote(War war) {
		BattleSchedulePhase phase = war.getBattleSchedulePhase();
		if (phase == BattleSchedulePhase.SCHEDULED) {
			return "§7Scheduled: §e" + formatInstant(war.getScheduledBattleAt())
					+ " §7· Province: §e" + formatProvinceId(war.getScheduledBattleProvinceId())
					+ " §7· Phase: §e" + formatPhase(war);
		}
		if (phase == BattleSchedulePhase.AUTORESOLVE_PENDING) {
			return "§7Phase: §e" + formatPhase(war);
		}
		if (phase == BattleSchedulePhase.VOTING && war.getScheduledBattleAt() == null) {
			return "§7Postponed §7· Battle day: §e" + formatBattleDay(war)
					+ " §7· Phase: §e" + formatPhase(war);
		}
		return "§7Phase: §e" + formatPhase(war);
	}

	private static String formatCastVote(War war, Integer castVoteHour) {
		String hour = castVoteHour != null ? String.valueOf(castVoteHour) : "-";
		int selections = castVoteHour != null ? countVotesAtHour(war, castVoteHour) : 0;
		return "§7Hour §e" + hour
				+ " §7· Selections: §e" + selections
				+ " §7· Voters: §e" + BattleQuorumService.countDistinctVoters(war)
				+ " §7· Phase: §e" + formatPhase(war);
	}

	private static String formatSkipDay(War war) {
		return "§7Battle day: §e" + formatBattleDay(war);
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
				+ (war.getInitiativeHolder() != null ? war.getInitiativeHolder().name().toLowerCase() : "attacker")
				+ " §7· Campaign phase: §e"
				+ (war.getCampaignPhase() != null ? war.getCampaignPhase().toJson() : "invasion");
		if (CampaignPostBattleChoiceService.needsAnyChoice(war)) {
			line += " §7· §ePost-battle choice pending";
		}
		return line;
	}

	private static String formatBattleChoice(War war) {
		return "§7Initiative: §e"
				+ (war.getInitiativeHolderCoalition() != null
						? war.getInitiativeHolderCoalition().name().toLowerCase()
						: "aggressor")
				+ " §7· Push target: §e"
				+ (war.getPushTarget() != null ? war.getPushTarget().toJson() : "toward_objective");
	}

	private static String formatDefenderChoice(War war) {
		return formatBattleChoice(war);
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
		if (war.getBattleSchedulePhase() == null) {
			return "-";
		}
		return war.getBattleSchedulePhase().toJson();
	}

	private static String formatBattleDay(War war) {
		if (war.getBattleDay() == null) {
			return "-";
		}
		return war.getBattleDay().toString();
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

	static int countVotesAtHour(War war, int hour) {
		if (war == null || war.getBattleVotes() == null) {
			return 0;
		}
		int count = 0;
		for (Set<Integer> hours : war.getBattleVotes().values()) {
			if (hours != null && hours.contains(hour)) {
				count++;
			}
		}
		return count;
	}
}
