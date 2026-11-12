package net.tfminecraft.simplefactions.war.core;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;

public class WarTabCompletion implements TabCompleter {

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		if (!cmd.getName().equalsIgnoreCase(WarCommandManager.CMD)) {
			return List.of();
		}
		if (args.length == 1) {
			List<String> completions = new ArrayList<>();
			if ("list".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
				completions.add("list");
			}
			addIfPrefix(completions, args[0], "vote");
			addIfPrefix(completions, args[0], "installations");
			addIfPrefix(completions, args[0], "calls");
			addIfPrefix(completions, args[0], "call");
			if (Permissions.isAdmin(sender) && "admin".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
				completions.add("admin");
			}
			return completions;
		}
		if (args[0].equalsIgnoreCase("call")) {
			return callCompletions(sender, args);
		}
		if (args[0].equalsIgnoreCase("installations")) {
			List<String> completions = new ArrayList<>();
			if (args.length == 2) {
				for (net.tfminecraft.simplefactions.war.core.War war : net.tfminecraft.simplefactions.managers.WarManager.getActive()) {
					addIfPrefix(completions, args[1], String.valueOf(war.getId()));
				}
			}
			return completions;
		}
		if (args[0].equalsIgnoreCase("vote")) {
			List<String> completions = new ArrayList<>();
			if (args.length == 2) {
				for (net.tfminecraft.simplefactions.war.core.War war : net.tfminecraft.simplefactions.managers.WarManager.getActive()) {
					// Only wars whose vote menu can open: raids and wars without a route have none.
					if (war.getWarType() != net.tfminecraft.simplefactions.war.enums.WarType.RAID
							&& war.getCampaignProvinces() != null && !war.getCampaignProvinces().isEmpty()) {
						addIfPrefix(completions, args[1], String.valueOf(war.getId()));
					}
				}
			}
			return completions;
		}
		if (!Permissions.isAdmin(sender) || !args[0].equalsIgnoreCase("admin")) {
			return List.of();
		}
		if (args.length == 2) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[1], "declare");
			addIfPrefix(completions, args[1], "end");
			addIfPrefix(completions, args[1], "win");
			addIfPrefix(completions, args[1], "status");
			addIfPrefix(completions, args[1], "path");
			addIfPrefix(completions, args[1], "time");
			addIfPrefix(completions, args[1], "schedule");
			addIfPrefix(completions, args[1], "buildup");
			addIfPrefix(completions, args[1], "devmode");
			addIfPrefix(completions, args[1], "raid");
			addIfPrefix(completions, args[1], "reparations");
			addIfPrefix(completions, args[1], "truce");
			addIfPrefix(completions, args[1], "history");
			addIfPrefix(completions, args[1], "factions");
			addIfPrefix(completions, args[1], "forceinstallation");
			addIfPrefix(completions, args[1], "losses");
			addIfPrefix(completions, args[1], "army");
			addIfPrefix(completions, args[1], "wounds");
			return completions;
		}
		if (args[1].equalsIgnoreCase("losses") || args[1].equalsIgnoreCase("army") || args[1].equalsIgnoreCase("wounds")) {
			return armyCompletions(args);
		}
		if (args[1].equalsIgnoreCase("forceinstallation")) {
			return forceInstallationCompletions(args);
		}
		if (args.length == 3 && args[1].equalsIgnoreCase("reparations")) {
			return factionIds(args[2]);
		}
		if (args.length == 3 && args[1].equalsIgnoreCase("factions")) {
			return factionIds(args[2]);
		}
		if (args[1].equalsIgnoreCase("declare")) {
			return declareCompletions(args);
		}
		if (args.length == 4 && args[1].equalsIgnoreCase("reparations")) {
			return factionIds(args[3]);
		}
		if (args.length == 3 && args[1].equalsIgnoreCase("history")) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[2], "status");
			addIfPrefix(completions, args[2], "upload-battles");
			return completions;
		}
		if (args.length == 3 && args[1].equalsIgnoreCase("truce")) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[2], "list");
			addIfPrefix(completions, args[2], "clear");
			addIfPrefix(completions, args[2], "cooldowns");
			addIfPrefix(completions, args[2], "clearcooldown");
			return completions;
		}
		if (args.length == 4 && args[1].equalsIgnoreCase("truce")
				&& (args[2].equalsIgnoreCase("list")
						|| args[2].equalsIgnoreCase("clear")
						|| args[2].equalsIgnoreCase("clearcooldown"))) {
			return factionIds(args[3]);
		}
		if (args.length == 5 && args[1].equalsIgnoreCase("truce") && args[2].equalsIgnoreCase("clear")) {
			return factionIds(args[4]);
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
			addIfPrefix(completions, args[2], "remove");
			addIfPrefix(completions, args[2], "skip-to-battle-day");
			return completions;
		}
		if (args.length == 3
				&& (args[1].equalsIgnoreCase("end")
						|| args[1].equalsIgnoreCase("win")
						|| args[1].equalsIgnoreCase("status")
						|| args[1].equalsIgnoreCase("path")
						|| args[1].equalsIgnoreCase("schedule")
						|| args[1].equalsIgnoreCase("buildup"))) {
			return activeWarIds(args[2]);
		}
		if (args.length == 4 && args[1].equalsIgnoreCase("buildup")) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[3], "status");
			addIfPrefix(completions, args[3], "end");
			addIfPrefix(completions, args[3], "setday");
			return completions;
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
		if (args.length == 4 && args[1].equalsIgnoreCase("time")
				&& (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("remove"))) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[3], "1h");
			addIfPrefix(completions, args[3], "1d");
			addIfPrefix(completions, args[3], "4h");
			addIfPrefix(completions, args[3], "1h31m");
			return completions;
		}
		if (args.length == 4 && args[1].equalsIgnoreCase("schedule")) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[3], "availability");
			addIfPrefix(completions, args[3], "lockday");
			addIfPrefix(completions, args[3], "unlockday");
			addIfPrefix(completions, args[3], "sethour");
			addIfPrefix(completions, args[3], "skipday");
			addIfPrefix(completions, args[3], "castvote");
			addIfPrefix(completions, args[3], "setscheduled");
			addIfPrefix(completions, args[3], "battlecreate");
			addIfPrefix(completions, args[3], "battledelete");
			addIfPrefix(completions, args[3], "battlestart");
			addIfPrefix(completions, args[3], "battlerestart");
			addIfPrefix(completions, args[3], "battleabort");
			addIfPrefix(completions, args[3], "battleend");
			addIfPrefix(completions, args[3], "battlehold");
			addIfPrefix(completions, args[3], "winbattle");
			addIfPrefix(completions, args[3], "choice");
			addIfPrefix(completions, args[3], "battlechoice");
			addIfPrefix(completions, args[3], "defenderchoice");
			return completions;
		}
		if (args.length == 5 && args[1].equalsIgnoreCase("schedule")) {
			if ("battleend".equalsIgnoreCase(args[3])) {
				List<String> completions = new ArrayList<>();
				addIfPrefix(completions, args[4], "nowinner");
				addIfPrefix(completions, args[4], "attacker");
				addIfPrefix(completions, args[4], "defender");
				return completions;
			}
			if ("battlehold".equalsIgnoreCase(args[3])) {
				List<String> completions = new ArrayList<>();
				addIfPrefix(completions, args[4], "on");
				addIfPrefix(completions, args[4], "off");
				return completions;
			}
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
		if (args.length == 5 && args[1].equalsIgnoreCase("schedule") && isDayArgument(args[3])) {
			return upcomingDays(args[4]);
		}
		if (args.length == 5 && args[1].equalsIgnoreCase("buildup") && args[3].equalsIgnoreCase("setday")) {
			return upcomingDays(args[4]);
		}
		if (args.length == 5 && args[1].equalsIgnoreCase("schedule") && args[3].equalsIgnoreCase("sethour")) {
			return startHours(args[4]);
		}
		if (args.length == 6 && args[1].equalsIgnoreCase("schedule") && args[3].equalsIgnoreCase("castvote")) {
			return startHours(args[5]);
		}
		if (args.length == 6 && args[1].equalsIgnoreCase("schedule") && args[3].equalsIgnoreCase("sethour")) {
			return upcomingDays(args[5]);
		}
		if (args.length == 7 && args[1].equalsIgnoreCase("schedule") && args[3].equalsIgnoreCase("castvote")) {
			List<String> completions = new ArrayList<>();
			addIfPrefix(completions, args[6], "attacker");
			addIfPrefix(completions, args[6], "defender");
			addIfPrefix(completions, args[6], "both");
			return completions;
		}
		return List.of();
	}

	private static boolean isDayArgument(String subcommand) {
		return subcommand.equalsIgnoreCase("lockday") || subcommand.equalsIgnoreCase("unlockday")
				|| subcommand.equalsIgnoreCase("castvote");
	}

	/** The next week's battle days, as staff type them. */
	private static List<String> upcomingDays(String prefix) {
		List<String> completions = new ArrayList<>();
		java.time.LocalDate today = BattleScheduleService.battleDayDate(CampaignClock.now());
		for (int offset = 0; offset < 7; offset++) {
			addIfPrefix(completions, prefix, today.plusDays(offset).toString());
		}
		return completions;
	}

	private static List<String> startHours(String prefix) {
		List<String> completions = new ArrayList<>();
		for (int hour : BattleWindowService.listValidInputHours()) {
			String value = String.valueOf(hour);
			if (value.startsWith(prefix)) {
				completions.add(value);
			}
		}
		return completions;
	}

	private static List<String> declareCompletions(String[] args) {
		if (args.length == 3 || args.length == 4) {
			return factionIds(args[args.length - 1]);
		}
		if (args.length == 5) {
			List<String> completions = new ArrayList<>();
			for (WarGoalType goal : WarGoalType.values()) {
				if (!goal.isMovementOrigin()) {
					addIfPrefix(completions, args[4], goal.toJson());
				}
			}
			return completions;
		}
		if (args.length == 6 && WarGoalType.fromJson(args[4]) == WarGoalType.TRANSFER_SUBJECT) {
			return factionIds(args[5]);
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
		String lower = prefix.toLowerCase(java.util.Locale.ROOT);
		for (War war : WarManager.getActive()) {
			String id = String.valueOf(war.getId());
			if (id.startsWith(lower)) {
				completions.add(id);
			}
		}
		return completions;
	}

	/** Campaign battle ids, then the factions of that battle's war, then that faction's installations. */
	private static List<String> forceInstallationCompletions(String[] args) {
		List<String> completions = new ArrayList<>();
		if (args.length == 3) {
			for (var battle : net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.get()) {
				if (battle.getWarId() != null && !battle.isCampaignRaid()) {
					addIfPrefix(completions, args[2], battle.getId());
				}
			}
			return completions;
		}
		var battle = net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.getByString(args[2]);
		War war = battle == null || battle.getWarId() == null || battle.isCampaignRaid() ? null : WarManager.getById(battle.getWarId());
		if (war == null) {
			return completions;
		}
		if (args.length == 4) {
			for (Side side : List.of(war.getAttackers(), war.getDefenders())) {
				for (Faction faction : net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers
						.collectParticipatingFactions(side)) {
					addIfPrefix(completions, args[3], faction.getId());
				}
			}
			return completions;
		}
		Faction faction = FactionManager.getByString(args[3]);
		if (args.length == 5 && faction != null && war.getSide(faction) != null) {
			for (var installation : faction.getInstallationHandler().getAll()) {
				addIfPrefix(completions, args[4], installation.getId());
			}
		}
		return completions;
	}

	/**
	 * {@code losses <war> <battle#> <side> set|add|remove}, {@code army <faction>} and
	 * {@code wounds <war> <faction> <regiment> add|remove} or {@code wounds company <guild> set}.
	 */
	private static List<String> armyCompletions(String[] args) {
		String sub = args[1].toLowerCase(java.util.Locale.ROOT);
		String typed = args[args.length - 1];
		List<String> completions = new ArrayList<>();
		if (sub.equals("army")) {
			return args.length == 3 ? factionIds(typed) : completions;
		}
		if (args.length == 3) {
			completions.addAll(activeWarIds(typed));
			if (sub.equals("wounds")) {
				addIfPrefix(completions, typed, "company");
			}
			return completions;
		}
		boolean company = args[2].equalsIgnoreCase("company");
		War war = company ? null : WarCommandHelper.parseWarId(args[2]).map(WarManager::getById).orElse(null);
		List<String> options = switch (sub + ":" + args.length) {
			case "losses:4" -> war == null ? List.of() : war.getLedger().battles().stream()
					.map(entry -> String.valueOf(entry.number())).toList();
			case "losses:5" -> List.of("attacker", "defender");
			case "losses:6" -> List.of("set", "add", "remove");
			case "wounds:4" -> company ? companyGuildIds() : factionIds(typed);
			case "wounds:5" -> company ? List.of("set") : regimentIds(args[3]);
			case "wounds:6" -> company ? List.of() : List.of("add", "remove");
			default -> List.of();
		};
		for (String option : options) {
			addIfPrefix(completions, typed, option);
		}
		return completions;
	}

	private static List<String> companyGuildIds() {
		List<String> ids = new ArrayList<>();
		for (net.tfminecraft.simplefactions.guild.Guild guild : FactionManager.getAllGuilds()) {
			if (guild.getCompany() != null) {
				ids.add(guild.getId());
			}
		}
		return ids;
	}

	private static List<String> regimentIds(String factionId) {
		Faction faction = FactionManager.getByString(factionId);
		List<String> ids = new ArrayList<>();
		if (faction != null && faction.getMilitary() != null) {
			for (var regiment : faction.getMilitary().getRegiments()) {
				if (!regiment.isLevy() && !regiment.isEquipment()) {
					ids.add(regiment.getId());
				}
			}
		}
		return ids;
	}

	private static List<String> factionIds(String prefix) {
		List<String> completions = new ArrayList<>();
		String lower = prefix == null ? "" : prefix.toLowerCase(java.util.Locale.ROOT);
		for (Faction faction : FactionManager.factions) {
			if (faction == null || faction.getId() == null) {
				continue;
			}
			String id = faction.getId();
			if (id.toLowerCase(java.util.Locale.ROOT).startsWith(lower)) {
				completions.add(id);
			}
		}
		return completions;
	}

	private static void addIfPrefix(List<String> completions, String typed, String candidate) {
		if (candidate.toLowerCase(java.util.Locale.ROOT).startsWith(typed.toLowerCase(java.util.Locale.ROOT))) {
			completions.add(candidate);
		}
	}

	/** {@code /war call accept|decline <war id>}: the wars with an open call to the sender's faction. */
	private static List<String> callCompletions(CommandSender sender, String[] args) {
		List<String> completions = new ArrayList<>();
		if (args.length == 2) {
			addIfPrefix(completions, args[1], "accept");
			addIfPrefix(completions, args[1], "decline");
		} else if (args.length == 3 && sender instanceof org.bukkit.entity.Player player) {
			for (var pending : net.tfminecraft.simplefactions.war.core.CallToArmsService.pendingFor(player)) {
				addIfPrefix(completions, args[2], String.valueOf(pending.war().getId()));
			}
		}
		return completions;
	}
}
