package net.tfminecraft.simplefactions.admin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.migration.QuarantineCommands;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.rest.RestServer;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.utils.Permissions;

/**
 * {@code /faction admin ...} and {@code /guild admin ...}: staff-only fixes for
 * realms and guilds whose leader is away or stuck. Everything that needs no
 * location or held item also works from the console. Changes are logged.
 */
public final class StaffAdminCommand {
	public static final String SUBCOMMAND = "admin";
	public static final List<String> FACTION_SUBCOMMANDS = List.of("info", "who", "rename", "renamesettlement",
			"movecapital", "confirm", "setcolour", "setbanner", "set", "kick", "removeinstallation", "quarantine");
	public static final List<String> GUILD_SUBCOMMANDS = List.of("info", "rename", "setcolour", "setbanner", "kick");
	static final String[] FACTION_USAGE = {
			"§6/faction admin info <faction> §7- leader, capital, settlements, guilds and bank",
			"§6/faction admin who <character> §7- which faction and guild a character is in",
			"§6/faction admin rename <faction> <name...> §7- change the faction's name",
			"§6/faction admin renamesettlement <faction> <settlement|capital> <name...> §7- rename a settlement",
			"§6/faction admin movecapital <faction> [name...] §7- move the capital to the province you stand in",
			"§6/faction admin confirm §7- apply the capital move you just previewed",
			"§6/faction admin setcolour <faction> <R,G,B> §7- change the map colour",
			"§6/faction admin setbanner <faction> §7- use the banner in your hand",
			"§6/faction admin set <faction> <culture|religion|rulingsystem|rulertitle> <value...>",
			"§6/faction admin kick <faction> <character> §7- remove a member from the faction",
			"§6/faction admin removeinstallation <faction> <id> §7- take down a station, port, airport or fort, or cancel its construction",
			"§6/faction admin quarantine list|assign §7- unmatched character records",
	};
	static final String[] GUILD_USAGE = {
			"§6/guild admin info <guild> §7- leader, members, seat and bank",
			"§6/guild admin rename <guild> <name...> §7- change the guild's name",
			"§6/guild admin setcolour <guild> <R,G,B> §7- change the map colour",
			"§6/guild admin setbanner <guild> §7- use the banner in your hand",
			"§6/guild admin kick <guild> <character> §7- send a member back to the faction's main guild",
	};
	static final String DENIED = "§a[SimpleFactions]§c You do not have access to this command";
	static final String IN_GAME = "§cOnly a player in game can do that";
	static final long CONFIRM_MILLIS = 60_000;

	private record Pending(StaffAdminService.CapitalMove move, long expires) {}

	private static final Map<UUID, Pending> pending = new HashMap<>();
	static LongSupplier clock = System::currentTimeMillis;

	private StaffAdminCommand() {}

	/** Also covers {@code /faction admin quarantine}, which is passed to its own handler. */
	public static boolean matches(String command, String[] args) {
		return (command.equalsIgnoreCase("faction") || command.equalsIgnoreCase("guild"))
				&& args.length >= 1 && args[0].equalsIgnoreCase(SUBCOMMAND);
	}

	public static boolean handle(CommandSender sender, String command, String[] args) {
		if (!Permissions.isAdmin(sender)) {
			sender.sendMessage(DENIED);
			return true;
		}
		boolean guild = command.equalsIgnoreCase("guild");
		if (args.length < 2) {
			sender.sendMessage(guild ? GUILD_USAGE : FACTION_USAGE);
			return true;
		}
		String sub = args[1].toLowerCase();
		if (guild) handleGuild(sender, sub, args);
		else handleFaction(sender, sub, args);
		return true;
	}

	private static String rest(String[] args, int from) {
		return String.join(" ", Arrays.copyOfRange(args, from, args.length));
	}

	private static String usage(String[] usage, List<String> subs, String sub) {
		return usage[subs.indexOf(sub)];
	}

	private static void send(CommandSender sender, List<String> lines) {
		for (String line : lines) sender.sendMessage(line);
	}

	/** Sends the result and records the change in the server log. */
	private static void apply(CommandSender sender, String command, String[] args, List<String> lines) {
		send(sender, lines);
		SimpleFactions.getInstance().getLogger().info("[StaffAdmin] " + sender.getName() + " ran /" + command + " " + rest(args, 0)
				+ ": " + ChatColor.stripColor(String.join(" | ", lines)));
	}

