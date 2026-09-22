package me.Plugins.SimpleFactions.War.campaign.progression;


import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushProjection;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import java.util.OptionalInt;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.military.BattlePoolService;
import me.Plugins.SimpleFactions.War.battle.military.PoolMode;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService;

public final class CampaignCapabilityService {
	private CampaignCapabilityService() {}

	public static int capitulationTargetIndex(War war, CampaignCoalition coalition) {
		if (!isValidWar(war) || coalition == null) {
			return -1;
		}
		if (coalition == CampaignCoalition.AGGRESSOR) {
			return objectiveIndex(war);
		}
		return aggressorCapitalIndex(war);
	}

	public static int stepsToCapitulationTarget(War war, CampaignCoalition coalition) {
		if (!isValidWar(war) || coalition == null) {
			return 0;
		}
		int targetIndex = capitulationTargetIndex(war, coalition);
		if (targetIndex < 0) {
			return 0;
		}
		return stepsAlongAxis(war.getCursorIndex(), targetIndex);
	}

	public static int stepsToCapitulationTarget(War war, BelligerentRole side) {
		return stepsToCapitulationTarget(war, CampaignCoalitionService.belligerentRoleToCoalition(side));
	}

	public static CampaignCoalition battleOffensiveCoalition(War war) {
		return CampaignCoalitionService.getInitiativeHolderCoalition(war);
	}

	public static CampaignCoalition battleDefensiveCoalition(War war) {
		CampaignCoalition offensive = battleOffensiveCoalition(war);
		return offensive != null ? offensive.opposing() : null;
	}

	public static int offensiveRegiments(War war, int provinceId, CampaignCoalition coalition) {
		Side side = CampaignCoalitionService.toSide(war, coalition);
		if (side == null) {
			return 0;
		}
		return BattlePoolService.totalCommittedRegiments(war, provinceId, side, PoolMode.OFFENSIVE);
	}

	public static int defensiveRegiments(War war, int provinceId, CampaignCoalition coalition) {
		Side side = CampaignCoalitionService.toSide(war, coalition);
		if (side == null) {
			return 0;
		}
		return BattlePoolService.totalCommittedRegiments(war, provinceId, side, PoolMode.DEFENSIVE);
	}

	public static boolean canDefend(War war, int provinceId, CampaignCoalition coalition) {
		return defensiveRegiments(war, provinceId, coalition) > 0;
	}

	public static OptionalInt nextBattleProvince(War war) {
		if (!isValidWar(war)) {
			return OptionalInt.empty();
		}
		if (war.getPostBattleChoicePhase() != PostBattleChoicePhase.NONE) {
			return OptionalInt.empty();
		}
		if (needsPostBattleChoice(war)) {
			return OptionalInt.empty();
		}

		CampaignPushTarget pushTarget = effectivePushTarget(war);
		CampaignCoalition holder = CampaignCoalitionService.getInitiativeHolderCoalition(war);
		if (holder == null || CampaignCoalitionService.getFuel(war, holder) <= 0) {
			return OptionalInt.empty();
		}

		if (CampaignScheduleService.hasActiveSchedule(war)) {
			return CampaignScheduleService.currentSlot(war)
					.map(slot -> OptionalInt.of(slot.provinceId()))
					.orElse(OptionalInt.empty());
		}

		return switch (pushTarget) {
			case RETAKE_OBJECTIVE -> objectiveProvince(war);
			case TOWARD_AGGRESSOR_CAPITAL -> provinceAtIndex(war, war.getCursorIndex() - 1);
			case TOWARD_OBJECTIVE -> invasionTargetProvince(war);
		};
	}

	public static boolean canAttack(War war, CampaignCoalition coalition) {
		if (!isValidWar(war) || coalition == null) {
			return false;
		}
		if (war.getPostBattleChoicePhase() != PostBattleChoicePhase.NONE) {
			return false;
		}
		if (needsPostBattleChoice(war)) {
			return false;
		}
		if (coalition != CampaignCoalitionService.getInitiativeHolderCoalition(war)) {
			return false;
		}
		if (CampaignCoalitionService.getFuel(war, coalition) < 1) {
			return false;
		}
		OptionalInt next = nextBattleProvince(war);
		if (next.isEmpty()) {
			return false;
		}
		return offensiveRegiments(war, next.getAsInt(), coalition) > 0;
	}

