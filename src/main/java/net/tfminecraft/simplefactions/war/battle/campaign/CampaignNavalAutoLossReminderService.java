package net.tfminecraft.simplefactions.war.battle.campaign;

import java.time.Instant;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.campaign.progression.AttackerNavalContestService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiCopy;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;

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
