package net.tfminecraft.simplefactions.government.movement.admin;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.government.movement.Movement;

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
		String action = args[1].toLowerCase(java.util.Locale.ROOT);
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
			return names(args, 4, MovementAdminService.wantedLeaderNames(movement));
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
		String slot = args[3].toLowerCase(java.util.Locale.ROOT);
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
			return targets(movement, args, 5);
		}
		if (slot.equals("cause")) {
			if (args.length == 5) {
				return prefixList(args[4], MovementAdminService.causeIndices(movement));
			}
			if (args.length == 6) {
				return prefix(args[5], "citizen", "guild", "vassal");
			}
			return targets(movement, args, 6);
		}
		return List.of();
	}

	/** Completes the target at {@code from}; citizen names may span several words. */
	private static List<String> targets(Movement movement, String[] args, int from) {
		String memberType = args[from - 1].toLowerCase(java.util.Locale.ROOT);
		if (memberType.equals("citizen")) {
			return names(args, from, MovementAdminService.hostCitizenNames(movement));
		}
		if (args.length != from + 1) {
			return List.of();
		}
		return switch (memberType) {
			case "guild" -> prefixList(args[from], MovementAdminService.hostGuildIds(movement));
			case "vassal" -> prefixList(args[from], MovementAdminService.hostSubjectIds(movement));
			default -> List.of();
		};
	}

	/** Completes a character name that starts at {@code from} and may contain spaces. */
	private static List<String> names(String[] args, int from, List<String> names) {
		String[] tail = new String[args.length - from + 1];
		tail[0] = "";
		System.arraycopy(args, from, tail, 1, args.length - from);
		return Members.completions(names, tail);
	}

	private static List<String> prefix(String typed, String... candidates) {
		List<String> completions = new ArrayList<>();
		String lower = typed == null ? "" : typed.toLowerCase(java.util.Locale.ROOT);
		for (String candidate : candidates) {
			if (candidate.toLowerCase(java.util.Locale.ROOT).startsWith(lower)) {
				completions.add(candidate);
			}
		}
		return completions;
	}

	private static List<String> prefixList(String typed, List<String> candidates) {
		List<String> completions = new ArrayList<>();
		String lower = typed == null ? "" : typed.toLowerCase(java.util.Locale.ROOT);
		for (String candidate : candidates) {
			if (candidate != null && candidate.toLowerCase(java.util.Locale.ROOT).startsWith(lower)) {
				completions.add(candidate);
			}
		}
		return completions;
	}
}
