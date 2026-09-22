package me.Plugins.SimpleFactions.War.campaign.ui;

import java.time.LocalDate;

import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService;

public final class CampaignUiCopy {
	public static final String LABEL = "#a39ba8";
	public static final String VALUE = "#d4c9ae";
	public static final String STATUS_HIGHLIGHT = "#e6c84a";
	public static final String OBJECTIVE = "#c9a0ff";
	public static final String MUTED = "#6b6b6b";
	public static final String NEXT_BATTLE = "#3dff3d";
	public static final String SELECT = "#7fbd73";
	public static final String REMOVE = "#e85d5d";
	public static final String WARNING = "#e6c84a";
	public static final String BATTLE_KIND = "#9ec8ff";
	public static final String FOUGHT_LABEL = "Fought";
	public static final String RETREATED_LABEL = "Retreated";
	public static final String REQUIRED_ZOC_PORT = "Required ZOC port";
	public static final String NAVY_BLOCKADE =
			"You need an operational port for a naval path. Source ships before the battle.";

	private CampaignUiCopy() {}

	public static String formatBattleKind(CampaignBattleKind kind) {
		if (kind == null || kind == CampaignBattleKind.FIELD) {
			return "Field Battle";
		}
		return switch (kind) {
			case SIEGE -> "Siege";
			case NAVAL -> "Naval Battle";
			case NAVAL_INVASION -> "Naval Invasion";
			default -> null;
		};
	}

	public static String titleCasePhase(CampaignPhase phase) {
		if (phase == null) {
			return "Invasion";
		}
		return switch (phase) {
			case INVASION -> "Invasion";
			case RETAKE -> "Retake";
			case COUNTER_PUSH -> "Counter Push";
		};
	}

	public static String formatInitiativeHolder(BelligerentRole holder) {
		if (holder == null) {
			return "Attacker";
		}
		return switch (holder) {
			case ATTACKER -> "Attacker";
			case DEFENDER -> "Defender";
		};
	}

	public static String formatBattleDay(LocalDate date) {
		if (date == null) {
			return "-";
		}
		return String.format("%02d/%02d/%04d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
	}

	public static String resolveActivityStatus(War war) {
		if (war == null) {
			return "Between Battles";
		}
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle != null && battle.hasStarted()) {
			return "In Battle";
		}
		if (CampaignPostBattleChoiceService.needsAnyChoice(war)) {
			return "Awaiting Decision";
		}
		BattleSchedulePhase schedulePhase = war.getBattleSchedulePhase();
		if (schedulePhase == BattleSchedulePhase.VOTING) {
			return "Currently Voting";
		}
		if (schedulePhase == BattleSchedulePhase.SCHEDULED) {
			return "Awaiting Battle";
		}
		if (schedulePhase == BattleSchedulePhase.AUTORESOLVE_PENDING) {
			return "Autoresolve Pending";
		}
		return "Between Battles";
	}

	public static String navyBlockadeDeclareMessage() {
		return "§c" + NAVY_BLOCKADE;
	}

	public static String navalAutoLossLeaderPing() {
		return "§cBerth a naval vehicle at a committed port before the naval battle or you will auto-lose.";
	}

	public static String navalAutoLossBroadcast() {
		return "§eAttacker had no berthed navy at a committed port. §7Defender wins the naval slot.";
	}
}