	private static Faction faction(CommandSender sender, String id) {
		Faction faction = FactionManager.getByString(id);
		if (faction == null) sender.sendMessage("§cNo faction by the id " + id);
		return faction;
	}

	private static Guild guild(CommandSender sender, String id) {
		Guild guild = FactionManager.getGuildByString(id);
		if (guild == null) sender.sendMessage("§cNo guild by the id " + id);
		return guild;
	}

	private static Player player(CommandSender sender) {
		if (sender instanceof Player player) return player;
		sender.sendMessage(IN_GAME);
		return null;
	}

	/** Arguments a subcommand needs, counting {@code admin} and the subcommand itself. */
	static int minArgs(String sub) {
		return switch (sub) {
			case "confirm" -> 2;
			case "info", "who", "movecapital", "setbanner" -> 3;
			case "rename", "setcolour", "kick", "removeinstallation" -> 4;
			default -> 5;
		};
	}

	/** Whether the arguments fit {@code sub}; these take nothing after their target. */
	static boolean fits(String sub, int length) {
		boolean exact = sub.equals("confirm") || sub.equals("info") || sub.equals("setbanner") || sub.equals("removeinstallation");
		return exact ? length == minArgs(sub) : length >= minArgs(sub);
	}

	private static void handleFaction(CommandSender sender, String sub, String[] args) {
		if (sub.equals("quarantine")) {
			QuarantineCommands.handle(sender, args);
			return;
		}
		if (!FACTION_SUBCOMMANDS.contains(sub)) {
			sender.sendMessage(FACTION_USAGE);
			return;
		}
		if (!fits(sub, args.length)) {
			sender.sendMessage(usage(FACTION_USAGE, FACTION_SUBCOMMANDS, sub));
			return;
		}
		if (sub.equals("who")) {
			send(sender, StaffAdminService.who(rest(args, 2)));
			return;
		}
		if (sub.equals("confirm")) {
			confirm(sender, args);
			return;
		}
		if ((sub.equals("movecapital") || sub.equals("renamesettlement")) && !Cache.requireProvinces(sender)) return;
		Faction faction = faction(sender, args[2]);
		if (faction == null) return;
		switch (sub) {
			case "info" -> send(sender, StaffAdminService.info(faction));
			case "rename" -> apply(sender, "faction", args, StaffAdminService.rename(faction, rest(args, 3)));
			case "renamesettlement" -> apply(sender, "faction", args, StaffAdminService.renameSettlement(faction, args[3], rest(args, 4)));
			case "movecapital" -> previewCapital(sender, faction, args);
			case "setcolour" -> apply(sender, "faction", args, StaffAdminService.setColour(faction, String.join("", Arrays.copyOfRange(args, 3, args.length))));
			case "setbanner" -> {
				Player player = player(sender);
				if (player != null) apply(sender, "faction", args, StaffAdminService.setBanner(faction, player.getInventory().getItemInMainHand()));
			}
			case "set" -> apply(sender, "faction", args, StaffAdminService.set(faction, args[3], rest(args, 4)));
			case "removeinstallation" -> apply(sender, "faction", args, StaffAdminService.removeInstallation(faction, args[3]));
			default -> apply(sender, "faction", args, StaffAdminService.kick(faction, rest(args, 3)));
		}
	}

	private static void previewCapital(CommandSender sender, Faction faction, String[] args) {
		Player player = player(sender);
		if (player == null) return;
		String name = args.length > 3 ? rest(args, 3) : null;
		List<String> lines = new ArrayList<>();
		StaffAdminService.CapitalMove move = StaffAdminService.previewCapital(player, faction, RestServer.getProvince(player), name, lines);
		send(sender, lines);
		if (move == null) pending.remove(player.getUniqueId());
		else pending.put(player.getUniqueId(), new Pending(move, clock.getAsLong() + CONFIRM_MILLIS));
	}

	private static void confirm(CommandSender sender, String[] args) {
		Player player = player(sender);
		if (player == null) return;
		Pending waiting = pending.remove(player.getUniqueId());
		if (waiting == null || waiting.expires() < clock.getAsLong()) {
			sender.sendMessage("§cNothing to confirm. Run §e/faction admin movecapital §cagain.");
			return;
		}
		if (!Cache.requireProvinces(sender)) return;
		apply(sender, "faction", args, StaffAdminService.moveCapital(player, waiting.move(), RestServer.getProvince(player)));
	}

