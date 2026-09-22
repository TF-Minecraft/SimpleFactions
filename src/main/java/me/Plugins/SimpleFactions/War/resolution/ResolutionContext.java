package me.Plugins.SimpleFactions.War.resolution;

import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCapabilityService;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;

public final class ResolutionContext {
	private final Integer battleProvinceId;
	private final CampaignCoalition battleWinnerCoalition;
	private final CampaignPushTarget preBattlePushTarget;
	private final ObjectiveHolder preBattleObjectiveHeldBy;

	private ResolutionContext(
			Integer battleProvinceId,
			CampaignCoalition battleWinnerCoalition,
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

	public static ResolutionContext forBattle(War war, int battleProvinceId, CampaignCoalition winner) {
		CampaignPushTarget pushTarget = war != null ? CampaignCapabilityService.effectivePushTarget(war) : null;
		ObjectiveHolder objectiveHeldBy = war != null ? war.getObjectiveHeldBy() : null;
		return new ResolutionContext(battleProvinceId, winner, pushTarget, objectiveHeldBy);
	}

	public static ResolutionContext forBattle(
			War war,
			int battleProvinceId,
			CampaignCoalition winner,
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

	public CampaignCoalition battleWinnerCoalition() {
		return battleWinnerCoalition;
	}

	public CampaignPushTarget preBattlePushTarget() {
		return preBattlePushTarget;
	}

	public ObjectiveHolder preBattleObjectiveHeldBy() {
		return preBattleObjectiveHeldBy;
	}
}
