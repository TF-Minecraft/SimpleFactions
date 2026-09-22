package net.tfminecraft.simplefactions.war.campaign.raid.fight;


import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidMessages;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import java.time.Instant;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.TransitionResult;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationLookup;

public final class CampaignRaidLaunchService {
	private CampaignRaidLaunchService() {}

	public static void startFight(War war, Instant now) {
		if (war == null || now == null) {
			return;
		}
		TransitionResult result = CampaignRaidService.transitionToFighting(war, now);
		if (result != TransitionResult.OK) {
			return;
		}
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid != null) {
			CampaignRaidWarbandService.enrollOnlineDefenders(war, raid);
			CampaignRaidService.setRepairLockUntil(
					war,
					raid.getTargetInstallationId(),
					CampaignRaidService.repairLockUntilFromStart(now));
			CampaignRaidBattleService.createAndStart(war, raid, now);
			CampaignRaidFightScheduler.onFightStarted(war, now);
		}
		WarManager.persist(war);
		broadcastRaidStarted(war);
	}

	private static void broadcastRaidStarted(War war) {
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null) {
			return;
		}
		Installation target = InstallationLookup.findById(raid.getTargetInstallationId());
		String message = CampaignRaidMessages.buildRaidStartedMessage(target, raid.getDisplayName());
		broadcastToSide(war.getAttackers(), message);
		broadcastToSide(war.getDefenders(), message);
	}

	private static void broadcastToSide(Side side, String message) {
		if (side == null || message == null) {
			return;
		}
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(side)) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendMessage(message);
			}
		}
	}
}
