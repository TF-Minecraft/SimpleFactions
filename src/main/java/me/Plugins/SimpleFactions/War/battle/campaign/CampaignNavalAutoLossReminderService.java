package me.Plugins.SimpleFactions.War.battle.campaign;

import java.time.Instant;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.campaign.progression.AttackerNavalContestService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleService;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignUiCopy;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;

public final class CampaignNavalAutoLossReminderService {
	private CampaignNavalAutoLossReminderService() {}

	public static void processReminders(War war, Instant now) {
		if (war == null || !war.isActive() || now == null) {
			return;
		}
		BattleSchedulePhase phase = war.getBattleSchedulePhase();
		if (phase != BattleSchedulePhase.VOTING && phase != BattleSchedulePhase.SCHEDULED) {
			return;
		}
		if (!BattleScheduleService.isOnBattleDay(war, now)) {
			return;
		}
		if (!AttackerNavalContestService.wouldAttackerAutoLoseNaval(war)) {
			return;
		}
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle != null && battle.hasStarted()) {
			return;
		}

		Faction attackerLeader = war.getAttackers() != null ? war.getAttackers().getLeader() : null;
		if (attackerLeader == null || attackerLeader.getLeader() == null) {
			return;
		}
		Player player = Bukkit.getPlayerExact(attackerLeader.getLeader());
		if (player == null || !player.isOnline()) {
			return;
		}
		player.sendMessage(CampaignUiCopy.navalAutoLossLeaderPing());
	}
}
