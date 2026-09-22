package me.Plugins.SimpleFactions.government.movement.admin;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Utils.Permissions;
import me.Plugins.SimpleFactions.government.movement.Movement;

public class MovementTabCompletion implements TabCompleter {

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		if (!command.getName().equalsIgnoreCase(MovementCommandManager.CMD)) {
			return List.of();
		}
		if (!Permissions.isAdmin(sender)) {
			return List.of();
		}
		if (args.length == 1) {
			return prefix(args[0], "admin");
		}
		if (!args[0].equalsIgnoreCase("admin")) {
			return List.of();
		}
		if (args.length == 2) {
			return prefix(args[1], "list", "join", "leave", "demands", "target");
		}
		String action = args[1].toLowerCase();
		if (action.equals("target")) {
			if (args.length == 3) {
				return prefixList(args[2], MovementAdminService.allMovementIds());
			}
			Movement movement = FactionManager.getMovementById(args[2]);
			if (movement == null) {
				return List.of();
			}
			if (args.length == 4) {
				return prefixList(args[3], MovementAdminService.causeIndices(movement));
			}
			if (args.length == 5) {
				return prefixList(args[4], MovementAdminService.wantedLeaderNames(movement));
			}
			return List.of();
		}
		if (action.equals("demands")) {
			if (args.length == 3) {
				return prefixList(args[2], MovementAdminService.allMovementIds());
			}
			if (args.length == 4) {
				return prefix(args[3], "accept", "reject");
			}
			return List.of();
		}
		if (!action.equals("join") && !action.equals("leave")) {
			if (args.length == 3) {
				return prefix(args[2], "demands");
			}
			if (args.length == 4 && args[2].equalsIgnoreCase("demands")) {
				return prefix(args[3], "accept", "reject");
			}
			return List.of();
		}
		if (args.length == 3) {
			return prefixList(args[2], MovementAdminService.allMovementIds());
		}
		Movement movement = FactionManager.getMovementById(args[2]);
		if (args.length == 4) {
			return prefix(args[3], "supporter", "cause", "backer");
		}
		if (movement == null) {
			return List.of();
		}
		String slot = args[3].toLowerCase();
		if (slot.equals("backer")) {
			if (args.length == 5) {
				return prefixList(args[4], MovementAdminService.otherFactionIds(movement));
			}
			return List.of();
		}
		if (slot.equals("supporter")) {
			if (args.length == 5) {
				return prefix(args[4], "citizen", "guild", "vassal");
			}
			if (args.length == 6) {
				return prefixList(args[5], targets(movement, args[4]));
			}
			return List.of();
		}
		if (slot.equals("cause")) {
			if (args.length == 5) {
				return prefixList(args[4], MovementAdminService.causeIndices(movement));
			}
			if (args.length == 6) {
				return prefix(args[5], "citizen", "guild", "vassal");
			}
			if (args.length == 7) {
				return prefixList(args[6], targets(movement, args[5]));
			}
		}
		return List.of();
	}

	private static List<String> targets(Movement movement, String memberType) {
		if (memberType == null) {
			return List.of();
		}
		return switch (memberType.toLowerCase()) {
			case "citizen" -> MovementAdminService.hostCitizenNames(movement);
			case "guild" -> MovementAdminService.hostGuildIds(movement);
			case "vassal" -> MovementAdminService.hostSubjectIds(movement);
			default -> List.of();
		};
	}

	private static List<String> prefix(String typed, String... candidates) {
		List<String> completions = new ArrayList<>();
		String lower = typed == null ? "" : typed.toLowerCase();
		for (String candidate : candidates) {
			if (candidate.toLowerCase().startsWith(lower)) {
				completions.add(candidate);
			}
		}
		return completions;
	}

	private static List<String> prefixList(String typed, List<String> candidates) {
		List<String> completions = new ArrayList<>();
		String lower = typed == null ? "" : typed.toLowerCase();
		for (String candidate : candidates) {
			if (candidate != null && candidate.toLowerCase().startsWith(lower)) {
				completions.add(candidate);
			}
		}
		return completions;
	}
}
