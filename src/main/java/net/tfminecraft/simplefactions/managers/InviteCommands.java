package net.tfminecraft.simplefactions.managers;

import net.tfminecraft.simplefactions.identity.Members;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Faction and guild invites: Accept/Decline chat buttons, {@code join [id]} and {@code decline <id>}.
 * {@code /faction decline} without an id still answers diplomatic requests.
 */
public final class InviteCommands {

	private static final String FACTION = "faction";
	private static final String GUILD = "guild";

	private InviteCommands() {}

	public static boolean handles(String command, String[] args) {
		if (!command.equalsIgnoreCase(FACTION) && !command.equalsIgnoreCase(GUILD)) return false;
		if (args.length == 0) return false;
		if (args[0].equalsIgnoreCase("join")) return args.length <= 2;
		return args[0].equalsIgnoreCase("decline") && args.length == 2;
	}

	public static boolean handle(Player p, String command, String[] args) {
		boolean guild = command.equalsIgnoreCase(GUILD);
		if (Members.key(p) == null) {
			p.sendMessage(CommandManager.NO_CHARACTER);
			return true;
		}
		if (args[0].equalsIgnoreCase("decline")) {
			if (guild) declineGuild(p, args[1]);
			else declineFaction(p, args[1]);
		} else if (args.length == 2) {
			if (guild) joinGuild(p, args[1]);
			else joinFaction(p, args[1]);
		} else if (guild) {
			List<Guild> invites = guildInvites(Members.key(p));
			joinOnlyInvite(p, GUILD, invites.stream().map(Guild::getId).toList(),
					invites.stream().map(Guild::getName).toList());
		} else {
			List<Faction> invites = factionInvites(Members.key(p));
			joinOnlyInvite(p, FACTION, invites.stream().map(Faction::getId).toList(),
					invites.stream().map(Faction::getName).toList());
		}
		return true;
	}

	/** The Accept/Decline line sent under an invite. */
	public static Component buttons(String command, String id, String name) {
		return Component.text()
				.append(button("[Accept]", NamedTextColor.GREEN, "/" + command + " join " + id,
						Component.text("Join ").append(legacy(name))))
				.append(Component.text("  "))
				.append(button("[Decline]", NamedTextColor.RED, "/" + command + " decline " + id,
						Component.text("Turn down this invite")))
				.build();
	}

	private static Component button(String label, NamedTextColor colour, String command, Component hover) {
		return Component.text(label, colour, TextDecoration.BOLD)
				.clickEvent(ClickEvent.runCommand(command))
				.hoverEvent(HoverEvent.showText(hover));
	}

	private static Component legacy(String text) {
		return LegacyComponentSerializer.legacySection().deserialize(text == null ? "" : text);
	}

	private static void joinOnlyInvite(Player p, String command, List<String> ids, List<String> names) {
		if (ids.isEmpty()) {
			p.sendMessage("§cYou have no " + command + " invites");
			return;
		}
		if (ids.size() == 1) {
			if (command.equals(GUILD)) joinGuild(p, ids.get(0));
			else joinFaction(p, ids.get(0));
			return;
		}
		p.sendMessage("§aYou have " + ids.size() + " " + command + " invites:");
		for (int i = 0; i < ids.size(); i++) {
			p.sendMessage(legacy(names.get(i)).append(Component.text(" ")).append(buttons(command, ids.get(i), names.get(i))));
		}
	}

	/** Ids of the player's pending invites that start with {@code prefix}, for tab completion. */
	public static List<String> inviteIds(String command, String player, String prefix) {
		List<String> ids = command.equalsIgnoreCase(GUILD)
				? guildInvites(player).stream().map(Guild::getId).toList()
				: factionInvites(player).stream().map(Faction::getId).toList();
		String start = prefix.toLowerCase(Locale.ROOT);
		return new ArrayList<>(ids.stream().filter(id -> id.toLowerCase(Locale.ROOT).startsWith(start)).toList());
	}

