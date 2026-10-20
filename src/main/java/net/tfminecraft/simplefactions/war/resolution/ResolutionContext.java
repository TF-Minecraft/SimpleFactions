package net.tfminecraft.simplefactions.war.resolution;

import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.ObjectiveHolder;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCapabilityService;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;

public final class ResolutionContext {
	private final Integer battleProvinceId;
	private final CampaignSideRole battleWinnerCoalition;
	private final CampaignPushTarget preBattlePushTarget;
	private final ObjectiveHolder preBattleObjectiveHeldBy;

	private ResolutionContext(
			Integer battleProvinceId,
			CampaignSideRole battleWinnerCoalition,
			CampaignPushTarget preBattlePushTarget,
			ObjectiveHolder preBattleObjectiveHeldBy) {
		this.battleProvinceId = battleProvinceId;
		this.battleWinnerCoalition = battleWinnerCoalition;
		this.preBattlePushTarget = preBattlePushTarget;
		this.preBattleObjectiveHeldBy = preBattleObjectiveHeldBy;
	}

	public static ResolutionContext none() {
		return new ResolutionContext(null, null, null, null);
	}

	public static ResolutionContext forBattle(War war, int battleProvinceId, CampaignSideRole winner) {
		CampaignPushTarget pushTarget = war != null ? CampaignCapabilityService.effectivePushTarget(war) : null;
		ObjectiveHolder objectiveHeldBy = war != null ? war.getObjectiveHeldBy() : null;
		return new ResolutionContext(battleProvinceId, winner, pushTarget, objectiveHeldBy);
	}

	public static ResolutionContext forBattle(
			War war,
			int battleProvinceId,
			CampaignSideRole winner,
			CampaignPushTarget preBattlePushTarget,
			ObjectiveHolder preBattleObjectiveHeldBy) {
		return new ResolutionContext(
				battleProvinceId,
				winner,
				preBattlePushTarget,
				preBattleObjectiveHeldBy);
	}

	public Integer battleProvinceId() {
		return battleProvinceId;
	}

	public CampaignSideRole battleWinnerCoalition() {
		return battleWinnerCoalition;
	}

	public CampaignPushTarget preBattlePushTarget() {
		return preBattlePushTarget;
	}

	public ObjectiveHolder preBattleObjectiveHeldBy() {
		return preBattleObjectiveHeldBy;
	}
}
