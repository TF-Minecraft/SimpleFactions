package me.Plugins.SimpleFactions.War.campaign.raid;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;

public class RaidTabCompletion implements TabCompleter {

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		if (!cmd.getName().equalsIgnoreCase("raid") || !(sender instanceof Player player)) {
			return List.of();
		}
		if (args.length == 1) {
			List<String> completions = new ArrayList<>();
			if ("join".startsWith(args[0].toLowerCase())) {
				completions.add("join");
			}
			return completions;
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
			Faction faction = FactionManager.getByMember(player.getName());
			if (faction == null) {
				faction = FactionManager.getByLeader(player.getName());
			}
			if (faction == null) {
				return List.of();
			}
			String prefix = args[1].toLowerCase();
			List<String> completions = new ArrayList<>();
			for (String raidId : CampaignRaidJoinService.listJoinableRaidIds(faction)) {
				if (raidId.toLowerCase().startsWith(prefix)) {
					completions.add(raidId);
				}
			}
			return completions;
		}
		return List.of();
	}
}
