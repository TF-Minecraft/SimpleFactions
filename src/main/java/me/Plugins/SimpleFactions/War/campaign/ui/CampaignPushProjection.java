package me.Plugins.SimpleFactions.War.campaign.ui;



import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCapabilityService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignNavyGate;
import java.util.OptionalInt;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService;

/**
 * Dry-run projection of campaign state after a winner chooses Push.
 */
public final class CampaignPushProjection {
	private CampaignPushProjection() {}

	public static boolean canMountOffensiveAfterPush(War war, CampaignCoalition winner) {
		if (war == null || winner == null || !CampaignCapabilityService.isValidWar(war)) {
			return false;
		}
		ProjectedState projected = afterPush(war, winner);
		OptionalInt next = nextBattleProvince(war, projected);
		if (next.isEmpty()) {
			return false;
		}
		if (!CampaignCapabilityService.hasOffensiveArmy(war, winner, next.getAsInt())) {
			return false;
		}
		return CampaignNavyGate.winnerCanContestNextNaval(war, winner);
	}

	static ProjectedState afterPush(War war, CampaignCoalition winner) {
		CampaignCoalition offensive = war.getLastBattleOffensiveCoalition();
		if (offensive == null) {
			offensive = CampaignCapabilityService.battleOffensiveCoalition(war);
		}

		int cursor = war.getCursorIndex();
		CampaignPushTarget pushTarget = CampaignCapabilityService.effectivePushTarget(war);
		ObjectiveHolder objectiveHeldBy = war.getObjectiveHeldBy();

		if (winner == offensive) {
			int objectiveIndex = CampaignCapabilityService.objectiveIndex(war);
			switch (pushTarget) {
				case TOWARD_OBJECTIVE -> {
					if (cursor == objectiveIndex) {
						objectiveHeldBy = ObjectiveHolder.ATTACKER;
						pushTarget = CampaignPushTarget.RETAKE_OBJECTIVE;
					} else {
						cursor = CampaignCapabilityService.clampCursorIndex(war, cursor + 1);
					}
				}
				case TOWARD_AGGRESSOR_CAPITAL -> cursor = CampaignCapabilityService.clampCursorIndex(war, cursor - 1);
				case RETAKE_OBJECTIVE -> {
					objectiveHeldBy = ObjectiveHolder.DEFENDER;
					pushTarget = CampaignPushTarget.TOWARD_OBJECTIVE;
				}
			}
		} else {
			if (winner == CampaignCoalition.AGGRESSOR) {
				cursor = CampaignCapabilityService.clampCursorIndex(war, cursor + 1);
				if (pushTarget == CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL) {
					pushTarget = CampaignPushTarget.TOWARD_OBJECTIVE;
				}
			} else {
				cursor = CampaignCapabilityService.clampCursorIndex(war, cursor - 1);
				pushTarget = CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL;
			}
		}

		return new ProjectedState(cursor, pushTarget, objectiveHeldBy, winner, war.getCampaignBattlesFought());
	}

	static OptionalInt nextBattleProvince(War war, ProjectedState state) {
		if (!CampaignCapabilityService.isValidWar(war) || state == null || state.initiativeHolder() == null) {
			return OptionalInt.empty();
		}
		if (CampaignCoalitionService.getFuel(war, state.initiativeHolder()) <= 0) {
			return OptionalInt.empty();
		}

		if (CampaignScheduleService.hasActiveSchedule(war)) {
			return CampaignScheduleService.currentSlot(war)
					.map(slot -> OptionalInt.of(slot.provinceId()))
					.orElse(OptionalInt.empty());
		}

		return switch (state.pushTarget()) {
			case RETAKE_OBJECTIVE -> objectiveProvince(war);
			case TOWARD_AGGRESSOR_CAPITAL -> provinceAtIndex(war, state.cursorIndex() - 1);
			case TOWARD_OBJECTIVE -> invasionTargetProvince(war, state);
		};
	}

	private static OptionalInt objectiveProvince(War war) {
		int objectiveIndex = CampaignCapabilityService.objectiveIndex(war);
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

	private static OptionalInt invasionTargetProvince(War war, ProjectedState state) {
		if (state.campaignBattlesFought() == 0 && Cache.warFirstBattleAtBorder) {
			return OptionalInt.of(war.getCampaignProvinces().get(state.cursorIndex()));
		}
		int objectiveIndex = CampaignCapabilityService.objectiveIndex(war);
		int nextIndex = state.cursorIndex() + Cache.warProvincesBetweenBattles;
		if (objectiveIndex >= 0) {
			nextIndex = Math.min(nextIndex, objectiveIndex);
		}
		nextIndex = CampaignCapabilityService.clampCursorIndex(war, nextIndex);
		return OptionalInt.of(war.getCampaignProvinces().get(nextIndex));
	}

	record ProjectedState(
			int cursorIndex,
			CampaignPushTarget pushTarget,
			ObjectiveHolder objectiveHeldBy,
			CampaignCoalition initiativeHolder,
			int campaignBattlesFought) {}
}
