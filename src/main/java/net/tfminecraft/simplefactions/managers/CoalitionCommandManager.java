package net.tfminecraft.simplefactions.managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.diplomacy.coalition.Coalition;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionCodePrompt;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionCost;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionService;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Permissions;

/** {@code /coalition}: forming, joining and running coalitions, plus staff tools. */
public final class CoalitionCommandManager implements CommandExecutor {
	public static final String CMD = "coalition";
	public static final List<String> SUBCOMMANDS = List.of(
			"menu", "list", "create", "invite", "join", "leave", "kick", "leader", "target",
			"setbanner", "resetbanner", "offensive", "disband");
	public static final List<String> ADMIN_SUBCOMMANDS = List.of("disband", "offensive", "add", "kick", "setleader", "cost");
	static final String NOT_A_LEADER = "§cOnly a faction leader can do that.";

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
			if (!Permissions.isAdmin(sender)) {
				sender.sendMessage("§cYou do not have access to this command.");
				return true;
			}
			admin(sender, args);
			return true;
		}
		if (!(sender instanceof Player p)) {
			sender.sendMessage("§cThis command can only be used by players.");
			return true;
		}
		String sub = args.length == 0 ? "menu" : args[0].toLowerCase();
		switch (sub) {
			case "menu" -> menu(p);
			case "list" -> FactionManager.getInv().coalitionView.list(p, 0);
			case "create" -> create(p, args);
			case "invite" -> withFaction(p, args, "invite", CoalitionService::invite);
			case "join" -> join(p, args);
			case "leave" -> asLeader(p, leader -> CoalitionService.leave(leader));
			case "kick" -> withFaction(p, args, "kick", CoalitionService::kick);
			case "leader" -> withFaction(p, args, "leader", CoalitionService::transferLeadership);
			case "target" -> withFaction(p, args, "target", CoalitionService::retarget);
			case "setbanner" -> asLeader(p, leader -> CoalitionService.setBanner(leader, p.getInventory().getItemInMainHand()));
			case "resetbanner" -> asLeader(p, CoalitionService::resetBanner);
			case "offensive" -> {
				Faction leader = FactionManager.getByLeader(Members.key(p));
				if (leader == null) p.sendMessage(NOT_A_LEADER);
				else CoalitionCodePrompt.begin(p, leader);
			}
			case "disband" -> asLeader(p, CoalitionService::disband);
			default -> usage(p);
		}
		return true;
	}

	private void menu(Player p) {
		Coalition coalition = CoalitionService.of(FactionManager.getByMember(Members.key(p)));
		if (coalition == null) {
			p.sendMessage("§7Your faction is not in a coalition. See §e/coalition list§7.");
			return;
		}
		FactionManager.getInv().coalitionView.menu(p, coalition);
	}

	private void create(Player p, String[] args) {
		if (args.length < 3) {
			p.sendMessage("§cUsage: /coalition create <target faction> <name>");
			return;
		}
		Faction leader = FactionManager.getByLeader(Members.key(p));
		if (leader == null) {
			p.sendMessage(NOT_A_LEADER);
			return;
		}
		Faction target = MercenaryCommandManager.resolveFaction(args[1]);
		String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
		p.sendMessage(CoalitionService.create(leader, name, target).message());
	}

	private void join(Player p, String[] args) {
		if (args.length < 2) {
			p.sendMessage("§cUsage: /coalition join <coalition>");
			return;
		}
		Faction joiner = FactionManager.getByLeader(Members.key(p));
		if (joiner == null) {
			p.sendMessage(NOT_A_LEADER);
			return;
		}
		p.sendMessage(CoalitionService.join(joiner, CoalitionService.byName(rest(args, 1))).message());
	}

	private interface LeaderAction {
		CoalitionService.Result apply(Faction leader);
	}

	private interface FactionAction {
		CoalitionService.Result apply(Faction leader, Faction other);
	}

	private void asLeader(Player p, LeaderAction action) {
		Faction leader = FactionManager.getByLeader(Members.key(p));
		if (leader == null) {
			p.sendMessage(NOT_A_LEADER);
			return;
		}
		p.sendMessage(action.apply(leader).message());
	}

	private void withFaction(Player p, String[] args, String sub, FactionAction action) {
		if (args.length < 2) {
			p.sendMessage("§cUsage: /coalition " + sub + " <faction>");
			return;
		}
		asLeader(p, leader -> action.apply(leader, MercenaryCommandManager.resolveFaction(rest(args, 1))));
	}

	private void usage(Player p) {
		p.sendMessage("§e/coalition §7- open your coalition's menu");
		p.sendMessage("§e/coalition list §7- every coalition");
		p.sendMessage("§e/coalition create <target faction> <name> §7- form a coalition");
		p.sendMessage("§e/coalition invite|kick|leader <faction> §7- manage members");
		p.sendMessage("§e/coalition join <coalition> §7- accept an invite");
		p.sendMessage("§e/coalition leave|disband §7- leave or end it");
		p.sendMessage("§e/coalition target <faction> §7- choose a new target after losing one");
		p.sendMessage("§e/coalition setbanner|resetbanner §7- the coalition banner");
		p.sendMessage("§e/coalition offensive §7- enter a staff code to go offensive");
	}

	// ---- Staff ----

	private void admin(CommandSender sender, String[] args) {
		String sub = args.length < 2 ? "" : args[1].toLowerCase();
		switch (sub) {
			case "cost" -> cost(sender, Arrays.copyOfRange(args, 2, args.length));
			case "disband" -> {
				Coalition coalition = coalitionArg(sender, args, 2, "disband <coalition>");
				if (coalition == null) return;
				CoalitionService.adminDisband(coalition);
				sender.sendMessage("§7Disbanded " + CoalitionService.displayName(coalition) + "§7.");
			}
			case "offensive" -> {
				if (args.length < 4 || !(args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("off"))) {
					sender.sendMessage("§cUsage: /coalition admin offensive <on|off> <coalition>");
					return;
				}
				Coalition coalition = coalitionArg(sender, args, 3, "offensive <on|off> <coalition>");
				if (coalition == null) return;
				CoalitionService.setOffensive(coalition, args[2].equalsIgnoreCase("on"));
				sender.sendMessage("§7" + CoalitionService.displayName(coalition) + " §7is now "
						+ (coalition.isOffensive() ? "offensive" : "defensive") + ".");
			}
			case "add", "kick", "setleader" -> {
				if (args.length < 4) {
					sender.sendMessage("§cUsage: /coalition admin " + sub + " <faction> <coalition>");
					return;
				}
				Coalition coalition = coalitionArg(sender, args, 3, sub + " <faction> <coalition>");
				if (coalition == null) return;
				Faction faction = MercenaryCommandManager.resolveFaction(args[2]);
				CoalitionService.Result result = switch (sub) {
					case "add" -> CoalitionService.adminAdd(coalition, faction);
					case "kick" -> CoalitionService.adminKick(coalition, faction);
					default -> CoalitionService.adminSetLeader(coalition, faction);
				};
				sender.sendMessage(result.message());
			}
			default -> sender.sendMessage("§cUsage: /coalition admin <disband|offensive|add|kick|setleader|cost>");
		}
	}

	private Coalition coalitionArg(CommandSender sender, String[] args, int from, String usage) {
		if (args.length <= from) {
			sender.sendMessage("§cUsage: /coalition admin " + usage);
			return null;
		}
		Coalition coalition = CoalitionService.byName(rest(args, from));
		if (coalition == null) sender.sendMessage(CoalitionService.NO_COALITION);
		return coalition;
	}

	/**
	 * Seat costs from live prestige, next to what an alliance with a median faction
	 * costs, for tuning {@code coalition.cost} in diplomacy.yml.
	 */
	private void cost(CommandSender sender, String[] ids) {
		double median = CoalitionCost.medianPrestige();
		List<Faction> factions = new ArrayList<>();
		if (ids.length == 0) {
			for (Faction faction : FactionManager.factions) {
				if (CoalitionService.isIndependent(faction) && faction.getPrestige() != null && faction.getPrestige() > 0) {
					factions.add(faction);
				}
			}
		} else {
			for (String id : ids) {
				Faction faction = MercenaryCommandManager.resolveFaction(id);
				if (faction == null) sender.sendMessage("§cNo faction " + id + ".");
				else factions.add(faction);
			}
		}
		factions.sort(Comparator.comparingDouble(CoalitionCost::realmPrestige).reversed());
		Faction typical = closestTo(median);
		RelationType ally = RelationLoader.getType("ally");
		sender.sendMessage("§eMedian prestige of active factions: §f" + Math.round(median)
				+ (typical == null ? "" : " §7(ally cost against " + typical.getName() + "§7)"));
		for (Faction faction : factions) {
			String allyCost = typical == null || ally == null || typical == faction ? "-"
					: String.valueOf(Math.round(RelationManager.getDiplomaticCost(faction, typical, ally)));
			sender.sendMessage("§f" + faction.getName() + "§7: prestige " + Math.round(CoalitionCost.realmPrestige(faction))
					+ " realm, r=" + String.format(java.util.Locale.ROOT, "%.2f", CoalitionCost.ratio(faction))
					+ ", seat §f" + Math.round(CoalitionCost.cost(faction, false))
					+ "§7, ally §f" + allyCost);
		}
	}

	private static Faction closestTo(double prestige) {
		Faction closest = null;
		for (Faction faction : FactionManager.factions) {
			if (faction.getPrestige() == null || faction.getPrestige() <= 0) continue;
			if (closest == null || Math.abs(faction.getPrestige() - prestige) < Math.abs(closest.getPrestige() - prestige)) {
				closest = faction;
			}
		}
		return closest;
	}

	private static String rest(String[] args, int from) {
		return String.join(" ", Arrays.copyOfRange(args, from, args.length));
	}
}