	static List<Guild> guildInvites(String player) {
		List<Guild> invites = new ArrayList<>();
		for (Faction f : FactionManager.factions) {
			for (Guild g : f.getGuildHandler().getGuilds()) {
				if (g.isInvited(player)) invites.add(g);
			}
		}
		return invites;
	}

	static List<Faction> factionInvites(String player) {
		List<Faction> invites = new ArrayList<>();
		for (Faction f : FactionManager.factions) {
			if (f.isInvited(player)) invites.add(f);
		}
		return invites;
	}

	private static void joinGuild(Player p, String id) {
		if (FactionManager.getGuildByLeader(Members.key(p)) != null) {
			p.sendMessage("§cYou are the leader of a guild");
			return;
		}
		if (!FactionManager.canJoinGuild(p)) {
			p.sendMessage("§cYou are already in a guild");
			return;
		}
		Guild g = FactionManager.getGuildByString(id);
		if (g == null) {
			p.sendMessage("§cNo guild by the id " + id);
			return;
		}
		if (!g.consumeInvite(Members.key(p))) {
			p.sendMessage("§cYou need to be invited to this guild by the leader first!");
			return;
		}
		Guild previous = FactionManager.getGuildByMember(Members.key(p));
		if (previous != null) {
			previous.kick(Members.key(p));
		}
		g.addMember(Members.key(p));
		p.sendMessage("§aJoined " + g.getName());
		g.getFaction().updatePrestige();
		tellOthers(g.getFaction(), p, "§a" + Members.name(p) + " joined the guild " + g.getName() + "§a!");
	}

	private static void joinFaction(Player p, String id) {
		if (FactionManager.getByMember(Members.key(p)) != null) {
			p.sendMessage("§cAlready in a faction, leave your current faction first!");
			return;
		}
		Faction f = FactionManager.getByString(id);
		if (f == null) {
			p.sendMessage("§cNo faction with that name exists");
			return;
		}
		if (net.tfminecraft.simplefactions.guild.HeldRealms.isHeld(f)) {
			p.sendMessage("§c" + f.getName() + " §chas no living members, so nobody can join it.");
			return;
		}
		if (!f.consumeInvite(Members.key(p))) {
			p.sendMessage("§cYou need to be invited to this faction by the leader first!");
			return;
		}
		f.addMember(Members.key(p));
		p.sendMessage("§aJoined " + f.getName());
		f.updatePrestige();
		tellOthers(f, p, "§a" + Members.name(p) + " joined the faction!");
	}

	private static void declineGuild(Player p, String id) {
		Guild g = FactionManager.getGuildByString(id);
		if (g != null && net.tfminecraft.simplefactions.guild.LeaderlessGuilds.decline(p, g)) return;
		if (g == null || !g.consumeInvite(Members.key(p))) {
			p.sendMessage("§cYou have no invite from that guild");
			return;
		}
		p.sendMessage("§aDeclined the invite to " + g.getName());
		tellLeader(g.getLeader(), "§c" + Members.name(p) + " declined your invite to " + g.getName());
	}

	private static void declineFaction(Player p, String id) {
		Faction f = FactionManager.getByString(id);
		if (f == null || !f.consumeInvite(Members.key(p))) {
			p.sendMessage("§cYou have no invite from that faction");
			return;
		}
		p.sendMessage("§aDeclined the invite to " + f.getName());
		tellLeader(f.getLeader(), "§c" + Members.name(p) + " declined your invite to " + f.getName());
	}

	private static void tellOthers(Faction f, Player joiner, String message) {
		for (Player pl : Bukkit.getOnlinePlayers()) {
			if (!pl.equals(joiner) && f.getMembers().contains(Members.key(pl))) {
				pl.sendMessage(message);
			}
		}
	}

	private static void tellLeader(String leader, String message) {
		Members.online(leader).ifPresent(online -> online.sendMessage(message));
	}
}
