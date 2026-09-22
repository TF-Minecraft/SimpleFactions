package me.Plugins.SimpleFactions.War.campaign.raid;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.JoinResult;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.core.War;

public class RaidCommandManager implements CommandExecutor {
	public String cmd = "raid";

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			return true;
		}
		if (args.length < 1) {
			player.sendMessage("§a[Raid]§c Usage: /raid join <raid>");
			return true;
		}
		if (!args[0].equalsIgnoreCase("join") || args.length != 2) {
			player.sendMessage("§a[Raid]§c Usage: /raid join <raid>");
			return true;
		}

		Faction faction = FactionManager.getByMember(player.getName());
		if (faction == null) {
			faction = FactionManager.getByLeader(player.getName());
		}
		if (faction == null) {
			player.sendMessage(CampaignRaidMessages.NOT_PARTICIPANT);
			return true;
		}

		War war = CampaignRaidJoinService.findWarByRaidId(args[1]);
		if (war == null) {
			player.sendMessage(CampaignRaidMessages.RAID_NOT_FOUND);
			return true;
		}

		JoinResult result = CampaignRaidJoinService.join(
				war, player.getUniqueId(), player.getName(), faction, args[1], CampaignClock.now());
		if (result != JoinResult.OK) {
			String message = CampaignRaidMessages.messageForJoinResult(result);
			if (message != null) {
				player.sendMessage(message);
			}
			return true;
		}

		WarManager.persist(war);
		player.sendMessage(CampaignRaidMessages.JOINED);
		return true;
	}
}
