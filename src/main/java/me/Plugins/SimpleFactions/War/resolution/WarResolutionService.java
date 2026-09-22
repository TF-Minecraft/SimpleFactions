package me.Plugins.SimpleFactions.War.resolution;

import java.util.Optional;
import java.util.OptionalInt;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCapabilityService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.progression.WhitePeaceService;

public final class WarResolutionService {
	private WarResolutionService() {}

	public static Optional<WarEndReason> evaluateAndMaybeEnd(War war, ResolutionContext context) {
		if (war == null || !war.isActive()) {
			return Optional.empty();
		}

		Optional<WarEndReason> battleVictory = detectBattleVictory(war, context);
		if (battleVictory.isPresent()) {
			endWar(war, battleVictory.get());
			return battleVictory;
		}

		if (isOffensiveStalemate(war)) {
			endWar(war, WarEndReason.WHITE_PEACE);
			return Optional.of(WarEndReason.WHITE_PEACE);
		}

		Optional<WarEndReason> peace = WhitePeaceService.recalculateProposals(war);
		peace.ifPresent(reason -> endWar(war, reason));
		return peace;
	}

	public static Optional<WarEndReason> tryEndAfterBattle(
			War war,
			int battleProvinceId,
			CampaignCoalition winner,
			CampaignPushTarget preBattlePushTarget,
			ObjectiveHolder preBattleObjectiveHeldBy) {
		if (war == null || !war.isActive() || winner == null) {
			return Optional.empty();
		}
		ResolutionContext context = ResolutionContext.forBattle(
				war,
				battleProvinceId,
				winner,
				preBattlePushTarget,
				preBattleObjectiveHeldBy);
		Optional<WarEndReason> victory = detectBattleVictory(war, context);
		victory.ifPresent(reason -> endWar(war, reason));
		return victory;
	}

	public static boolean surrender(War war, Faction surrenderingLeader) {
		if (war == null || !war.isActive() || surrenderingLeader == null || surrenderingLeader.getId() == null) {
			return false;
		}
		String leaderId = surrenderingLeader.getId();
		if (leaderId.equalsIgnoreCase(war.getAttackerLeaderId())) {
			endWar(war, WarEndReason.DEFENDER_VICTORY);
			return true;
		}
		if (leaderId.equalsIgnoreCase(war.getDefenderLeaderId())) {
			endWar(war, WarEndReason.ATTACKER_VICTORY);
			return true;
		}
		return false;
	}

	public static boolean acceptWhitePeaceAndEnd(War war, Faction acceptingLeader) {
		if (!WhitePeaceService.acceptWhitePeace(war, acceptingLeader)) {
			return false;
		}
		endWar(war, WarEndReason.WHITE_PEACE);
		return true;
	}

	public static void endWhitePeace(War war) {
		endWar(war, WarEndReason.WHITE_PEACE);
	}

	static Optional<WarEndReason> detectBattleVictory(War war, ResolutionContext context) {
		if (war == null || context == null) {
			return Optional.empty();
		}
		Integer provinceId = context.battleProvinceId();
		CampaignCoalition winner = context.battleWinnerCoalition();
		if (provinceId == null || provinceId <= 0 || winner == null) {
			return Optional.empty();
		}

		Integer objectiveProvinceId = war.getObjectiveProvinceId();
		if (war.getGoal() == WarGoalType.PILLAGE
				&& objectiveProvinceId != null
				&& provinceId.equals(objectiveProvinceId)) {
			if (winner == CampaignCoalition.AGGRESSOR) {
				return Optional.of(WarEndReason.ATTACKER_VICTORY);
			}
			if (winner == CampaignCoalition.DEFENDER) {
				return Optional.of(WarEndReason.DEFENDER_VICTORY);
			}
		}

		int defenderCapital = capitalProvinceId(war.getDefenders().getLeader());
		int attackerCapital = capitalProvinceId(war.getAttackers().getLeader());

		if (defenderCapital > 0
				&& provinceId == defenderCapital
				&& winner == CampaignCoalition.AGGRESSOR) {
			return Optional.of(WarEndReason.ATTACKER_VICTORY);
		}
		if (attackerCapital > 0
				&& provinceId == attackerCapital
				&& winner == CampaignCoalition.DEFENDER) {
			return Optional.of(WarEndReason.DEFENDER_VICTORY);
		}

		CampaignPushTarget pushTarget = context.preBattlePushTarget() != null
				? context.preBattlePushTarget()
				: CampaignCapabilityService.effectivePushTarget(war);
		ObjectiveHolder objectiveHeldBy = context.preBattleObjectiveHeldBy() != null
				? context.preBattleObjectiveHeldBy()
				: war.getObjectiveHeldBy();
		if (pushTarget == CampaignPushTarget.RETAKE_OBJECTIVE
				&& objectiveHeldBy == ObjectiveHolder.ATTACKER
				&& objectiveProvinceId != null
				&& provinceId.equals(objectiveProvinceId)
				&& winner == CampaignCoalition.AGGRESSOR) {
			return Optional.of(WarEndReason.ATTACKER_VICTORY);
		}

		return Optional.empty();
	}

	static boolean isOffensiveStalemate(War war) {
		if (!CampaignCapabilityService.isValidWar(war)) {
			return false;
		}
		if (CampaignPostBattleChoiceService.needsAnyChoice(war)) {
			return false;
		}
		BattleSchedulePhase phase = war.getBattleSchedulePhase();
		if (phase != BattleSchedulePhase.VOTING && phase != BattleSchedulePhase.SCHEDULED) {
			return false;
		}
		OptionalInt next = CampaignCapabilityService.nextBattleProvince(war);
		if (next.isEmpty()) {
			return false;
		}
		int provinceId = next.getAsInt();
		boolean aggressorCan = CampaignCapabilityService.canMountOffensive(
				war, CampaignCoalition.AGGRESSOR, provinceId);
		boolean defenderCan = CampaignCapabilityService.canMountOffensive(
				war, CampaignCoalition.DEFENDER, provinceId);
		return !aggressorCan && !defenderCan;
	}

	private static int capitalProvinceId(Faction faction) {
		if (faction == null) {
			return 0;
		}
		return faction.getCapital();
	}

	private static void endWar(War war, WarEndReason reason) {
		WarManager.endWar(war, reason);
	}
}
