package net.tfminecraft.simplefactions.war.campaign.progression;


import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import java.util.Optional;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.resolution.ResolutionContext;
import net.tfminecraft.simplefactions.war.resolution.WarResolutionService;

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
