package net.tfminecraft.simplefactions.war.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiTimeFormatter;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleQuorumService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.campaign.buildup.BuildupService;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.utils.StaffConfirm;
import net.tfminecraft.simplefactions.war.campaign.admin.CampaignTimeCommandService;
import net.tfminecraft.simplefactions.war.campaign.admin.CampaignTimeResult;
import net.tfminecraft.simplefactions.war.campaign.admin.WarReparationsAdminService;
import net.tfminecraft.simplefactions.war.campaign.admin.WarScheduleAdminResult;
import net.tfminecraft.simplefactions.war.campaign.admin.WarScheduleAdminService;
import net.tfminecraft.simplefactions.war.campaign.admin.WarScheduleFeedbackFormatter;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.truce.TruceService;
import net.tfminecraft.simplefactions.war.history.WarHistoryAdmin;

public class WarCommandManager implements CommandExecutor {
	public static final String CMD = "war";

	private static final int FACTION_LIST_LIMIT = 40;

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			return true;
		}
		if (args.length < 1) {
			player.sendMessage("§eUsage: §a/war list§e, §a/war calls§e, §a/war call accept|decline <war id>");
			if (Permissions.isAdmin(sender)) {
				player.sendMessage("§7Staff: §e/war admin declare|end|win|status|path|time|schedule|buildup|devmode|raid|reparations|truce|history|factions|forceinstallation §7...");
			}
			return true;
		}
		return handle(player, args);
	}

	private boolean handle(Player player, String[] args) {
		if (args[0].equalsIgnoreCase("list") && args.length == 1) {
			InventoryManager inventory = new InventoryManager();
			inventory.warList(player);
			return true;
		}
		if (args[0].equalsIgnoreCase("admin")) {
			return handleAdmin(player, args);
		}
		if (args[0].equalsIgnoreCase("calls") && args.length == 1) {
			CallToArmsService.list(player);
			return true;
		}
		if (args[0].equalsIgnoreCase("call")) {
			return handleCall(player, args);
		}
		player.sendMessage("§cUnknown subcommand. Use §e/war list§c or §e/war calls");
		return true;
	}

	private boolean handleCall(Player player, String[] args) {
		boolean accept = args.length == 3 && args[1].equalsIgnoreCase("accept");
		boolean decline = args.length == 3 && args[1].equalsIgnoreCase("decline");
		if (!accept && !decline) {
			player.sendMessage("§eUsage: §a/war call accept|decline <war id>");
			return true;
		}
		War war = WarCommandHelper.parseWarId(args[2]).map(WarManager::getById).orElse(null);
		if (war == null || !war.isActive()) {
			player.sendMessage("§cNo active war with id §f" + args[2]);
			return true;
		}
		if (accept) {
			CallToArmsService.accept(player, war);
		} else {
			CallToArmsService.decline(player, war);
		}
		return true;
	}

	private boolean handleAdmin(Player player, String[] rawArgs) {
		if (!Permissions.isAdmin(player)) {
			player.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
			return true;
		}
		String[] args = StaffConfirm.gate(player, CMD, rawArgs, WarAdminRisks.describe(StaffConfirm.strip(rawArgs)));
		if (args == null) {
			return true;
		}
		if (args.length < 2) {
			player.sendMessage("§cUsage: /war admin declare|end|win|status|path|time|schedule|buildup|devmode|raid|reparations|truce|history|factions|forceinstallation ...");
			return true;
		}
		String subcommand = args[1].toLowerCase(java.util.Locale.ROOT);
		return switch (subcommand) {
			case "declare" -> handleDeclare(player, args);
			case "end" -> handleEnd(player, args);
			case "win" -> handleWin(player, args);
			case "status" -> handleStatus(player, args);
			case "path" -> handlePath(player, args);
			case "time" -> handleTime(player, args);
			case "schedule" -> handleSchedule(player, args);
			case "buildup" -> handleBuildup(player, args);
			case "devmode" -> handleDevmode(player, args);
			case "raid" -> handleRaid(player, args);
			case "reparations" -> handleReparations(player, args);
			case "truce" -> handleTruce(player, args);
			case "history" -> {
				WarHistoryAdmin.handle(player, args);
				yield true;
			}
			case "factions" -> handleFactions(player, args);
			case "forceinstallation" -> {
				player.sendMessage(WarForceInstallation.run(args, player.getName()));
				yield true;
			}
			default -> {
				player.sendMessage("§cUnknown admin subcommand. Use: declare, end, win, status, path, time, schedule, devmode, raid, reparations, truce, history, factions, forceinstallation");
				yield true;
			}
		};
	}

	private static final String DECLARE_USAGE =
			"§cUsage: /war admin declare <attacker> <defender> [goal] [goal args] §7— goals needing args: "
					+ "de_jure_annex <titleId>, subjugate <relationType>, transfer_subject <subjectFaction>, "
					+ "pillage <settlementId>, change_government <governmentLaw> <leadershipLaw>";

	/** Declares a war between two factions as staff, through the same validation as the declare GUI. */
	private boolean handleDeclare(Player player, String[] args) {
		if (args.length < 4) {
			player.sendMessage(DECLARE_USAGE);
			return true;
		}
		Faction attacker = FactionManager.getByString(args[2]);
		Faction defender = FactionManager.getByString(args[3]);
		if (attacker == null || defender == null) {
			player.sendMessage("§cUnknown faction: " + (attacker == null ? args[2] : args[3]));
			return true;
		}
		WarGoalType goal = args.length >= 5 ? WarGoalType.fromJson(args[4]) : WarGoalType.WAR;
		if (goal == null) {
			player.sendMessage("§cUnknown war goal: " + args[4]);
			player.sendMessage(DECLARE_USAGE);
			return true;
		}
		if (goal.isMovementOrigin()) {
			player.sendMessage("§c" + goal.getDisplayName() + " wars start from movements, not from a declaration.");
			return true;
		}
		int maxArgs = switch (goal) {
			case DE_JURE_ANNEX, SUBJUGATE, TRANSFER_SUBJECT, PILLAGE -> 6;
			case CHANGE_GOVERNMENT -> 7;
			default -> 5;
		};
		if (args.length > maxArgs) {
			player.sendMessage(DECLARE_USAGE);
			return true;
		}
		String arg1 = args.length >= 6 ? args[5] : null;
		String arg2 = args.length >= 7 ? args[6] : null;
		String titleId = goal == WarGoalType.DE_JURE_ANNEX ? arg1 : null;
		String relationTypeId = goal == WarGoalType.SUBJUGATE ? arg1 : null;
		String settlementId = goal == WarGoalType.PILLAGE ? arg1 : null;
		String governmentLawId = goal == WarGoalType.CHANGE_GOVERNMENT ? arg1 : null;
		String leadershipLawId = goal == WarGoalType.CHANGE_GOVERNMENT ? arg2 : null;
		String subjectFactionId = null;
		if (goal == WarGoalType.TRANSFER_SUBJECT && arg1 != null) {
			Faction subject = FactionManager.getByString(arg1);
			if (subject == null) {
				player.sendMessage("§cUnknown subject faction: " + arg1);
				return true;
			}
			subjectFactionId = subject.getId();
		}
		War war = WarManager.declareWar(
				attacker,
				defender,
				goal,
				titleId,
				subjectFactionId,
				relationTypeId,
				governmentLawId,
				leadershipLawId,
				settlementId);
		if (war == null) {
			String error = WarManager.getLastDeclareError();
			player.sendMessage(error != null ? error : "§cCould not declare war.");
			return true;
		}
		LogManager.war(
				"ADMIN_DECLARE warId=%d by=%s attacker=%s defender=%s goal=%s",
				war.getId(),
				player.getName(),
				attacker.getId(),
				defender.getId(),
				goal.toJson());
		player.sendMessage("§aDeclared war §e#" + war.getId() + "§a: " + attacker.getName() + " §avs "
				+ defender.getName() + " §a(" + goal.getDisplayName() + ")."
				+ (BuildupService.isDayVoteOpen(war)
						? " In its buildup; the day vote closes §e"
								+ CampaignUiTimeFormatter.formatDateTime(war.getDayVoteClosesAt())
						: " Battle day: §e" + war.getBattleDay()));
		return true;
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
		player.sendMessage("§aEnded war " + w.getId() + " (" + winner.name().toLowerCase(java.util.Locale.ROOT) + " victory).");
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
		String filter = args.length == 3 ? args[2].toLowerCase(java.util.Locale.ROOT) : "";
		List<Faction> matches = new ArrayList<>();
		for (Faction faction : FactionManager.factions) {
			if (faction == null || faction.getId() == null) {
				continue;
			}
			String plainName = faction.getName() == null
					? ""
					: Formatter.formatId(faction.getName());
			if (filter.isEmpty()
					|| faction.getId().toLowerCase(java.util.Locale.ROOT).contains(filter)
					|| plainName.toLowerCase(java.util.Locale.ROOT).contains(filter)) {
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
			player.sendMessage("§cUsage: /war admin time status|reset|add|remove|skip-to-battle-day ...");
			return true;
		}
		String subcommand = args[2].toLowerCase(java.util.Locale.ROOT);
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
				player.sendMessage(result.message());
			}
			case "remove" -> {
				if (args.length < 4) {
					player.sendMessage("§cUsage: /war admin time remove <duration...>");
					return true;
				}
				CampaignTimeResult result = CampaignTimeCommandService.remove(
						Arrays.copyOfRange(args, 3, args.length));
				player.sendMessage(result.message());
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
					"§cUnknown subcommand. Use: status, reset, add, remove, skip-to-battle-day");
		}
		return true;
	}

	private boolean handleDevmode(Player player, String[] args) {
		if (args.length != 3) {
			player.sendMessage("§cUsage: /war admin devmode on|off|status");
			return true;
		}
		switch (args[2].toLowerCase(java.util.Locale.ROOT)) {
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
		CampaignSideRole coalition = WarCommandHelper.parseCoalitionScope(sideArg);
		int cleared = CampaignRaidService.resetRaidQuota(war, coalition);
		String scope = coalition == null
				? "both sides"
				: (coalition == CampaignSideRole.AGGRESSOR ? "aggressor" : "defender");
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

	private boolean handleTruce(Player player, String[] args) {
		String usage = "§cUsage: /war admin truce list [faction] | clear <factionA> <factionB>"
				+ " | cooldowns | clearcooldown <faction>";
		String subcommand = args.length >= 3 ? args[2].toLowerCase(java.util.Locale.ROOT) : "";
		if (subcommand.equals("list") && args.length <= 4) {
			String factionId = args.length == 4 ? args[3] : null;
			List<TruceService.Truce> truces = TruceService.list(factionId);
			if (truces.isEmpty()) {
				player.sendMessage(factionId == null
						? "§7No active truces."
						: "§7No active truces for §f" + factionName(factionId) + "§7.");
				return true;
			}
			player.sendMessage("§7Truces (" + truces.size() + "):");
			for (TruceService.Truce truce : truces) {
				player.sendMessage("§f" + factionName(truce.firstId()) + " §7- §f" + factionName(truce.secondId())
						+ " §7(" + TruceService.formatRemaining(TruceService.remainingUntil(truce.until()))
						+ " left, war " + truce.warId() + ")");
			}
			return true;
		}
		if (subcommand.equals("clear") && args.length == 5) {
			String first = factionName(args[3]);
			String second = factionName(args[4]);
			if (TruceService.clear(args[3], args[4])) {
				player.sendMessage("§aCleared the truce between §f" + first + " §aand §f" + second + "§a.");
			} else {
				player.sendMessage("§cNo truce between §f" + first + " §cand §f" + second + "§c.");
			}
			return true;
		}
		if (subcommand.equals("cooldowns") && args.length == 3) {
			List<TruceService.Cooldown> cooldowns = TruceService.listCooldowns();
			if (cooldowns.isEmpty()) {
				player.sendMessage("§7No active civil war cooldowns.");
				return true;
			}
			player.sendMessage("§7Civil war cooldowns (" + cooldowns.size() + "):");
			for (TruceService.Cooldown cooldown : cooldowns) {
				player.sendMessage("§f" + factionName(cooldown.factionId()) + " §7("
						+ TruceService.formatRemaining(TruceService.remainingUntil(cooldown.until()))
						+ " left, war " + cooldown.warId() + ")");
			}
			return true;
		}
		if (subcommand.equals("clearcooldown") && args.length == 4) {
			String name = factionName(args[3]);
			if (TruceService.clearCivilWarCooldown(args[3])) {
				player.sendMessage("§aCleared the civil war cooldown of §f" + name + "§a.");
			} else {
				player.sendMessage("§c" + name + " §chas no civil war cooldown.");
			}
			return true;
		}
		player.sendMessage(usage);
		return true;
	}

	private static String factionName(String factionId) {
		Faction faction = FactionManager.getByString(factionId);
		return faction != null && faction.getName() != null ? faction.getName() : factionId;
	}

	/** Schedule and battle tools that would leave a war voting on its day in a broken state. */
	static final java.util.Set<String> BUILDUP_REFUSED = java.util.Set.of(
			"opencvote", "closevote", "skipday", "castvote", "forcequorum", "setday", "sethour", "setscheduled",
			"battlecreate", "battlestart", "winbattle", "choice", "battlechoice", "defenderchoice", "pushchoice",
			"holdchoice");

	private boolean handleBuildup(Player player, String[] args) {
		if (args.length < 4) {
			player.sendMessage("§cUsage: /war admin buildup <warId> status|end|closevote|setday <yyyy-MM-dd>");
			return true;
		}
		War w = WarCommandHelper.parseWarId(args[2]).map(WarManager::getById).orElse(null);
		if (w == null || !w.isActive()) {
			player.sendMessage("§cNo active war by that id");
			return true;
		}
		java.time.Instant now = CampaignClock.now();
		switch (args[3].toLowerCase(java.util.Locale.ROOT)) {
			case "status" -> {
				player.sendMessage("§eWar " + w.getId() + " buildup: " + (w.isBuildupFrozen()
						? "§cfrozen until the first battle" : "§anot frozen by a buildup"));
				if (BuildupService.isDayVoteOpen(w)) {
					player.sendMessage("§7Day vote closes " + CampaignUiTimeFormatter.formatDateTime(w.getDayVoteClosesAt())
							+ "§7, " + BattleQuorumService.countDistinctVoters(w.getDayVotes()) + " voters.");
				} else {
					player.sendMessage("§7First battle day: "
							+ net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiCopy.formatBattleDay(w.getBattleDay()));
				}
				return true;
			}
			case "end" -> {
				BuildupService.end(w, now);
				player.sendMessage("§aEnded the buildup of war " + w.getId() + ". Its factions can build again.");
			}
			case "closevote" -> {
				if (!BuildupService.isDayVoteOpen(w)) {
					player.sendMessage("§cWar " + w.getId() + " is not voting on its first battle day.");
					return true;
				}
				BuildupService.DayVoteResult result = BuildupService.closeDayVote(w, now,
						BattleScheduleLookups.uuidToFactionForWar(w), BattleScheduleLookups.memberNameToUuid());
				player.sendMessage("§aClosed the day vote" + (result.quorum() ? "" : " §7(no quorum)")
						+ "§a. First battle day: " + result.day());
			}
			case "setday" -> {
				java.time.LocalDate day;
				try {
					day = args.length >= 5 ? java.time.LocalDate.parse(args[4]) : null;
				} catch (java.time.format.DateTimeParseException e) {
					day = null;
				}
				if (day == null || !w.isBuildupFrozen()) {
					player.sendMessage(day == null
							? "§cUsage: /war admin buildup <warId> setday <yyyy-MM-dd>"
							: "§cWar " + w.getId() + " is not in a buildup. Use §e/war admin schedule setday§c.");
					return true;
				}
				BuildupService.DayVoteResult result = BuildupService.setFirstBattleDay(w, day, now, true);
				player.sendMessage("§aFirst battle day of war " + w.getId() + ": " + result.day());
			}
			default -> {
				player.sendMessage("§cUsage: /war admin buildup <warId> status|end|closevote|setday <yyyy-MM-dd>");
				return true;
			}
		}
		WarManager.persist(w);
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
		String subcommand = args[3].toLowerCase(java.util.Locale.ROOT);
		if (BuildupService.isDayVoteOpen(w) && BUILDUP_REFUSED.contains(subcommand)) {
			player.sendMessage("§cWar " + w.getId() + " is still voting on its first battle day. Use §e/war admin buildup "
					+ w.getId() + " setday|closevote|end§c first.");
			return true;
		}
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
			case "setday" -> {
				if (args.length < 5) {
					yield WarScheduleAdminResult.error("Usage: /war admin schedule <id> setday <yyyy-MM-dd>");
				}
				yield WarScheduleAdminService.setDay(w, args[4]);
			}
			case "sethour" -> {
				if (args.length < 5) {
					yield WarScheduleAdminResult.error(
							"Usage: /war admin schedule <id> sethour <hour> (0 and 1 mean after midnight CET)");
				}
				int hour;
				try {
					hour = Integer.parseInt(args[4]);
				} catch (NumberFormatException e) {
					yield WarScheduleAdminResult.error("Hour must be a number.");
				}
				yield WarScheduleAdminService.setHour(w, hour);
			}
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
			case "battlerestart", "battleabort", "battleend", "battlehold" -> WarScheduleAdminService.battleStaff(
					w, subcommand, args.length >= 5 ? args[4] : null, player.getName());
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
					"Unknown subcommand. Use: opencvote, closevote, skipday, setday, sethour, castvote, "
							+ "forcequorum, setscheduled, battlecreate, battledelete, battlestart, battlerestart, "
							+ "battleabort, battleend, battlehold, winbattle, choice");
		};
		if (result.success()) {
			// A command that ended the war already deleted its file; saving it would bring it back.
			if (w.isActive()) {
				WarManager.persist(w);
			}
			player.sendMessage("§a" + result.message());
			Integer castHour = null;
			if ("castvote".equals(subcommand)) {
				castHour = BattleWindowService.normalizeInputHour(Integer.parseInt(args[4]));
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
