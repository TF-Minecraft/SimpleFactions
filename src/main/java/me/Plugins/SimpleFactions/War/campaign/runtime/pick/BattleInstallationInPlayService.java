package me.Plugins.SimpleFactions.War.campaign.runtime.pick;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import me.Plugins.SimpleFactions.War.core.War;

public final class BattleInstallationInPlayService {
	private BattleInstallationInPlayService() {}

	public static boolean isInPlay(War war, String factionId, String installationId) {
		if (war == null || factionId == null || factionId.isBlank()
				|| installationId == null || installationId.isBlank()) {
			return false;
		}
		if (isInPlayForCampaignRaid(war, factionId, installationId)) {
			return true;
		}
		return BattleInstallationPickService.getPicks(war, factionId).contains(installationId)
				|| BattleSiegeFortService.isSiegeFortInPlayForFaction(war, factionId, installationId);
	}

	private static boolean isInPlayForCampaignRaid(War war, String factionId, String installationId) {
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null || raid.getState() != CampaignRaidState.FIGHTING) {
			return false;
		}
		CampaignCoalition attackerCoalition = raid.getAttackerCoalition();
		if (attackerCoalition == null) {
			return false;
		}
		Faction faction = FactionManager.getByString(factionId);
		if (faction == null) {
			return false;
		}
		CampaignCoalition coalition = CampaignRaidService.coalitionForFaction(war, faction);
		if (coalition == null) {
			return false;
		}
		if (coalition == attackerCoalition && installationId.equals(raid.getSourceInstallationId())) {
			return true;
		}
		return coalition == attackerCoalition.opposing()
				&& installationId.equals(raid.getTargetInstallationId());
	}
}
