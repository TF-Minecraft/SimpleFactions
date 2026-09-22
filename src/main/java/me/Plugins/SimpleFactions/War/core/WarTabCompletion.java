package me.Plugins.SimpleFactions.War.core;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.Permissions;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;

public class WarTabCompletion implements TabCompleter {

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		if (!cmd.getName().equalsIgnoreCase(WarCommandManager.CMD)) {
			return List.of();
		}
		if (args.length == 1) {
			List<String> completions = new ArrayList<>();
			if ("list".startsWith(args[0].toLowerCase())) {
				completions.add("list");
			}
			if (Permissions.isAdmin(sender) && "admin".startsWith(args[0].toLowerCase())) {
				completions.add("admin");
			}
			return completions;
		}
		if (!Permissions.isAdmin(sender) || !args[0].equalsIgnoreCase("admin")) {
			return List.of();
		}
		if (args.length == 2) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[1], "end");
			addIfPrefix(completions, args[1], "win");
			addIfPrefix(completions, args[1], "status");
			addIfPrefix(completions, args[1], "path");
			addIfPrefix(completions, args[1], "time");
			addIfPrefix(completions, args[1], "schedule");
			addIfPrefix(completions, args[1], "devmode");
			addIfPrefix(completions, args[1], "raid");
			addIfPrefix(completions, args[1], "reparations");
			addIfPrefix(completions, args[1], "factions");
			return completions;
		}
		if (args.length == 3 && args[1].equalsIgnoreCase("reparations")) {
			return factionIds(args[2]);
		}
		if (args.length == 3 && args[1].equalsIgnoreCase("factions")) {
			return factionIds(args[2]);
		}
		if (args.length == 4 && args[1].equalsIgnoreCase("reparations")) {
			return factionIds(args[3]);
		}
		if (args.length == 3 && args[1].equalsIgnoreCase("devmode")) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[2], "on");
			addIfPrefix(completions, args[2], "off");
			addIfPrefix(completions, args[2], "status");
			return completions;
		}
		if (args.length == 3 && args[1].equalsIgnoreCase("raid")) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[2], "resetquota");
			return completions;
		}
		if (args.length == 4 && args[1].equalsIgnoreCase("raid") && args[2].equalsIgnoreCase("resetquota")) {
			return activeWarIds(args[3]);
		}
		if (args.length == 5 && args[1].equalsIgnoreCase("raid") && args[2].equalsIgnoreCase("resetquota")) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[4], "aggressor");
			addIfPrefix(completions, args[4], "defender");
			addIfPrefix(completions, args[4], "both");
			return completions;
		}
		if (args.length == 3 && args[1].equalsIgnoreCase("time")) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[2], "status");
			addIfPrefix(completions, args[2], "reset");
			addIfPrefix(completions, args[2], "add");
			addIfPrefix(completions, args[2], "skip-to-battle-day");
			return completions;
		}
		if (args.length == 3
				&& (args[1].equalsIgnoreCase("end")
						|| args[1].equalsIgnoreCase("win")
						|| args[1].equalsIgnoreCase("status")
						|| args[1].equalsIgnoreCase("path")
						|| args[1].equalsIgnoreCase("schedule"))) {
			return activeWarIds(args[2]);
		}
		if (args.length == 4 && args[1].equalsIgnoreCase("win")) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[3], "attacker");
			addIfPrefix(completions, args[3], "defender");
			return completions;
		}
		if (args.length == 4
				&& args[1].equalsIgnoreCase("time")
				&& args[2].equalsIgnoreCase("skip-to-battle-day")) {
			return activeWarIds(args[3]);
		}
		if (args.length == 4 && args[1].equalsIgnoreCase("time") && args[2].equalsIgnoreCase("add")) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[3], "1h");
			addIfPrefix(completions, args[3], "1d");
			addIfPrefix(completions, args[3], "4h");
			addIfPrefix(completions, args[3], "1h31m");
			return completions;
		}
		if (args.length == 4 && args[1].equalsIgnoreCase("schedule")) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[3], "opencvote");
			addIfPrefix(completions, args[3], "closevote");
			addIfPrefix(completions, args[3], "skipday");
			addIfPrefix(completions, args[3], "castvote");
			addIfPrefix(completions, args[3], "forcequorum");
			addIfPrefix(completions, args[3], "setscheduled");
			addIfPrefix(completions, args[3], "battlecreate");
			addIfPrefix(completions, args[3], "battledelete");
			addIfPrefix(completions, args[3], "battlestart");
			addIfPrefix(completions, args[3], "winbattle");
			addIfPrefix(completions, args[3], "choice");
			addIfPrefix(completions, args[3], "battlechoice");
			addIfPrefix(completions, args[3], "defenderchoice");
			return completions;
		}
		if (args.length == 5 && args[1].equalsIgnoreCase("schedule")) {
			if ("winbattle".equalsIgnoreCase(args[3])) {
				List<String> completions = new ArrayList<>();
				addIfPrefix(completions, args[4], "attacker");
				addIfPrefix(completions, args[4], "defender");
				return completions;
			}
			if (isScheduleChoiceSubcommand(args[3])) {
				List<String> completions = new ArrayList<>();
				addIfPrefix(completions, args[4], "push");
				addIfPrefix(completions, args[4], "hold");
				addIfPrefix(completions, args[4], "attack");
				addIfPrefix(completions, args[4], "accept");
				return completions;
			}
		}
		if (args.length == 5 && args[1].equalsIgnoreCase("schedule") && args[3].equalsIgnoreCase("castvote")) {
			List<String> completions = new ArrayList<>();
			for (int hour : BattleWindowService.listValidHours()) {
				String value = String.valueOf(hour);
				if (value.startsWith(args[4])) {
					completions.add(value);
				}
			}
			return completions;
		}
		if (args.length == 6 && args[1].equalsIgnoreCase("schedule") && args[3].equalsIgnoreCase("castvote")) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[5], "attacker");
			addIfPrefix(completions, args[5], "defender");
			addIfPrefix(completions, args[5], "both");
			return completions;
		}
		return List.of();
	}

	private static boolean isScheduleChoiceSubcommand(String subcommand) {
		return "choice".equalsIgnoreCase(subcommand)
				|| "battlechoice".equalsIgnoreCase(subcommand)
				|| "defenderchoice".equalsIgnoreCase(subcommand)
				|| "pushchoice".equalsIgnoreCase(subcommand)
				|| "holdchoice".equalsIgnoreCase(subcommand);
	}

	private static List<String> activeWarIds(String prefix) {
		List<String> completions = new ArrayList<>();
		String lower = prefix.toLowerCase();
		for (War war : WarManager.getActive()) {
			String id = String.valueOf(war.getId());
			if (id.startsWith(lower)) {
				completions.add(id);
			}
		}
		return completions;
	}

	private static List<String> factionIds(String prefix) {
		List<String> completions = new ArrayList<>();
		String lower = prefix == null ? "" : prefix.toLowerCase();
		for (Faction faction : FactionManager.factions) {
			if (faction == null || faction.getId() == null) {
				continue;
			}
			String id = faction.getId();
			if (id.toLowerCase().startsWith(lower)) {
				completions.add(id);
			}
		}
		return completions;
	}

	private static void addIfPrefix(List<String> completions, String typed, String candidate) {
		if (candidate.toLowerCase().startsWith(typed.toLowerCase())) {
			completions.add(candidate);
		}
	}
}
