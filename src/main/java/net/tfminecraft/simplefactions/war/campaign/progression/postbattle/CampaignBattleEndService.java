package net.tfminecraft.simplefactions.war.campaign.progression.postbattle;




import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCapabilityService;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.ObjectiveHolder;

public final class CampaignBattleEndService {
	private CampaignBattleEndService() {}

	public static void beginPostBattleChoice(War war, CampaignSideRole winner) {
		if (war == null || winner == null) {
			return;
		}
		war.setPostBattleWinnerCoalition(winner);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleChoiceResolved(false);
	}

	public static void snapshotBattleStart(War war) {
		if (war == null) {
			return;
		}
		snapshotBattleStart(war, CampaignCapabilityService.battleOffensiveCoalition(war));
	}

	public static void snapshotBattleStart(War war, CampaignSideRole lastBattleOffensive) {
		if (war == null) {
			return;
		}
		war.setLastBattleOffensiveCoalition(lastBattleOffensive);
	}

	public static void spendOffensiveFuel(War war) {
		CampaignSideRole offensive = war.getLastBattleOffensiveCoalition();
		if (offensive == null) {
			offensive = CampaignCapabilityService.battleOffensiveCoalition(war);
		}
		CampaignSideService.spendFuel(war, offensive);
	}

	public static boolean applyPush(War war) {
		if (war == null || war.getPostBattleWinnerCoalition() == null) {
			return false;
		}
		CampaignSideRole winner = war.getPostBattleWinnerCoalition();
		CampaignSideRole offensive = war.getLastBattleOffensiveCoalition();
		if (offensive == null) {
			offensive = CampaignCapabilityService.battleOffensiveCoalition(war);
		}
		boolean offensiveWon = winner == offensive;
		if (offensiveWon) {
			advanceAlongPushTarget(war);
		} else {
			advanceTowardCapitulationTarget(war, winner);
		}
		CampaignSideService.setInitiativeHolderCoalition(war, winner);
		clearHoldPeace(war);
		resolveChoicePhase(war);
		return true;
	}

	public static boolean applyHold(War war) {
		if (war == null || war.getPostBattleWinnerCoalition() == null) {
			return false;
		}
		CampaignSideRole winner = war.getPostBattleWinnerCoalition();
		war.setHoldPeaceProposalActive(true);
		CampaignSideService.setWhitePeaceProposed(war, winner, true);
		CampaignSideService.setInitiativeHolderCoalition(war, winner);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.LOSER_ATTACK_PEACE);
		war.setPostBattleChoiceResolved(false);
		return true;
	}

	public static boolean applyLoserAttack(War war) {
		if (war == null || war.getPostBattleWinnerCoalition() == null) {
			return false;
		}
		CampaignSideRole loser = war.getPostBattleWinnerCoalition().opposing();
		CampaignSideService.setInitiativeHolderCoalition(war, loser);
		clearHoldPeace(war);
		resolveChoicePhase(war);
		return true;
	}

	public static void clearHoldPeace(War war) {
		if (war == null) {
			return;
		}
		war.setHoldPeaceProposalActive(false);
	}

	public static void resolveChoicePhase(War war) {
		if (war == null) {
			return;
		}
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		war.setPostBattleWinnerCoalition(null);
	}

	static void advanceAlongPushTarget(War war) {
		CampaignPushTarget target = CampaignCapabilityService.effectivePushTarget(war);
		int cursor = war.getCursorIndex();
		int objectiveIndex = CampaignCapabilityService.objectiveIndex(war);

		switch (target) {
			case TOWARD_OBJECTIVE -> {
				if (cursor == objectiveIndex) {
					war.setObjectiveHeldBy(ObjectiveHolder.ATTACKER);
					war.setPushTarget(CampaignPushTarget.RETAKE_OBJECTIVE);
				} else {
					war.setCursorIndex(CampaignCapabilityService.clampCursorIndex(war, cursor + 1));
				}
			}
			case TOWARD_AGGRESSOR_CAPITAL -> war.setCursorIndex(
					CampaignCapabilityService.clampCursorIndex(war, cursor - 1));
			case RETAKE_OBJECTIVE -> {
				war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
				war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
			}
		}
		syncLegacyPhase(war);
	}

	static void advanceTowardCapitulationTarget(War war, CampaignSideRole winner) {
		int cursor = war.getCursorIndex();
		if (winner == CampaignSideRole.AGGRESSOR) {
			war.setCursorIndex(CampaignCapabilityService.clampCursorIndex(war, cursor + 1));
			if (war.getPushTarget() == CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL) {
				war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
			}
		} else {
			war.setCursorIndex(CampaignCapabilityService.clampCursorIndex(war, cursor - 1));
			war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);
		}
		syncLegacyPhase(war);
	}

	static void syncLegacyPhase(War war) {
		war.setCampaignPhase(CampaignSideService.deriveLegacyPhaseFromPushTarget(
				war.getPushTarget(),
				war.getObjectiveHeldBy()));
	}
}
