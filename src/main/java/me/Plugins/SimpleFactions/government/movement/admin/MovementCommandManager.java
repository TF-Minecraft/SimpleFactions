package me.Plugins.SimpleFactions.government.movement.admin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Utils.Permissions;

public class MovementCommandManager implements CommandExecutor {
	public static final String CMD = "movement";

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			return true;
		}
		if (!Permissions.isAdmin(player)) {
			player.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
			return true;
		}
		if (args.length < 1 || !args[0].equalsIgnoreCase("admin")) {
			player.sendMessage(MovementAdminService.USAGE);
			return true;
		}
		if (args.length < 2) {
			player.sendMessage(MovementAdminService.USAGE);
			return true;
		}
		String sub = args[1].toLowerCase();
		return switch (sub) {
			case "list" -> {
				for (String line : MovementAdminService.listLines()) {
					player.sendMessage(line);
				}
				yield true;
			}
			case "join" -> handleMutate(player, true, args);
			case "leave" -> handleMutate(player, false, args);
			case "demands" -> handleDemands(player, args, 2);
			case "target" -> handleTarget(player, args);
			default -> {
				if (args.length >= 4 && args[2].equalsIgnoreCase("demands")) {
					yield handleDemands(player, args, 1);
				}
				player.sendMessage(MovementAdminService.USAGE);
				yield true;
			}
		};
	}

	private boolean handleDemands(Player player, String[] args, int idIndex) {
		if (args.length < 4) {
			player.sendMessage(MovementAdminService.DEMANDS_USAGE);
			return true;
		}
		String movementId = args[idIndex];
		String outcome = args[3];
		MovementAdminService.Result result = MovementAdminService.demands(movementId, outcome);
		player.sendMessage(result.message());
		return true;
	}

	private boolean handleTarget(Player player, String[] args) {
		if (args.length < 5) {
			player.sendMessage(MovementAdminService.TARGET_USAGE);
			return true;
		}
		MovementAdminService.Result result = MovementAdminService.target(args[2], args[3], args[4]);
		player.sendMessage(result.message());
		return true;
	}

	private boolean handleMutate(Player player, boolean joining, String[] args) {
		String usage = joining ? MovementAdminService.JOIN_USAGE : MovementAdminService.LEAVE_USAGE;
		if (args.length < 4) {
			player.sendMessage(usage);
			return true;
		}
		String movementId = args[2];
		String slot = args[3].toLowerCase();
		MovementAdminService.Result result;
		if (slot.equals("backer")) {
			if (args.length < 5) {
				player.sendMessage(usage);
				return true;
			}
			result = joining
					? MovementAdminService.joinBacker(movementId, args[4])
					: MovementAdminService.leaveBacker(movementId, args[4]);
		} else if (slot.equals("supporter")) {
			if (args.length < 6) {
				player.sendMessage(usage);
				return true;
			}
			result = joining
					? MovementAdminService.joinSupporter(movementId, args[4], args[5])
					: MovementAdminService.leaveSupporter(movementId, args[4], args[5]);
		} else if (slot.equals("cause")) {
			if (args.length < 7) {
				player.sendMessage(usage);
				return true;
			}
			result = joining
					? MovementAdminService.joinCause(movementId, args[4], args[5], args[6])
					: MovementAdminService.leaveCause(movementId, args[4], args[5], args[6]);
		} else {
			player.sendMessage(usage);
			return true;
		}
		player.sendMessage(result.message());
		return true;
	}
}
