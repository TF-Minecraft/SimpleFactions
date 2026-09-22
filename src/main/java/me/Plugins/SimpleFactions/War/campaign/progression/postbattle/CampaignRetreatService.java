package me.Plugins.SimpleFactions.War.campaign.progression.postbattle;




import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCapabilityService;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.progression.OccupationService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService;
import java.time.Instant;
import java.util.Optional;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleService;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortControlService;
import me.Plugins.SimpleFactions.installation.WartimeInstallationService;
import me.Plugins.SimpleFactions.War.pathfinder.TitleManagerProvinceOwnerLookup;
import me.Plugins.SimpleFactions.War.resolution.ResolutionContext;
import me.Plugins.SimpleFactions.War.resolution.WarResolutionService;

public final class CampaignRetreatService {
	public enum RetreatResult {
		SUCCESS,
		REJECTED_NOT_ELIGIBLE,
		REJECTED_NOT_LEADER,
		REJECTED_VOTE_CLOSED,
		REJECTED_NO_ACTIVE_SLOT,
		REJECTED_POST_BATTLE_CHOICE
	}

	public record ConcedeResult(
			RetreatResult result,
			Optional<WarEndReason> autoEndReason) {
		public static ConcedeResult rejected(RetreatResult result) {
			return new ConcedeResult(result, Optional.empty());
		}
	}

	private CampaignRetreatService() {
	}

	public static CampaignCoalition pushedCoalition(War war) {
		if (war == null) {
			return null;
		}
		return switch (CampaignCapabilityService.effectivePushTarget(war)) {
			case TOWARD_OBJECTIVE -> CampaignCoalition.DEFENDER;
			case TOWARD_AGGRESSOR_CAPITAL -> CampaignCoalition.AGGRESSOR;
			case RETAKE_OBJECTIVE -> null;
		};
	}

	public static String slotKey(ScheduleLeg leg, int index) {
		if (leg == null) {
			return null;
		}
		String legId = leg == ScheduleLeg.COUNTER ? "counter" : "invasion";
		return legId + ":" + index;
	}

	public static boolean isSlotConceded(War war, ScheduleLeg leg, int index) {
		if (war == null || leg == null || index < 0) {
			return false;
		}
		String key = slotKey(leg, index);
		return key != null && war.getConcededScheduleSlots().contains(key);
	}

	public static boolean canRetreat(War war, Faction leader, Instant now) {
		return retreatRejection(war, leader, now) == null;
	}

	public static ConcedeResult concedeActiveSlot(War war, Faction leader, Instant now) {
		RetreatResult rejection = retreatRejection(war, leader, now);
		if (rejection != null) {
			return ConcedeResult.rejected(rejection);
		}

		CampaignPushTarget preBattlePushTarget = war.getPushTarget();
		ObjectiveHolder preBattleObjectiveHeldBy = war.getObjectiveHeldBy();
		ScheduleLeg leg = CampaignScheduleService.activeLeg(war);
		int index = CampaignScheduleService.getActiveScheduleIndex(war);
		ScheduledCampaignBattle slot = CampaignScheduleService.slotAtActiveIndex(war).orElse(null);
		if (slot == null) {
			return ConcedeResult.rejected(RetreatResult.REJECTED_NO_ACTIVE_SLOT);
		}

		int provinceId = slot.provinceId();
		CampaignCoalition pusher = CampaignCapabilityService.battleOffensiveCoalition(war);
		if (pusher == null) {
			return ConcedeResult.rejected(RetreatResult.REJECTED_NOT_ELIGIBLE);
		}
		BelligerentRole winnerRole = CampaignCoalitionService.coalitionToBelligerentRole(pusher);

		if (slot.kind() == CampaignBattleKind.SIEGE && slot.fortInstallationId() != null) {
			FortControlService.setController(war, slot.fortInstallationId(), pusher);
		}

		occupationService().applyBattleWin(war, provinceId, winnerRole);
		WartimeInstallationService.occupySiegeFort(war, winnerRole, slot);
		war.addConcededScheduleSlot(slotKey(leg, index));
		CampaignScheduleService.advanceIndex(war);
		CampaignBattleEndService.advanceAlongPushTarget(war);
		CampaignCoalitionService.setInitiativeHolderCoalition(war, pusher);
		CampaignBattleEndService.clearHoldPeace(war);

		Optional<WarEndReason> autoEnd = WarResolutionService.tryEndAfterBattle(
				war,
				provinceId,
				pusher,
				preBattlePushTarget,
				preBattleObjectiveHeldBy);
		if (autoEnd.isPresent()) {
			return new ConcedeResult(RetreatResult.SUCCESS, autoEnd);
		}

		if (WarManager.getById(war.getId()) != null) {
			CampaignMilitaryWalkoverService.resolvePendingWalkovers(war);
			if (WarManager.getById(war.getId()) != null) {
				autoEnd = WarResolutionService.evaluateAndMaybeEnd(war, ResolutionContext.none());
			}
			WarManager.persist(war);
		}

		return new ConcedeResult(RetreatResult.SUCCESS, autoEnd);
	}

	private static RetreatResult retreatRejection(War war, Faction leader, Instant now) {
		if (war == null || !war.isActive()) {
			return RetreatResult.REJECTED_NOT_ELIGIBLE;
		}
		if (war.getBattleSchedulePhase() != BattleSchedulePhase.VOTING) {
			return RetreatResult.REJECTED_NOT_ELIGIBLE;
		}
		if (war.getPostBattleChoicePhase() != PostBattleChoicePhase.NONE
				|| CampaignPostBattleChoiceService.needsAnyChoice(war)) {
			return RetreatResult.REJECTED_POST_BATTLE_CHOICE;
		}
		if (now != null && BattleScheduleService.isVoteCloseDue(war, now)) {
			return RetreatResult.REJECTED_VOTE_CLOSED;
		}
		CampaignPushTarget pushTarget = CampaignCapabilityService.effectivePushTarget(war);
		if (pushTarget != CampaignPushTarget.TOWARD_OBJECTIVE
				&& pushTarget != CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL) {
			return RetreatResult.REJECTED_NOT_ELIGIBLE;
		}
		if (!CampaignScheduleService.hasActiveSchedule(war)
				|| CampaignScheduleService.slotAtActiveIndex(war).isEmpty()) {
			return RetreatResult.REJECTED_NO_ACTIVE_SLOT;
		}
		CampaignCoalition pushed = pushedCoalition(war);
		if (pushed == null || leader == null
				|| !CampaignCoalitionService.isCoalitionWarLeader(war, leader, pushed)) {
			return RetreatResult.REJECTED_NOT_LEADER;
		}
		return null;
	}

	private static OccupationService occupationService() {
		if (SimpleFactions.plugin != null) {
			return new OccupationService(
					SimpleFactions.plugin.getProvinceManager(),
					new TitleManagerProvinceOwnerLookup());
		}
		return new OccupationService(null, new TitleManagerProvinceOwnerLookup());
	}
}
