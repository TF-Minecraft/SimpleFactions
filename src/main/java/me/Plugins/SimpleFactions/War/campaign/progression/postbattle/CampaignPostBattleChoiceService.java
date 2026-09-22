package me.Plugins.SimpleFactions.War.campaign.progression.postbattle;



import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCapabilityService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignChoiceService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService;
import java.time.Instant;
import java.util.Optional;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.resolution.WarResolutionService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleService;

public final class CampaignPostBattleChoiceService {
	private CampaignPostBattleChoiceService() {}

	public static boolean needsWinnerChoice(War war) {
		return war != null
				&& war.getPostBattleChoicePhase() == PostBattleChoicePhase.WINNER_PUSH_HOLD
				&& !war.isPostBattleChoiceResolved();
	}

	public static boolean needsLoserResponse(War war) {
		return war != null
				&& war.getPostBattleChoicePhase() == PostBattleChoicePhase.LOSER_ATTACK_PEACE
				&& !war.isPostBattleChoiceResolved();
	}

	public static boolean needsAnyChoice(War war) {
		return needsWinnerChoice(war) || needsLoserResponse(war);
	}

	public static CampaignCoalition choiceLeaderCoalition(War war) {
		if (needsWinnerChoice(war)) {
			return war.getPostBattleWinnerCoalition();
		}
		if (needsLoserResponse(war)) {
			CampaignCoalition winner = war.getPostBattleWinnerCoalition();
			return winner != null ? winner.opposing() : null;
		}
		return null;
	}

	public static boolean applyPushChoice(War war) {
		if (!needsWinnerChoice(war)) {
			return false;
		}
		CampaignCoalition winner = war.getPostBattleWinnerCoalition();
		if (!CampaignCapabilityService.canMountOffensiveAfterPush(war, winner)) {
			return false;
		}
		if (!CampaignBattleEndService.applyPush(war)) {
			return false;
		}
		afterChoiceResolved(war);
		return true;
	}

	public static boolean applyHoldChoice(War war) {
		if (!needsWinnerChoice(war)) {
			return false;
		}
		if (!CampaignBattleEndService.applyHold(war)) {
			return false;
		}
		WarManager.persist(war);
		return true;
	}

	/**
	 * Moment C: winner cannot mount the next offensive after Push - auto Hold immediately.
	 */
	public static boolean resolveMandatoryHoldIfNeeded(War war, CampaignCoalition winner) {
		if (war == null || winner == null) {
			return false;
		}
		if (CampaignCapabilityService.canMountOffensiveAfterPush(war, winner)) {
			return false;
		}
		war.setPostBattleWinnerCoalition(winner);
		if (!CampaignBattleEndService.applyHold(war)) {
			return false;
		}
		WarManager.persist(war);
		return true;
	}

	public static boolean applyLoserAttack(War war) {
		if (!needsLoserResponse(war)) {
			return false;
		}
		if (!CampaignBattleEndService.applyLoserAttack(war)) {
			return false;
		}
		afterChoiceResolved(war);
		return true;
	}

	public static boolean applyLoserAcceptPeace(War war) {
		if (!needsLoserResponse(war)) {
			return false;
		}
		CampaignBattleEndService.resolveChoicePhase(war);
		WarResolutionService.endWhitePeace(war);
		return true;
	}

	public static boolean applyDeadlineIfDue(War war, Instant now) {
		if (war == null || !war.isActive() || now == null) {
			return false;
		}
		if (!BattleScheduleService.isOnBattleDay(war, now)) {
			return false;
		}
		if (!BattleScheduleService.isPostBattleChoiceDeadlineDue(war, now)) {
			return false;
		}
		if (needsWinnerChoice(war)) {
			CampaignCoalition winner = war.getPostBattleWinnerCoalition();
			if (CampaignCapabilityService.canMountOffensiveAfterPush(war, winner)) {
				return applyPushChoice(war);
			}
			return applyHoldChoice(war);
		}
		if (needsLoserResponse(war)) {
			return applyLoserAttack(war);
		}
		return false;
	}

	public static boolean isChoiceLeader(War war, Faction leader) {
		if (war == null || leader == null) {
			return false;
		}
		CampaignCoalition coalition = choiceLeaderCoalition(war);
		return coalition != null && CampaignCoalitionService.isCoalitionWarLeader(war, leader, coalition);
	}

	private static void afterChoiceResolved(War war) {
		CampaignMilitaryWalkoverService.resolvePendingWalkovers(war);
		Optional<WarEndReason> autoEnd = CampaignChoiceService.recalculateAndMaybeEnd(war);
		if (autoEnd.isEmpty() && WarManager.getById(war.getId()) != null) {
			BattleScheduleService.openVote(war);
			WarManager.persist(war);
		}
	}

	public enum PostBattleChoicePhase {
		NONE("none"),
		WINNER_PUSH_HOLD("winner_push_hold"),
		LOSER_ATTACK_PEACE("loser_attack_peace");

		private final String jsonId;

		PostBattleChoicePhase(String jsonId) {
			this.jsonId = jsonId;
		}

		public String toJson() {
			return jsonId;
		}

		public static PostBattleChoicePhase fromJson(String value) {
			if (value == null || value.isBlank()) {
				return NONE;
			}
			for (PostBattleChoicePhase phase : values()) {
				if (phase.jsonId.equalsIgnoreCase(value)) {
					return phase;
				}
			}
			return null;
		}
	}
}
