package net.tfminecraft.simplefactions.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import net.tfminecraft.simplefactions.diplomacy.coalition.Coalition;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionRegistry;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionService;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Permissions;

public class CoalitionTabCompletion implements TabCompleter {

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		boolean admin = Permissions.isAdmin(sender);
		if (args.length == 1) {
			List<String> options = new ArrayList<>(CoalitionCommandManager.SUBCOMMANDS);
			if (admin) options.add("admin");
			return prefix(args[0], options);
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		if (sub.equals("admin")) {
			if (!admin) return List.of();
			if (args.length == 2) return prefix(args[1], CoalitionCommandManager.ADMIN_SUBCOMMANDS);
			String action = args[1].toLowerCase(Locale.ROOT);
			if (action.equals("cost")) return prefix(args[args.length - 1], factionIds());
			if (action.equals("offensive") && args.length == 3) return prefix(args[2], List.of("on", "off"));
			if ((action.equals("add") || action.equals("kick") || action.equals("setleader")) && args.length == 3) {
				return prefix(args[2], factionIds());
			}
			return args.length <= 4 ? prefix(args[args.length - 1], coalitionNames()) : List.of();
		}
		if (args.length != 2) return List.of();
		return switch (sub) {
			case "create", "invite", "kick", "leader", "target" -> prefix(args[1], factionIds());
			case "join" -> prefix(args[1], coalitionNames());
			default -> List.of();
		};
	}

	private static List<String> factionIds() {
		List<String> ids = new ArrayList<>();
		for (Faction faction : FactionManager.factions) ids.add(faction.getId());
		return ids;
	}

	private static List<String> coalitionNames() {
		List<String> names = new ArrayList<>();
		for (Coalition coalition : CoalitionRegistry.all()) names.add(CoalitionService.plainName(coalition.getName()));
		return names;
	}

	private static List<String> prefix(String typed, List<String> options) {
		String lower = typed.toLowerCase(Locale.ROOT);
		List<String> result = new ArrayList<>();
		for (String option : options) {
			if (option.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(option);
		}
		return result;
	}
}
