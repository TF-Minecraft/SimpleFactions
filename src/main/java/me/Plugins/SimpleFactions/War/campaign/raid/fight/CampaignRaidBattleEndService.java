package me.Plugins.SimpleFactions.War.campaign.raid.fight;


import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidMessages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.events.BattleEndedEvent;
import me.Plugins.SimpleFactions.War.battle.persistence.BattlePersistenceService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationLookup;

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
