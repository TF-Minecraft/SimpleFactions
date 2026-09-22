package net.tfminecraft.simplefactions.war.campaign.progression.postbattle;




import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCapabilityService;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.campaign.progression.OccupationService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService;
import java.time.Instant;
import java.util.Optional;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.ObjectiveHolder;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.zoc.FortControlService;
import net.tfminecraft.simplefactions.installation.WartimeInstallationService;
import net.tfminecraft.simplefactions.war.pathfinder.TitleManagerProvinceOwnerLookup;
import net.tfminecraft.simplefactions.war.resolution.ResolutionContext;
import net.tfminecraft.simplefactions.war.resolution.WarResolutionService;

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
