package net.tfminecraft.simplefactions.war.campaign.raid.fight;


import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidMessages;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidState;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import java.time.Instant;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.TransitionResult;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidEligibilityService;

public final class CampaignRaidLaunchService {
	private CampaignRaidLaunchService() {}

	public static void startFight(War war, Instant now) {
		if (war == null || now == null) {
			return;
		}
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null || raid.getState() != CampaignRaidState.MUSTER) {
			return;
		}
		CampaignRaidWarbandService.enrollOnlineDefenders(war, raid);
		Battle battle = CampaignRaidBattleService.createAndStart(war, raid, now);
		if (CampaignRaidService.getActive(war) != raid) {
			return;
		}
		if (battle == null) {
			CampaignRaidService.endRaid(war, now);
			WarManager.persist(war);
			return;
		}
		CampaignRaidService.transitionToFighting(war, now);
		CampaignRaidService.setInstallationRepairLockUntil(
				war,
				CampaignRaidEligibilityService.resolveTargetInstallation(war, raid),
				CampaignRaidService.repairLockUntilFromStart(now));
		CampaignRaidBossBarService.onFightStarted(battle, raid);
		CampaignRaidFightScheduler.onFightStarted(war, now);
		WarManager.persist(war);
		broadcastRaidStarted(war, raid);
	}

	private static void broadcastRaidStarted(War war, CampaignRaid raid) {
		Installation target = CampaignRaidEligibilityService.resolveTargetInstallation(war, raid);
		String message = CampaignRaidMessages.buildRaidStartedMessage(target, raid.getDisplayName());
		broadcastToSide(war.getAttackers(), message);
		broadcastToSide(war.getDefenders(), message);
	}

	private static void broadcastToSide(Side side, String message) {
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(side)) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendMessage(message);
			}
		}
	}
}
