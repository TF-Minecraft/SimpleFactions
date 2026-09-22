package me.Plugins.SimpleFactions.War.campaign.progression;


import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import java.util.Optional;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.resolution.ResolutionContext;
import me.Plugins.SimpleFactions.War.resolution.WarResolutionService;

public final class CampaignChoiceService {
	private CampaignChoiceService() {}

	public static boolean applyPush(War war) {
		if (!CampaignPostBattleChoiceService.applyPushChoice(war)) {
			return false;
		}
		WarManager.persist(war);
		return true;
	}

	public static boolean applyHold(War war) {
		return CampaignPostBattleChoiceService.applyHoldChoice(war);
	}

	public static boolean applyLoserAttack(War war) {
		if (!CampaignPostBattleChoiceService.applyLoserAttack(war)) {
			return false;
		}
		WarManager.persist(war);
		return true;
	}

	public static boolean applyLoserAcceptPeace(War war) {
		return CampaignPostBattleChoiceService.applyLoserAcceptPeace(war);
	}

	public static Optional<WarEndReason> recalculateAndMaybeEnd(War war) {
		return WarResolutionService.evaluateAndMaybeEnd(war, ResolutionContext.none());
	}

	public static boolean acceptWhitePeaceAndEnd(War war, Faction acceptingLeader) {
		return WarResolutionService.acceptWhitePeaceAndEnd(war, acceptingLeader);
	}
}
