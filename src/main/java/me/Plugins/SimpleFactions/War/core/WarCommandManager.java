package me.Plugins.SimpleFactions.War.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.Utils.Permissions;
import me.Plugins.SimpleFactions.War.campaign.admin.CampaignTimeCommandService;
import me.Plugins.SimpleFactions.War.campaign.admin.CampaignTimeResult;
import me.Plugins.SimpleFactions.War.campaign.admin.WarReparationsAdminService;
import me.Plugins.SimpleFactions.War.campaign.admin.WarScheduleAdminResult;
import me.Plugins.SimpleFactions.War.campaign.admin.WarScheduleAdminService;
import me.Plugins.SimpleFactions.War.campaign.admin.WarScheduleFeedbackFormatter;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;

public class WarCommandManager implements CommandExecutor {
	public static final String CMD = "war";

	private static final int FACTION_LIST_LIMIT = 40;

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			return true;
		}
		if (args.length < 1) {
			player.sendMessage("§eUsage: §a/war list");
			if (Permissions.isAdmin(sender)) {
				player.sendMessage("§7Staff: §e/war admin end|win|status|path|time|schedule|devmode|raid|reparations §7...");
			}
			return true;
		}
		return handle(player, args);
	}

	private boolean handle(Player player, String[] args) {
		if (args.length < 1) {
			return true;
		}
		if (args[0].equalsIgnoreCase("list") && args.length == 1) {
			InventoryManager inventory = new InventoryManager();
			inventory.warList(player);
			return true;
		}
		if (args[0].equalsIgnoreCase("admin")) {
			return handleAdmin(player, args);
		}
		player.sendMessage("§cUnknown subcommand. Use §e/war list");
		return true;
	}

	private boolean handleAdmin(Player player, String[] args) {
		if (!Permissions.isAdmin(player)) {
			player.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
			return true;
		}
		if (args.length < 2) {
			player.sendMessage("§cUsage: /war admin end|win|status|path|time|schedule|devmode|raid|reparations|factions ...");
			return true;
		}
		String subcommand = args[1].toLowerCase();
		return switch (subcommand) {
			case "end" -> handleEnd(player, args);
			case "win" -> handleWin(player, args);
			case "status" -> handleStatus(player, args);
			case "path" -> handlePath(player, args);
			case "time" -> handleTime(player, args);
			case "schedule" -> handleSchedule(player, args);
			case "devmode" -> handleDevmode(player, args);
			case "raid" -> handleRaid(player, args);
			case "reparations" -> handleReparations(player, args);
			case "factions" -> handleFactions(player, args);
			default -> {
				player.sendMessage("§cUnknown admin subcommand. Use: end, win, status, path, time, schedule, devmode, raid, reparations, factions");
				yield true;
			}
		};
	}

	private boolean handleEnd(Player player, String[] args) {
		if (args.length != 3) {
			player.sendMessage("§cUsage: /war admin end <warId>");
			return true;
		}
		var warId = WarCommandHelper.parseWarId(args[2]);
		if (warId.isEmpty()) {
			player.sendMessage("§cWar id must be a number");
			return true;
		}
		War w = WarManager.getById(warId.get());
		if (w == null) {
			player.sendMessage("§cNo war by that id");
			return true;
		}
		WarManager.endWar(w);
		player.sendMessage("§aEnded war " + w.getName());
		return true;
	}

	private boolean handleWin(Player player, String[] args) {
		if (args.length != 4) {
			player.sendMessage("§cUsage: /war admin win <warId> attacker|defender");
			return true;
		}
		var warId = WarCommandHelper.parseWarId(args[2]);
		if (warId.isEmpty()) {
			player.sendMessage("§cWar id must be a number");
			return true;
		}
		BelligerentRole winner = WarCommandHelper.parseBelligerentRoleArg(args[3]);
		if (winner == null) {
			player.sendMessage("§cWinner must be attacker or defender.");
			return true;
		}
		War w = WarManager.getById(warId.get());
		if (w == null) {
			player.sendMessage("§cNo war by that id");
			return true;
		}
		if (!w.isActive()) {
			player.sendMessage("§cWar is not active");
			return true;
		}
		WarEndReason reason = winner == BelligerentRole.ATTACKER
				? WarEndReason.ATTACKER_VICTORY
				: WarEndReason.DEFENDER_VICTORY;
		WarManager.endWar(w, reason);
		player.sendMessage("§aEnded war " + w.getId() + " (" + winner.name().toLowerCase() + " victory).");
		return true;
	}

	/**
	 * Faction id lookup for staff minting war declare codes in Discord. Nothing links a
	 * Discord user to a faction, so the ids have to be read here and pasted there.
	 */
	private boolean handleFactions(Player player, String[] args) {
		if (args.length > 3) {
			player.sendMessage("§cUsage: /war admin factions [filter]");
			return true;
		}
		String filter = args.length == 3 ? args[2].toLowerCase() : "";
		List<Faction> matches = new ArrayList<>();
		for (Faction faction : FactionManager.factions) {
			if (faction == null || faction.getId() == null) {
				continue;
			}
			String plainName = faction.getName() == null
					? ""
					: Formatter.formatId(faction.getName());
			if (filter.isEmpty()
					|| faction.getId().toLowerCase().contains(filter)
					|| plainName.toLowerCase().contains(filter)) {
				matches.add(faction);
			}
		}
		if (matches.isEmpty()) {
			player.sendMessage("§cNo faction matches §f" + (filter.isEmpty() ? "-" : filter));
			return true;
		}
		player.sendMessage("§7Factions (" + matches.size() + "):");
		int shown = 0;
		for (Faction faction : matches) {
			if (shown >= FACTION_LIST_LIMIT) {
				// There is no chat pager anywhere in the plugin, so the honest answer is to
				// stop and tell staff to narrow the filter.
				player.sendMessage("§7... " + (matches.size() - shown)
						+ " more. Narrow the filter: §e/war admin factions <filter>");
				break;
			}
			player.sendMessage("§f" + faction.getId() + " §7- " + faction.getName());
			shown++;
		}
		return true;
	}

	private boolean handleStatus(Player player, String[] args) {
		if (args.length != 3) {
			player.sendMessage("§cUsage: /war admin status <warId>");
			return true;
		}
		var warId = WarCommandHelper.parseWarId(args[2]);
		if (warId.isEmpty()) {
			player.sendMessage("§cWar id must be a number");
			return true;
		}
		War w = WarManager.getById(warId.get());
		if (w == null) {
			player.sendMessage("§cNo war by that id");
			return true;
		}
		for (String line : WarDebugFormatter.formatStatusLines(w)) {
			player.sendMessage(line);
		}
		return true;
	}

	private boolean handlePath(Player player, String[] args) {
		if (args.length != 3) {
			player.sendMessage("§cUsage: /war admin path <warId>");
			return true;
		}
		var warId = WarCommandHelper.parseWarId(args[2]);
		if (warId.isEmpty()) {
			player.sendMessage("§cWar id must be a number");
			return true;
		}
		War w = WarManager.getById(warId.get());
		if (w == null) {
			player.sendMessage("§cNo war by that id");
			return true;
		}
		if (!w.isActive()) {
			player.sendMessage("§cWar is not active");
			return true;
		}
		if (!WarManager.regenerateCampaign(w)) {
			player.sendMessage("§cCould not regenerate campaign route");
			return true;
		}
		List<Integer> axis = w.getCampaignProvinces();
		Integer cursorProvince = null;
		if (axis != null && w.getCursorIndex() >= 0 && w.getCursorIndex() < axis.size()) {
			cursorProvince = axis.get(w.getCursorIndex());
		}
		String phase = w.getCampaignPhase() != null ? w.getCampaignPhase().toJson() : "invasion";
		player.sendMessage("§aRegenerated campaign for war " + w.getId()
				+ ": objective " + w.getObjectiveProvinceId()
				+ ", start " + w.getCampaignStartProvinceId()
				+ ", path length " + (axis == null ? 0 : axis.size())
				+ ", cursor " + w.getCursorIndex()
				+ (cursorProvince != null ? " (province " + cursorProvince + ")" : "")
				+ ", phase " + phase
				+ ", initiative " + w.getInitiativeAttacker() + "/" + w.getInitiativeDefender()
				+ ". Progression and occupation reset.");
		for (String line : WarDebugFormatter.formatStatusLines(w)) {
			player.sendMessage(line);
		}
		return true;
	}

	private boolean handleTime(Player player, String[] args) {
		if (args.length < 3) {
			player.sendMessage("§cUsage: /war admin time status|reset|add|skip-to-battle-day ...");
			return true;
		}
		String subcommand = args[2].toLowerCase();
		switch (subcommand) {
			case "status" -> {
				for (String line : CampaignTimeCommandService.statusLines()) {
					player.sendMessage(line);
				}
			}
			case "reset" -> {
				CampaignTimeResult result = CampaignTimeCommandService.reset();
				player.sendMessage(result.success() ? result.message() : "§c" + result.message());
			}
			case "add" -> {
				if (args.length < 4) {
					player.sendMessage("§cUsage: /war admin time add <duration...>");
					return true;
				}
				CampaignTimeResult result = CampaignTimeCommandService.add(
						Arrays.copyOfRange(args, 3, args.length));
				player.sendMessage(result.success() ? result.message() : result.message());
			}
			case "skip-to-battle-day" -> {
				if (args.length < 4) {
					player.sendMessage("§cUsage: /war admin time skip-to-battle-day <warId>");
					return true;
				}
				var warId = WarCommandHelper.parseWarId(args[3]);
				if (warId.isEmpty()) {
					player.sendMessage("§cWar id must be a number");
					return true;
				}
				War war = WarManager.getById(warId.get());
				CampaignTimeResult result = CampaignTimeCommandService.skipToBattleDay(war);
				player.sendMessage(result.success() ? result.message() : result.message());
			}
			default -> player.sendMessage(
					"§cUnknown subcommand. Use: status, reset, add, skip-to-battle-day");
		}
		return true;
	}

	private boolean handleDevmode(Player player, String[] args) {
		if (args.length != 3) {
			player.sendMessage("§cUsage: /war admin devmode on|off|status");
			return true;
		}
		switch (args[2].toLowerCase()) {
			case "on" -> {
				int filled = WarDevMode.setEnabled(true);
				if (filled > 0) {
					player.sendMessage("§aWar devmode enabled. Filled " + filled + " campaign side warbands.");
				} else {
					player.sendMessage("§aWar devmode enabled.");
				}
			}
			case "off" -> {
				int cleared = WarDevMode.setEnabled(false);
				if (cleared > 0) {
					player.sendMessage("§aWar devmode disabled. Cleared dummies from " + cleared + " warbands.");
				} else {
					player.sendMessage("§aWar devmode disabled.");
				}
			}
			case "status" -> player.sendMessage("§aWar devmode: "
					+ (WarDevMode.isEnabled() ? "§aenabled" : "§cdisabled")
					+ "§a, roster fill: §e" + Cache.warDevmodePhantomCount);
			default -> player.sendMessage("§cUsage: /war admin devmode on|off|status");
		}
		return true;
	}

	private boolean handleRaid(Player player, String[] args) {
		if (args.length < 4 || !args[2].equalsIgnoreCase("resetquota")) {
			player.sendMessage("§cUsage: /war admin raid resetquota <warId> [aggressor|defender|both]");
			return true;
		}
		var warId = WarCommandHelper.parseWarId(args[3]);
		if (warId.isEmpty()) {
			player.sendMessage("§cWar id must be a number");
			return true;
		}
		War war = WarManager.getById(warId.get());
		if (war == null) {
			player.sendMessage("§cNo war by that id");
			return true;
		}
		String sideArg = args.length >= 5 ? args[4] : "both";
		if (!WarCommandHelper.isValidCoalitionScope(sideArg)) {
			player.sendMessage("§cSide must be aggressor, defender, or both");
			return true;
		}
		CampaignCoalition coalition = WarCommandHelper.parseCoalitionScope(sideArg);
		int cleared = CampaignRaidService.resetRaidQuota(war, coalition);
		String scope = coalition == null
				? "both sides"
				: (coalition == CampaignCoalition.AGGRESSOR ? "aggressor" : "defender");
		player.sendMessage("§aReset raid quota for war " + war.getId()
				+ " (" + scope + "): cleared " + cleared + " entr" + (cleared == 1 ? "y" : "ies") + ".");
		return true;
	}

	private boolean handleReparations(Player player, String[] args) {
		if (args.length < 4 || args.length > 6) {
			player.sendMessage(WarReparationsAdminService.USAGE);
			return true;
		}
		String percentArg = args.length >= 5 ? args[4] : null;
		String daysArg = args.length >= 6 ? args[5] : null;
		WarReparationsAdminService.ApplyResult result =
				WarReparationsAdminService.apply(args[2], args[3], percentArg, daysArg);
		player.sendMessage(result.message());
		return true;
	}

	private boolean handleSchedule(Player player, String[] args) {
		if (args.length < 4) {
			player.sendMessage("§cUsage: /war admin schedule <warId> <subcommand> ...");
			return true;
		}
		var warId = WarCommandHelper.parseWarId(args[2]);
		if (warId.isEmpty()) {
			player.sendMessage("§cWar id must be a number");
			return true;
		}
		War w = WarManager.getById(warId.get());
		if (w == null) {
			player.sendMessage("§cNo war by that id");
			return true;
		}
		String subcommand = args[3].toLowerCase();
		WarScheduleAdminResult result = switch (subcommand) {
			case "opencvote" -> WarScheduleAdminService.openVote(w);
			case "closevote" -> WarScheduleAdminService.closeVote(w, CampaignClock.now());
			case "skipday" -> WarScheduleAdminService.skipDay(w);
			case "castvote" -> {
				if (args.length < 5) {
					yield WarScheduleAdminResult.error(
							"Usage: /war admin schedule <id> castvote <hour> [attacker|defender|both]");
				}
				int hour;
				try {
					hour = Integer.parseInt(args[4]);
				} catch (NumberFormatException e) {
					yield WarScheduleAdminResult.error("Hour must be a number.");
				}
				String side = args.length >= 6 ? args[5] : "both";
				yield WarScheduleAdminService.castVote(w, hour, side);
			}
			case "forcequorum" -> WarScheduleAdminService.forceQuorum(w);
			case "setscheduled" -> {
				if (args.length < 5) {
					yield WarScheduleAdminResult.error(
							"Usage: /war admin schedule <id> setscheduled <iso-instant>");
				}
				yield WarScheduleAdminService.setScheduled(w, args[4]);
			}
			case "battlecreate" -> WarScheduleAdminService.battleCreate(w);
			case "battledelete" -> WarScheduleAdminService.battleDelete(w);
			case "battlestart" -> WarScheduleAdminService.battleStart(w);
			case "winbattle" -> {
				if (args.length < 5) {
					yield WarScheduleAdminResult.error(
							"Usage: /war admin schedule <id> winbattle attacker|defender");
				}
				BelligerentRole winner = WarCommandHelper.parseBelligerentRoleArg(args[4]);
				if (winner == null) {
					yield WarScheduleAdminResult.error("Winner must be attacker or defender.");
				}
				yield WarScheduleAdminService.winBattle(w, winner);
			}
			case "choice", "battlechoice", "defenderchoice", "pushchoice", "holdchoice" -> {
				if (args.length < 5) {
					yield WarScheduleAdminResult.error(
							"Usage: /war admin schedule <id> choice push|hold|attack|accept");
				}
				yield WarScheduleAdminService.battleChoice(w, args[4]);
			}
			default -> WarScheduleAdminResult.error(
					"Unknown subcommand. Use: opencvote, closevote, skipday, castvote, forcequorum, "
							+ "setscheduled, battlecreate, battledelete, battlestart, winbattle, choice");
		};
		if (result.success()) {
			WarManager.persist(w);
			player.sendMessage("§a" + result.message());
			Integer castHour = null;
			if ("castvote".equals(subcommand) && args.length >= 5) {
				try {
					castHour = Integer.parseInt(args[4]);
				} catch (NumberFormatException ignored) {
					// castvote branch already validated hour
				}
			}
			for (String line : WarScheduleFeedbackFormatter.format(subcommand, w, castHour)) {
				player.sendMessage(line);
			}
		} else {
			player.sendMessage("§c" + result.message());
		}
		for (String line : WarScheduleAdminService.devModeReminderLines()) {
			player.sendMessage(line);
		}
		return true;
	}
}
