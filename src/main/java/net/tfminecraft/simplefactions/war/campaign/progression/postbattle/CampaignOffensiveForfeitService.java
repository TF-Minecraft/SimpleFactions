package net.tfminecraft.simplefactions.war.campaign.progression.postbattle;



import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCapabilityService;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleOutcomeService;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;

public final class CampaignOffensiveForfeitService {
	private CampaignOffensiveForfeitService() {}

	/**
	 * Applies an automatic battle loss when the battle offensive coalition cannot attack.
	 *
	 * @return true if forfeit was applied and the caller should not start a live battle
	 */
	public static boolean applyIfBattleOffensiveCannotAttack(War war, int battleProvinceId) {
		if (war == null || !war.isActive() || battleProvinceId <= 0) {
			return false;
		}
		if (CampaignPostBattleChoiceService.needsAnyChoice(war)) {
			return false;
		}

		CampaignCoalition offensive = CampaignCapabilityService.battleOffensiveCoalition(war);
		if (offensive == null || CampaignCapabilityService.canAttack(war, offensive)) {
			return false;
		}

		CampaignCoalition winner = offensive.opposing();
		BelligerentRole winnerRole = CampaignCoalitionService.coalitionToBelligerentRole(winner);
		if (winnerRole == null) {
			return false;
		}

		purgeUnstartedBattle(war);

		CampaignBattleEndService.snapshotBattleStart(war);
		CampaignBattleOutcomeService.applyCampaignBattleOutcome(war, winnerRole, battleProvinceId);
		CampaignBattleOutcomeService.finalizeCampaignBattleAfterOutcome(war);
		broadcastForfeit(war, offensive, winner, battleProvinceId);
		return true;
	}

	private static void purgeUnstartedBattle(War war) {
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle == null || battle.hasStarted()) {
			return;
		}
		BattlePersistenceService.deleteCampaignBattle(battle);
		BattleManager.clearEditorSessions(battle);
		BattleManager.deleteBattle(battle);
	}

	private static void broadcastForfeit(
			War war,
			CampaignCoalition offensive,
			CampaignCoalition winner,
			int battleProvinceId) {
		if (Bukkit.getServer() == null) {
			return;
		}
		String offensiveLabel = offensive == CampaignCoalition.AGGRESSOR ? "Attacker" : "Defender";
		String winnerLabel = winner == CampaignCoalition.AGGRESSOR ? "attacker" : "defender";
		String message = "§e"
				+ offensiveLabel
				+ " coalition could not field an offensive army at province "
				+ battleProvinceId
				+ ". §7"
				+ winnerLabel
				+ " wins by forfeit.";
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(war.getAttackers())) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendMessage(message);
			}
		}
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(war.getDefenders())) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendMessage(message);
			}
		}
	}
}