	private static void handleGuild(CommandSender sender, String sub, String[] args) {
		if (!GUILD_SUBCOMMANDS.contains(sub)) {
			sender.sendMessage(GUILD_USAGE);
			return;
		}
		if (!fits(sub, args.length)) {
			sender.sendMessage(usage(GUILD_USAGE, GUILD_SUBCOMMANDS, sub));
			return;
		}
		Guild guild = guild(sender, args[2]);
		if (guild == null) return;
		switch (sub) {
			case "info" -> send(sender, StaffAdminService.guildInfo(guild));
			case "rename" -> apply(sender, "guild", args, StaffAdminService.guildRename(guild, rest(args, 3)));
			case "setcolour" -> apply(sender, "guild", args, StaffAdminService.guildSetColour(guild, String.join("", Arrays.copyOfRange(args, 3, args.length))));
			case "setbanner" -> {
				Player player = player(sender);
				if (player != null) apply(sender, "guild", args, StaffAdminService.guildSetBanner(guild, player.getInventory().getItemInMainHand()));
			}
			default -> apply(sender, "guild", args, StaffAdminService.guildKick(guild, rest(args, 3)));
		}
	}

	// ---- Tab completion ----

	public static List<String> complete(CommandSender sender, String command, String[] args) {
		if (!Permissions.isAdmin(sender) || !matches(command, args) || args.length < 2) return List.of();
		boolean guild = command.equalsIgnoreCase("guild");
		if (args.length == 2) return filtered(new ArrayList<>(guild ? GUILD_SUBCOMMANDS : FACTION_SUBCOMMANDS), args[1]);
		String sub = args[1].toLowerCase();
		if (!guild && sub.equals("quarantine")) return QuarantineCommands.complete(sender, args);
		List<String> subs = guild ? GUILD_SUBCOMMANDS : FACTION_SUBCOMMANDS;
		if (!subs.contains(sub) || sub.equals("confirm") || sub.equals("who")) return List.of();
		if (args.length == 3) {
			List<String> ids = new ArrayList<>();
			if (guild) {
				for (Guild each : FactionManager.getAllGuilds()) if (each != null) ids.add(each.getId());
			} else {
				for (Faction each : FactionManager.getCopy()) ids.add(each.getId());
			}
			return filtered(ids, args[2]);
		}
		String[] target = Arrays.copyOfRange(args, 2, args.length);
		if (guild) {
			Guild each = FactionManager.getGuildByString(args[2]);
			if (each == null) return List.of();
			return switch (sub) {
				case "setcolour" -> args.length == 4 ? filtered(new ArrayList<>(List.of(String.valueOf(each.getRGB()))), args[3]) : List.of();
				case "kick" -> Members.completions(Members.display(each.getMembers()), target);
				default -> List.of();
			};
		}
		Faction faction = FactionManager.getByString(args[2]);
		if (faction == null) return List.of();
		return switch (sub) {
			case "renamesettlement" -> {
				if (args.length != 4) yield List.of();
				List<String> ids = new ArrayList<>(List.of("capital"));
				for (Settlement settlement : faction.getSettlementHandler().getAll()) ids.add(settlement.getId());
				yield filtered(ids, args[3]);
			}
			case "setcolour" -> args.length == 4 ? filtered(new ArrayList<>(List.of(String.valueOf(faction.getRGB()))), args[3]) : List.of();
			case "set" -> args.length == 4 ? filtered(new ArrayList<>(StaffAdminService.FIELDS), args[3]) : List.of();
			case "kick" -> Members.completions(Members.display(faction.getMembers()), target);
			case "removeinstallation" -> args.length == 4 ? filtered(StaffAdminService.installationIds(faction), args[3]) : List.of();
			default -> List.of();
		};
	}

	private static List<String> filtered(List<String> values, String prefix) {
		String lower = prefix.toLowerCase();
		values.removeIf(value -> value == null || !value.toLowerCase().startsWith(lower));
		return values;
	}

	/** Test hook: forgets every previewed capital move. */
	static void clearPending() {
		pending.clear();
	}
}