	public static boolean canReachTarget(War war, CampaignCoalition coalition) {
		if (!isValidWar(war) || coalition == null) {
			return false;
		}
		int steps = stepsToCapitulationTarget(war, coalition);
		return CampaignCoalitionService.getFuel(war, coalition) >= steps;
	}

	public static boolean hasOffensiveArmy(War war, CampaignCoalition coalition, int provinceId) {
		if (!isValidWar(war) || coalition == null || provinceId <= 0) {
			return false;
		}
		if (CampaignCoalitionService.getFuel(war, coalition) < 1) {
			return false;
		}
		return offensiveRegiments(war, provinceId, coalition) > 0;
	}

	public static boolean canMountOffensive(War war, CampaignCoalition coalition, int provinceId) {
		return hasOffensiveArmy(war, coalition, provinceId);
	}

	public static boolean canMountOffensiveAtNextBattle(War war, CampaignCoalition coalition) {
		if (!isValidWar(war) || coalition == null) {
			return false;
		}
		if (coalition != CampaignCoalitionService.getInitiativeHolderCoalition(war)) {
			return false;
		}
		if (war.getPostBattleChoicePhase() != PostBattleChoicePhase.NONE || needsPostBattleChoice(war)) {
			return false;
		}
		OptionalInt next = nextBattleProvince(war);
		return next.isPresent() && hasOffensiveArmy(war, coalition, next.getAsInt());
	}

	public static boolean canMountOffensiveAfterPush(War war, CampaignCoalition winner) {
		return CampaignPushProjection.canMountOffensiveAfterPush(war, winner);
	}

	public static boolean needsPostBattleChoice(War war) {
		return CampaignPostBattleChoiceService.needsAnyChoice(war);
	}

	public static int objectiveIndex(War war) {
		if (war == null || war.getObjectiveProvinceId() == null || war.getCampaignProvinces() == null) {
			return -1;
		}
		return war.getCampaignProvinces().indexOf(war.getObjectiveProvinceId());
	}

	public static int aggressorCapitalIndex(War war) {
		if (!isValidWar(war) || war.getAttackers() == null || war.getAttackers().getLeader() == null) {
			return 0;
		}
		int capital = war.getAttackers().getLeader().getCapital();
		if (capital <= 0) {
			return 0;
		}
		int index = war.getCampaignProvinces().indexOf(capital);
		return index >= 0 ? index : 0;
	}

	public static int stepsAlongAxis(int fromIndex, int toIndex) {
		return Math.abs(toIndex - fromIndex);
	}

	public static int clampCursorIndex(War war, int index) {
		int max = war.getCampaignProvinces().size() - 1;
		return Math.max(0, Math.min(index, max));
	}

	public static CampaignPushTarget effectivePushTarget(War war) {
		CampaignPushTarget target = war.getPushTarget();
		if (target != null) {
			return target;
		}
		return CampaignCoalitionService.derivePushTargetFromLegacyPhase(
				war.getCampaignPhase(),
				war.getObjectiveHeldBy());
	}

	private static OptionalInt objectiveProvince(War war) {
		int objectiveIndex = objectiveIndex(war);
		if (objectiveIndex < 0) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(war.getCampaignProvinces().get(objectiveIndex));
	}

	private static OptionalInt provinceAtIndex(War war, int index) {
		if (index < 0 || index >= war.getCampaignProvinces().size()) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(war.getCampaignProvinces().get(index));
	}

	private static OptionalInt invasionTargetProvince(War war) {
		if (war.getCampaignBattlesFought() == 0 && Cache.warFirstBattleAtBorder) {
			return OptionalInt.of(war.getCampaignProvinces().get(war.getCursorIndex()));
		}
		int objectiveIndex = objectiveIndex(war);
		int nextIndex = war.getCursorIndex() + Cache.warProvincesBetweenBattles;
		if (objectiveIndex >= 0) {
			nextIndex = Math.min(nextIndex, objectiveIndex);
		}
		nextIndex = clampCursorIndex(war, nextIndex);
		return OptionalInt.of(war.getCampaignProvinces().get(nextIndex));
	}

	public static boolean isValidWar(War war) {
		return war != null
				&& war.isActive()
				&& war.getCampaignProvinces() != null
				&& !war.getCampaignProvinces().isEmpty()
				&& war.getCursorIndex() >= 0
				&& war.getCursorIndex() < war.getCampaignProvinces().size();
	}
}
