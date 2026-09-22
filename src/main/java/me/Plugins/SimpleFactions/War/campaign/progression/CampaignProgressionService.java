package me.Plugins.SimpleFactions.War.campaign.progression;


import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import me.Plugins.SimpleFactions.War.core.War;

public final class CampaignProgressionService {
	private CampaignProgressionService() {}

	public static BelligerentRole getInitiativeHolder(War war) {
		if (!CampaignCapabilityService.isValidWar(war)) {
			return null;
		}
		return CampaignCoalitionService.coalitionToBelligerentRole(
				CampaignCoalitionService.getInitiativeHolderCoalition(war));
	}

	public static boolean holdsInitiative(War war, BelligerentRole side) {
		if (side == null) {
			return false;
		}
		return side == getInitiativeHolder(war);
	}

	public static boolean holdsInitiative(War war, CampaignCoalition coalition) {
		return coalition != null
				&& coalition == CampaignCoalitionService.getInitiativeHolderCoalition(war);
	}

	public static List<Integer> resolveNextBattleNodes(War war) {
		if (!CampaignCapabilityService.isValidWar(war)) {
			return List.of();
		}
		if (CampaignPostBattleChoiceService.needsAnyChoice(war)) {
			return List.of();
		}
		OptionalInt next = CampaignCapabilityService.nextBattleProvince(war);
		if (next.isEmpty()) {
			return List.of();
		}
		return List.of(next.getAsInt());
	}

	public static int stepsToCapitulationTarget(War war, BelligerentRole side) {
		return CampaignCapabilityService.stepsToCapitulationTarget(war, side);
	}

	public static int stepsToCapitulationTarget(War war, CampaignCoalition coalition) {
		return CampaignCapabilityService.stepsToCapitulationTarget(war, coalition);
	}

	public static void applyPostponedBattle(War war) {
		// Postponed battles spend no initiative and do not move the cursor (step 59).
	}

	static int getObjectiveIndex(War war) {
		return CampaignCapabilityService.objectiveIndex(war);
	}

	static int clampCursorIndex(War war, int index) {
		return CampaignCapabilityService.clampCursorIndex(war, index);
	}
}
