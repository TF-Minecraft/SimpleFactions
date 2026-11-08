package net.tfminecraft.simplefactions.war.battle.ui;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.managers.StaffJoins;
import net.tfminecraft.simplefactions.utils.StaffConfirm;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleStaffService;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;

/**
 * Staff battle tools: watch, info, restart, abort, end, hold and lives on {@code /battle}, and
 * forcekick and toggleopen on {@code /warband}. Callers have already checked staff access.
 */
public final class BattleStaffCommands {
	static final String RESTART_RISK = "puts every soldier back at their spawn and resets lives, casualties,"
			+ " capture points and the battle timer. Dropped items and broken vehicles stay.";
	static final String ABORT_RISK = "stops the battle with no winner and no casualties. It stays on hold"
			+ " until staff start it again.";
	static final String END_RISK = "ends the battle now. In a campaign battle the casualties so far count;"
			+ " a named winner wins the battle, and with no winner voting reopens.";

	private BattleStaffCommands() {}

	/** Handles a {@code /battle} staff subcommand; false when {@code args} is not one of them. */
	public static boolean battle(Player player, String[] rawArgs) {
		String sub = rawArgs[0].toLowerCase(Locale.ROOT);
		String risk = switch (sub) {
			case "restart" -> RESTART_RISK;
			case "abort" -> ABORT_RISK;
			case "end" -> END_RISK;
			case "watch", "info", "hold", "lives" -> null;
			default -> "";
		};
		if ("".equals(risk)) {
			return false;
		}
		if (sub.equals("watch") && rawArgs.length == 2 && rawArgs[1].equalsIgnoreCase("off")) {
			player.sendMessage(BattleWatchService.stop(player));
			return true;
		}
		String[] args = StaffConfirm.strip(rawArgs);
		Battle battle = args.length >= 2 ? BattleManager.getByString(args[1]) : null;
		if (battle == null) {
			player.sendMessage(args.length < 2 ? usage(sub) : "§cThere is no battle with the id " + args[1]);
			return true;
		}
		if (StaffConfirm.gate(player, "battle", rawArgs, risk) == null) {
			return true;
		}
		String by = player.getName();
		switch (sub) {
			case "watch" -> player.sendMessage(BattleWatchService.toggle(player, battle));
			case "info" -> BattleInfoFormatter.lines(battle, Instant.now()).forEach(player::sendMessage);
			case "restart" -> player.sendMessage(BattleStaffService.restart(battle, by).message());
			case "abort" -> player.sendMessage(BattleStaffService.abort(battle, by).message());
			case "end" -> player.sendMessage(end(battle, args, by));
			case "hold" -> player.sendMessage(hold(battle, args, by));
			default -> player.sendMessage(lives(battle, args, by));
		}
		return true;
	}

	private static String end(Battle battle, String[] args, String by) {
		String winner = args.length >= 3 && !args[2].equalsIgnoreCase("nowinner") ? args[2] : null;
		return result(BattleStaffService.end(battle, winner, by));
	}

	private static String hold(Battle battle, String[] args, String by) {
		if (args.length != 3 || !(args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("off"))) {
			return usage("hold");
		}
		return result(BattleStaffService.hold(battle, args[2].equalsIgnoreCase("on"), by));
	}

	private static String lives(Battle battle, String[] args, String by) {
		if (args.length != 5) {
			return usage("lives");
		}
		int amount;
		try {
			amount = Integer.parseInt(args[4]);
		} catch (NumberFormatException invalid) {
			return "§cThe amount must be a whole number.";
		}
		return result(BattleStaffService.adjustLives(battle, args[2], args[3], amount, by));
	}

	private static String result(BattleStaffService.Result result) {
		return result.ok() ? result.message() : "§c" + result.message();
	}

	static String usage(String sub) {
		return "§cUsage: /battle " + switch (sub) {
			case "end" -> "end <battleId> [nowinner|<sideId>]";
			case "hold" -> "hold <battleId> on|off";
			case "lives" -> "lives <battleId> <sideId> add|remove|set <amount>";
			case "watch" -> "watch <battleId>|off";
			default -> sub + " <battleId>";
		};
	}

	/** Handles {@code /warband forcekick} and staff {@code /warband toggleopen <id>}; false otherwise. */
	public static boolean warband(Player player, String[] args) {
		boolean forcekick = args[0].equalsIgnoreCase("forcekick") && args.length >= 3;
		boolean toggle = args[0].equalsIgnoreCase("toggleopen") && args.length == 2;
		if (!forcekick && !toggle) {
			return false;
		}
		if (!BattlePermissions.isAdmin(player)) {
			player.sendMessage("§a[Battle]§4You do not have access to this command");
			return true;
		}
		Warband warband = WarbandManager.getByString(args[1]);
		if (warband == null) {
			player.sendMessage("§cWarband not found");
			return true;
		}
		if (forcekick) {
			player.sendMessage(StaffJoins.warbandKick(warband, String.join(" ", Arrays.copyOfRange(args, 2, args.length))));
			return true;
		}
		warband.setLocked(!warband.isLocked());
		BattlePersistenceService.persistWarband(warband);
		player.sendMessage(warband.isLocked() ? "§cWarband " + warband.getId() + " §cis now invite only"
				: "§aWarband " + warband.getId() + " §ais now open");
		return true;
	}
}
