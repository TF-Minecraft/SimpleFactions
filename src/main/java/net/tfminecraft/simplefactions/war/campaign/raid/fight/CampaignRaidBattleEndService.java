package net.tfminecraft.simplefactions.war.campaign.raid.fight;


import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidMessages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationLookup;

public class CampaignRaidBattleEndService implements Listener {
	@EventHandler
	public void onBattleEnded(BattleEndedEvent event) {
		handleBattleEnded(event);
	}

	static void handleBattleEnded(BattleEndedEvent event) {
		if (event == null || event.getWarId() == null) {
			return;
		}
		War war = WarManager.getById(event.getWarId());
		if (war == null || !war.isActive()) {
			return;
		}
		if (!CampaignRaidBattleService.isCampaignRaidEvent(war, event)) {
			return;
		}
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null) {
			return;
		}
		Battle battle = BattleManager.getByString(event.getBattleId());
		Installation target = InstallationLookup.findById(raid.getTargetInstallationId());
		String displayName = raid.getDisplayName();
		if (battle != null && battle.getDisplayName() != null && !battle.getDisplayName().isBlank()) {
			displayName = battle.getDisplayName();
		}
		CampaignRaidService.endRaid(war, CampaignClock.now());
		if (battle != null) {
			BattlePersistenceService.deleteRaidBattle(battle);
		}
		broadcastRaidEnded(war, target, displayName);
	}

	private static void broadcastRaidEnded(War war, Installation target, String displayName) {
		if (war == null) {
			return;
		}
		String message = CampaignRaidMessages.buildRaidEndedMessage(target, displayName);
		broadcastToSide(war.getAttackers(), message);
		broadcastToSide(war.getDefenders(), message);
	}

	private static void broadcastToSide(Side side, String message) {
		if (side == null || message == null || Bukkit.getServer() == null) {
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
