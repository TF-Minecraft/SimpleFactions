package net.tfminecraft.simplefactions.utils;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.admin.StaffAdminCommand;

import net.tfminecraft.simplefactions.espionage.EspionageCommands;

import net.tfminecraft.simplefactions.vehicles.VehicleFactionCommands.VehicleTabCompletions;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.LawLoader;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InviteCommands;
import net.tfminecraft.simplefactions.guild.LeaderlessGuilds;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.MercenaryInviteRequest;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.tiers.admin.TitleAdminCommand;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.laws.LawGroup;

public class TabCompletion implements TabCompleter{

	private boolean eitherCommand(Command cmd) {
		if(cmd.getName().equalsIgnoreCase("faction") || cmd.getName().equalsIgnoreCase("guild")) {
			return true;
		}
		return false;
	}

	private List<String> completeCompany(Player p, String[] args) {
		List<String> completions = new ArrayList<>();
		Guild guild = FactionManager.getGuildByLeader(Members.key(p));
		if(args.length <= 1) {
			if(guild != null && guild.getCompany() == null) completions.add("found");
			if(guild != null && guild.hasCompany()) {
				completions.add("invite");
				completions.add("kick");
				completions.add("expand");
				completions.add("draft");
				completions.add("offer");
			}
			Guild memberGuild = guild != null ? guild : FactionManager.getGuildByMember(Members.key(p));
			if(memberGuild != null && memberGuild.hasCompany()) completions.add("contracts");
			if(RequestManager.getRequest(p) instanceof MercenaryInviteRequest) {
				completions.add("accept");
				completions.add("decline");
			}
			if(Permissions.isAdmin(p)) {
				completions.add("admin");
			}
			return filtered(completions, args.length == 1 ? args[0] : "");
		}
		if(args.length == 2 && args[0].equalsIgnoreCase("admin") && Permissions.isAdmin(p)) {
			completions.add("give");
			completions.add("take");
			completions.add("add");
			return filtered(completions, args[1]);
		}
		if(args.length == 3 && args[0].equalsIgnoreCase("admin") && Permissions.isAdmin(p)
				&& (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("take") || args[1].equalsIgnoreCase("add"))) {
			for(Guild g : FactionManager.getAllGuilds()) {
				if(g == null || g.getCompany() == null) continue;
				if(g.getCompany().getName() != null && !g.getCompany().getName().contains(" ")) {
					completions.add(g.getCompany().getName());
				}
				completions.add(g.getId());
			}
			return filtered(completions, args[2]);
		}
		if(args.length >= 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("add") && Permissions.isAdmin(p)) {
			return Members.completions(onlineCharacters(), java.util.Arrays.copyOfRange(args, 2, args.length));
		}
		if(args.length == 4 && args[0].equalsIgnoreCase("admin") && Permissions.isAdmin(p)) {

			return completions;
		}
		if(args.length >= 2 && args[0].equalsIgnoreCase("kick")) {
			if(guild != null && guild.getCompany() != null) {
				completions.addAll(Members.display(guild.getCompany().getEnlisted()));
			}
			return Members.completions(completions, args);
		}
		if(args.length >= 2 && args[0].equalsIgnoreCase("invite")) {
			if(guild != null && guild.hasCompany()) {
				for(Player online : Bukkit.getOnlinePlayers()) {
					if(Members.key(online) != null && net.tfminecraft.simplefactions.mercenary.company.MercenaryCompanyService
							.canInvite(guild, Members.key(p), Members.key(online)).ok()) {
						completions.add(online.getName());
						completions.add(Members.name(online));
					}
				}
			}
			return Members.completions(completions, args);
		}
		if(args.length >= 2 && args[0].equalsIgnoreCase("offer") && guild != null && guild.hasCompany()) {
			for(net.tfminecraft.simplefactions.objects.Faction f : FactionManager.factions) {
				if(f.getName() != null) completions.add(f.getName());
			}
			return Members.completions(completions, args);
		}
		return completions;
	}

	private List<String> completeMercenaries(String[] args) {
		List<String> completions = new ArrayList<>();
		if(args.length <= 1) {
			completions.add("list");
			completions.add("hire");
			return filtered(completions, args.length == 1 ? args[0] : "");
		}
		if(args.length >= 2 && args[0].equalsIgnoreCase("hire")) {
			for(net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany company
					: net.tfminecraft.simplefactions.mercenary.contract.MercenaryMarket.listing()) {
				if(company.getName() != null) completions.add(company.getName());
			}
			return Members.completions(completions, args);
		}
		return completions;
	}

	/** Names of the characters being played right now. */
	static List<String> onlineCharacters() {
		List<String> names = new ArrayList<>();
		for(Player online : Bukkit.getOnlinePlayers()) {
			String key = Members.key(online);
			if(key != null) names.add(Members.display(key));
		}
		return names;
	}

	/** Drops the subcommand, so a member name that starts at the third word completes like one at the second. */
	private static String[] afterFirst(String[] args) {
		return java.util.Arrays.copyOfRange(args, 1, args.length);
	}

	private List<String> filtered(List<String> completions, String prefix) {
		String lower = prefix == null ? "" : prefix.toLowerCase(java.util.Locale.ROOT);
		return completions.stream().filter(java.util.Objects::nonNull)
				.filter(s -> s.toLowerCase(java.util.Locale.ROOT).startsWith(lower)).distinct().toList();
	}

	private List<String> completeConstructKinds(String[] args) {
		return completeConstructKinds(args, 1);
	}

	private List<String> completeConstructKinds(String[] args, int kindIndex) {
		List<String> completions = new ArrayList<>();
		for (InstallationKind kind : InstallationKind.values()) {
			completions.add(kind.getCommandName());
		}
		if (args.length > kindIndex) {
			String prefix = args[kindIndex].toLowerCase();
			completions.removeIf(kind -> !kind.toLowerCase().startsWith(prefix));
		}
		return completions;
	}

	private List<String> completeDeconstructIds(Player p, String[] args) {
		return completeInstallationIds(p, args.length >= 2 ? args[1] : "", true);
	}

	private List<String> completeTransferVehicleIds(Player p, String[] args) {
		return completeInstallationIds(p, args.length >= 2 ? args[1] : "", false);
	}

	private List<String> completeInstallationIds(Player p, String prefix, boolean includePendingConstruction) {
		List<String> completions = new ArrayList<>();
		Faction f = FactionManager.getByLeader(Members.key(p));
		if (f == null) {
			return completions;
		}
		for (Installation installation : f.getInstallationHandler().getAll()) {
			completions.add(installation.getId());
		}
		if (includePendingConstruction && f.getInstallationHandler().getPendingConstruction() != null) {
			completions.add(f.getInstallationHandler().getPendingConstruction().getId());
		}
		return net.tfminecraft.simplefactions.vehicles.VehicleFactionCommands.VehicleTabCompletions.filter(completions, prefix);
	}

	private List<String> leaderlessCandidates(Guild guild) {
		List<String> names = new ArrayList<>();
		for(String member : guild.getFaction().getMembers()) {
			if(Members.info(member).map(net.tfminecraft.tlibs.character.CharacterInfo::alive).orElse(false)
					&& FactionManager.getGuildByLeader(member) == null) names.add(Members.display(member));
		}
		return names;
	}

    @Override
    public List<String> onTabComplete (CommandSender sender, Command cmd, String label, String[] args){
        return filtered(complete(sender, cmd, args), args.length == 0 ? "" : args[args.length - 1]);
    }

    private List<String> complete(CommandSender sender, Command cmd, String[] args) {
		if(args.length >= 2 && StaffAdminCommand.matches(cmd.getName(), args)) {
			return StaffAdminCommand.complete(sender, cmd.getName(), args);
		}
		if(cmd.getName().equalsIgnoreCase("faction") && args.length < 2 && !(sender instanceof Player)) {
			List<String> completions = new ArrayList<>();
			if(sender.hasPermission(net.tfminecraft.simplefactions.espionage.EspionageConfig.reloadPermission()))
				completions.add("reloadespionage");
			if(Permissions.isAdmin(sender)) {
				completions.addAll(List.of("admin", "addguildbank"));
				if(Cache.provincesEnabled) completions.addAll(List.of(TitleAdminCommand.SUBCOMMAND, "tradegraph"));
			}
			return completions;
		}
		// The console can run /guild admin, and nothing else under /guild.
		if(cmd.getName().equalsIgnoreCase("guild") && args.length < 2 && !(sender instanceof Player)) {
			return Permissions.isAdmin(sender) ? List.of(StaffAdminCommand.SUBCOMMAND) : List.of();
		}
		if(cmd.getName().equalsIgnoreCase("faction") && sender instanceof Player
				&& Permissions.isAdmin(sender) && args.length == 2
				&& (args[0].equalsIgnoreCase("delbank") || args[0].equalsIgnoreCase("startelection")
						|| args[0].equalsIgnoreCase("endelection") || (Cache.provincesEnabled
								&& (args[0].equalsIgnoreCase("unclaim") || args[0].equalsIgnoreCase("forceclaim"))))) {
			return FactionManager.factions.stream().map(Faction::getId).toList();
		}
		if (cmd.getName().equalsIgnoreCase("faction") && args.length >= 2
				&& EspionageCommands.matches(args[0])) {
			return sender instanceof Player player
					? EspionageCommands.complete(player, args) : List.of();
		}
		if(cmd.getName().equalsIgnoreCase("faction") && Permissions.isAdmin(sender)
				&& args.length == 2 && args[0].equalsIgnoreCase("addguildbank")) {
			List<String> completions = new ArrayList<>();
			for(Guild guild : FactionManager.getAllGuilds()) {
				if(guild != null) completions.add(guild.getId());
			}
			return filtered(completions, args[1]);
		}
		if(cmd.getName().equalsIgnoreCase("faction") && args.length >= 2 && args[0].equalsIgnoreCase(TitleAdminCommand.SUBCOMMAND)) {
			if(!Permissions.isAdmin(sender) || !Cache.provincesEnabled) {
				return new ArrayList<>();
			}
			return TitleAdminCommand.complete(args);
		}
		if(cmd.getName().equalsIgnoreCase("company")) {
			if(sender instanceof Player p) {
				return completeCompany(p, args);
			}
			return new ArrayList<>();
		}
		else if(cmd.getName().equalsIgnoreCase("mercenaries")) {
			return completeMercenaries(args);
		}
		else if(cmd.getName().equalsIgnoreCase("guild") && args.length >= 0 && args.length < 2 ) {
			if(sender instanceof Player){
				Player p = (Player) sender;
				List<String> completions = new ArrayList<>();
				completions.add("create");
				completions.add("list");
				Guild ownGuild = FactionManager.getGuildByMember(Members.key(p));
				Guild ledGuild = FactionManager.getGuildByLeader(Members.key(p));
				if(ownGuild != null && ledGuild == null) completions.add("leave");
				if(Permissions.isAdmin(sender) || (ledGuild != null && !ledGuild.isBase())) completions.add("delete");
				completions.add("join");
				completions.add("decline");
				completions.add("menu");
				completions.add("setbank");
				completions.add("deposit");
				completions.add("withdraw");
				if(Cache.provincesEnabled) {
					completions.add("setcapital");
				}
				if(FactionManager.getGuildByLeader(Members.key(p)) != null) {
					completions.add("invite");
					completions.add("kick");
					completions.add("setleader");
					completions.add("rename");
					completions.add("setbanner");
					completions.add("setcolour");
				}
				if(!LeaderlessGuilds.appointable(Members.key(p)).isEmpty()) completions.add("appoint");
				if(LeaderlessGuilds.offeredTo(Members.key(p)) != null) completions.add("lead");
				if(Permissions.isAdmin(sender)) {
					completions.add(StaffAdminCommand.SUBCOMMAND);
					completions.add("dummify");
					completions.add("dummyleader");
					completions.add("forceleader");
					completions.add("forcejoin");
					if(Cache.provincesEnabled) completions.add("forceremovecapital");
				}
				return completions;
			}
		}
		else if(cmd.getName().equalsIgnoreCase("faction")
				&& args.length == 2
				&& args[0].equalsIgnoreCase("construct")) {
			if(!Cache.provincesEnabled) {
				return new ArrayList<>();
			}
			if(sender instanceof Player) {
				Player p = (Player) sender;
				if(FactionManager.getByLeader(Members.key(p)) == null) {
					return new ArrayList<>();
				}
				return completeConstructKinds(args);
			}
		}
		else if (cmd.getName().equalsIgnoreCase("faction")
				&& args.length == 2
				&& args[0].equalsIgnoreCase("upgrade")) {
			if (!Cache.provincesEnabled
					|| !(sender instanceof Player p)
					|| FactionManager.getByLeader(Members.key(p)) == null) {
				return new ArrayList<>();
			}
			return completeInstallationIds(p, args.length >= 2 ? args[1] : "", false);
		}
		else if(cmd.getName().equalsIgnoreCase("faction")
				&& args.length == 2
				&& args[0].equalsIgnoreCase("deconstruct")) {
			if(!Cache.provincesEnabled) {
				return new ArrayList<>();
			}
			if(sender instanceof Player) {
				Player p = (Player) sender;
				return completeDeconstructIds(p, args);
			}
		}
		else if(cmd.getName().equalsIgnoreCase("faction")
				&& args.length >= 2
				&& args[0].equalsIgnoreCase("vehicle")) {
			if(sender instanceof Player) {
				Player p = (Player) sender;
				if(args.length <= 2) {
					return net.tfminecraft.simplefactions.vehicles.VehicleFactionCommands.VehicleTabCompletions.subcommands(
							args.length >= 2 ? args[1] : "");
				}
				if(args.length >= 3 && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("handover"))) {
					List<String> names = new ArrayList<>();
					if(args[1].equalsIgnoreCase("give") && FactionManager.getByLeader(Members.key(p)) == null) return names;
					for(Player online : Bukkit.getOnlinePlayers()) {
						if(online.getUniqueId().equals(p.getUniqueId())) continue;
						names.add(online.getName());
						names.add(Members.name(online));
					}
					return Members.completions(names, afterFirst(args));
				}
				if(args.length == 3 && args[1].equalsIgnoreCase("transfer")) {
					List<String> ids = completeInstallationIds(p, args[2], false);
					String prefix = args[2] == null ? "" : args[2];
					if ("pool".startsWith(prefix.toLowerCase(java.util.Locale.ROOT))) {
						ids.add(0, "pool");
					}
					return ids;
				}
				if(args.length == 4 && args[1].equalsIgnoreCase("maintenance") && args[2].equalsIgnoreCase("pay")) {
					return VehicleTabCompletions.filter(List.of("bank"), args[3]);
				}
				if(args.length == 3 && args[1].equalsIgnoreCase("maintenance")) {
					return net.tfminecraft.simplefactions.vehicles.VehicleFactionCommands.VehicleTabCompletions.maintenanceActions(args[2]);
				}
			}
		}
		else if(cmd.getName().equalsIgnoreCase("faction")
				&& args.length == 2
				&& args[0].equalsIgnoreCase("transfervehicle")) {
			if(sender instanceof Player) {
				Player p = (Player) sender;
				return completeTransferVehicleIds(p, args);
			}
		}
		else if(cmd.getName().equalsIgnoreCase("faction")
				&& args.length == 2
				&& args[0].equalsIgnoreCase("findvehicles")) {
			if(sender instanceof Player) {
				Player p = (Player) sender;
				List<String> choices = completeInstallationIds(p, "", false);
				choices.add(0, "personal");
				if(FactionManager.getByLeader(Members.key(p)) != null) choices.add(0, "pool");
				return VehicleTabCompletions.filter(choices, args[1]);
			}
		}
		else if(cmd.getName().equalsIgnoreCase("faction") && args.length >= 0 && args.length < 2 ) {
			if(sender instanceof Player){
				Player p = (Player) sender;
				List<String> completions = new ArrayList<>();
				completions.add("list");
				Faction ownFaction = FactionManager.getByMember(Members.key(p));
				if(ownFaction != null) {
					if(ownFaction.getBank() != null) completions.add("deposit");
					if(FactionManager.getByLeader(Members.key(p)) == null && !ownFaction.isInGuild(Members.key(p))) completions.add("leave");
				}
				if(FactionManager.getByMember(Members.key(p)) != null) completions.add("menu");
				if(FactionManager.getByMember(Members.key(p)) != null) completions.addAll(List.of("positions", "spymaster", "espionage"));
                if (p.hasPermission(net.tfminecraft.simplefactions.espionage.EspionageConfig.reloadPermission())) completions.add("reloadespionage");
				completions.add("create");
				completions.add("accept");
				completions.add("decline");
				completions.add("join");
				completions.add("vehicle");
				completions.add("findvehicles");
				
				if(FactionManager.getByLeader(Members.key(p)) != null) {
					if(Cache.provincesEnabled) {
						completions.add("claim");
						completions.add("construct");
						completions.add("deconstruct");
						completions.add("upgrade");
						completions.add("unclaim");
						completions.add("setcapital");
					}
					completions.add("transfervehicle");
					completions.add("withdraw");
					completions.add("setbank");
					completions.add("delete");
					completions.add("invite");
					completions.add("kick");
					completions.add("setleader");
					completions.add("rename");
					completions.add("setculture");
					completions.add("setreligion");
					completions.add("setrulingsystem");
					completions.add("setrulertitle");
					completions.add("setbanner");
					completions.add("setcolour");
				}
				if(Permissions.isAdmin(sender)) {
					completions.addAll(List.of("admin", "delete", "forcedelete", "refresh", "delbank"));
					completions.add("addguildbank");
					completions.add("forceleader");
					if(Cache.provincesEnabled) completions.add("forceclaim");
					completions.add("forcejoin");
					completions.add("forcewithdraw");
					completions.add("forceregiment");
					completions.add("addprestigemodifier");
					//completions.add("addwealthmodifier");
					completions.add("getglobalwealth");
					if(Cache.provincesEnabled) {
						completions.add("provincecap");
						completions.add("forceconstruct");
						completions.add("forceupgrade");
						completions.add("queueallnations");
						completions.add("fullregen");
						completions.add("reloadtitles");
						completions.add("destroytitle");
						completions.add("granttitle");
						completions.add("usurp");
						completions.add(TitleAdminCommand.SUBCOMMAND);
						completions.add("tradegraph");
					}
					completions.add("reloadconfigs");
					completions.add("transfersubject");
					completions.add("setrelation");
					completions.add("settreaty");
					completions.add("setpower");
					completions.add("setlaw");
					completions.add("setstance");
					completions.add("startelection");
					completions.add("endelection");
				}
				return completions;
			}
		} else if(eitherCommand(cmd) && args.length == 2 && args[0].equalsIgnoreCase("create")){
			if(sender instanceof Player){
                return List.of();
			}
		} else if(eitherCommand(cmd) && args.length == 2 && args[0].equalsIgnoreCase("setcapital")) {
			if(!Cache.provincesEnabled) {
				return new ArrayList<>();
			}
			if(sender instanceof Player) {
				Player p = (Player) sender;
				Faction f = null;
				if(cmd.getName().equalsIgnoreCase("guild")) {
					Guild g = FactionManager.getGuildByLeader(Members.key(p));
					if(g != null) {
						f = g.getFaction();
					}
				} else {
					f = FactionManager.getByLeader(Members.key(p));
				}
				if(f == null) {
					return new ArrayList<>();
				}
				List<String> completions = new ArrayList<>();
				for(var s : f.getSettlementHandler().getAll()) {
					completions.add(s.getId());
				}
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("delete")){
			if(sender instanceof Player){
				Player p = (Player) sender;
				Faction f = FactionManager.getByLeader(Members.key(p));
				List<String> completions = new ArrayList<String>();
				if(Permissions.isAdmin(sender)) {
					for(Faction target : FactionManager.factions) completions.add(target.getId());
				} else if(f != null) {
					completions.add(f.getId());
				}
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("guild") && args.length == 2 && args[0].equalsIgnoreCase("delete")){
			if(sender instanceof Player){
				Player p = (Player) sender;
				Guild g = FactionManager.getGuildByLeader(Members.key(p));
				List<String> completions = new ArrayList<String>();
				if(g != null && !g.isBase()) {
					completions.add(g.getId());
				}
				if(Permissions.isAdmin(sender)) {
					for(Faction fac : FactionManager.factions) {
						for(Guild guild : fac.getGuildHandler().getGuilds()) {
							if(!completions.contains(guild.getId()) && !guild.isBase()) {
								completions.add(guild.getId());
							}
						}
					}
				}
				return completions;
			}
		} else if(eitherCommand(cmd) && args.length >= 2 && args[0].equalsIgnoreCase("invite")){
			if(sender instanceof Player){
				List<String> completions = new ArrayList<String>();
				for(Player p : Bukkit.getOnlinePlayers()) {
					completions.add(p.getName());
					completions.add(Members.name(p));
				}
				return Members.completions(completions, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("guild") && args.length >= 2 && args[0].equalsIgnoreCase("kick")){
			if(sender instanceof Player){
				Player p = (Player) sender;
				Guild g = FactionManager.getGuildByLeader(Members.key(p));
				List<String> completions = new ArrayList<String>();
				if(g != null && !g.isBase()) {
					for(String member : g.getMembers()) {
						if(!member.equals(g.getLeader())) completions.add(Members.display(member));
					}
				}
				return Members.completions(completions, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length >= 2 && args[0].equalsIgnoreCase("kick")){
			if(sender instanceof Player){
				Player p = (Player) sender;
				Faction f = FactionManager.getByLeader(Members.key(p));
				List<String> completions = new ArrayList<String>();
				if(f != null) {
					for(String member : f.getMembers()) {
						if(!member.equals(f.getLeader()) && !f.isInGuild(member)) completions.add(Members.display(member));
					}
				}
				return Members.completions(completions, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length >= 2 && args[0].equalsIgnoreCase("setleader")){
			if(sender instanceof Player){
				Player p = (Player) sender;
				Faction f = FactionManager.getByMember(Members.key(p));
				List<String> completions = new ArrayList<String>();
				if(f != null) {
					for(String member : f.getMembers()) {
						if(f.canBecomeLeader(member)) completions.add(Members.display(member));
					}
				}
				return Members.completions(completions, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("guild") && args.length >= 2 && args[0].equalsIgnoreCase("setleader")) {
			if(sender instanceof Player p) {
				Guild g = FactionManager.getGuildByLeader(Members.key(p));
				List<String> names = new ArrayList<>();
				if(g != null) for(String member : g.getMembers()) {
					if(!g.isLeader(member)) names.add(Members.display(member));
				}
				return Members.completions(names, args);
			}
		} else if(cmd.getName().equalsIgnoreCase("guild") && args.length >= 3 && args[0].equalsIgnoreCase("appoint")) {
			if(sender instanceof Player p) {
				Guild g = FactionManager.getGuildByString(args[1]);
				if(g != null && LeaderlessGuilds.appointable(Members.key(p)).contains(g)) {
					return Members.completions(leaderlessCandidates(g), afterFirst(args));
				}
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("rename")){
			if(sender instanceof Player){
                return List.of();
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setculture")){
			if(sender instanceof Player){
                return List.of();
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setreligion")){
			if(sender instanceof Player){
                return List.of();
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setrulingsystem")){
			if(sender instanceof Player){
                return List.of();
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setrulertitle")){
			if(sender instanceof Player){
                return List.of();
			}
		} else if(eitherCommand(cmd) && args.length == 2 && args[0].equalsIgnoreCase("setbanner")){
			if(sender instanceof Player){
                return List.of();
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("claim")){
			if(sender instanceof Player){
                return List.of();
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length >= 3 && args[0].equalsIgnoreCase("construct")){
			if(sender instanceof Player){
                return List.of();
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("unclaim")){
			if(sender instanceof Player){
                return List.of();
			}
		} else if(cmd.getName().equalsIgnoreCase("guild") && args.length == 2 && args[0].equalsIgnoreCase("appoint")){
			if(sender instanceof Player p){
				return LeaderlessGuilds.appointable(Members.key(p)).stream().map(Guild::getId)
						.filter(id -> id.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
			}
		} else if(cmd.getName().equalsIgnoreCase("guild") && args.length == 2 && args[0].equalsIgnoreCase("lead")){
			if(sender instanceof Player p){
				Guild offered = LeaderlessGuilds.offeredTo(Members.key(p));
				List<String> completions = new ArrayList<>();
				if (offered != null) completions.add(offered.getId());
				return completions;
			}
		} else if(eitherCommand(cmd) && args.length == 2
				&& (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("decline"))){
			if(sender instanceof Player p){
				List<String> ids = new ArrayList<>(InviteCommands.inviteIds(cmd.getName(), Members.key(p), args[1]));
				if(cmd.getName().equalsIgnoreCase("guild") && args[0].equalsIgnoreCase("decline")) {
					Guild offered = LeaderlessGuilds.offeredTo(Members.key(p));
					if(offered != null) ids.add(offered.getId());
				}
				return ids;
			}
		} else if(eitherCommand(cmd) && args.length == 2 && args[0].equalsIgnoreCase("accept")){
			if(sender instanceof Player){
                return List.of();
			}
		} else if((cmd.getName().equalsIgnoreCase("faction") || cmd.getName().equalsIgnoreCase("guild"))
				&& args.length == 2 && args[0].equalsIgnoreCase("setcolour")){
			if(sender instanceof Player){
                return List.of();
			}
		}
		if(Permissions.isAdmin(sender)) {
			if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("forceleader")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						completions.add(f.getId());
					}
					
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("guild") && args.length == 2 && args[0].equalsIgnoreCase("forceremovecapital")){
				List<String> completions = new ArrayList<String>();
				for(Guild g : FactionManager.getAllGuilds()) {
					if(g != null && !g.isBase() && g.hasCapital() && g.getId().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(g.getId());
				}
				return completions;
			} else if(cmd.getName().equalsIgnoreCase("guild") && args.length >= 2 && args[0].equalsIgnoreCase("forcejoin")){
				if(args.length > 2) {
					Guild g = FactionManager.getGuildByString(args[1]);
					List<String> names = new ArrayList<String>();
					for(Player online : Bukkit.getOnlinePlayers()) {
						String key = Members.key(online);
						if(g != null && key != null && !g.isMember(key)) names.add(Members.display(key));
					}
					return Members.completions(names, afterFirst(args));
				}
				List<String> completions = new ArrayList<String>();
				for(Guild g : FactionManager.getAllGuilds()) {
					if(g != null && !g.isBase() && g.getId().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(g.getId());
				}
				return completions;
			} else if(cmd.getName().equalsIgnoreCase("guild") && args.length == 2 && args[0].equalsIgnoreCase("forceleader")){
				List<String> completions = new ArrayList<String>();
				for(Faction f : FactionManager.factions) {
					for(Guild g : f.getGuildHandler().getGuilds()) {
						if(!g.isBase() && g.getId().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(g.getId());
					}
				}
				return completions;
			} else if(cmd.getName().equalsIgnoreCase("guild") && args.length >= 3 && args[0].equalsIgnoreCase("forceleader")){
				List<String> completions = new ArrayList<String>();
				Guild g = FactionManager.getGuildByString(args[1]);
				if(g != null && !g.isBase()) {
					if(g.isLeaderless()) return Members.completions(leaderlessCandidates(g), afterFirst(args));
					for(String member : g.getMembers()) {
						if(!g.isLeader(member)) completions.add(Members.display(member));
					}
				}
				return Members.completions(completions, afterFirst(args));
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length >= 3 && args[0].equalsIgnoreCase("forceleader")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					
					Faction f = FactionManager.getByString(args[1]);
					if(f != null) {
						for(String member : f.getMembers()) {
							if(f.canBecomeLeader(member)) completions.add(Members.display(member));
						}
					}
					return Members.completions(completions, afterFirst(args));
				}
			} else if (cmd.getName().equalsIgnoreCase("faction")
					&& Cache.provincesEnabled
					&& args.length == 2
					&& args[0].equalsIgnoreCase("forceupgrade")) {
				List<String> completions = new ArrayList<>();
				for (Faction f : FactionManager.factions) {
					completions.add(f.getId());
				}
				return completions;
			} else if (cmd.getName().equalsIgnoreCase("faction")
					&& Cache.provincesEnabled
					&& args.length == 3
					&& args[0].equalsIgnoreCase("forceupgrade")) {
				Faction f = FactionManager.getByString(args[1]);
				List<String> completions = new ArrayList<>();
				if (f != null) {
					for (Installation installation : f.getInstallationHandler().getAll()) {
						completions.add(installation.getId());
					}
				}
				String prefix = args[2].toLowerCase();
				completions.removeIf(id -> !id.toLowerCase().startsWith(prefix));
				return completions;
			} else if(cmd.getName().equalsIgnoreCase("faction")
					&& Cache.provincesEnabled
					&& args.length == 2
					&& args[0].equalsIgnoreCase("forceconstruct")) {
				List<String> completions = new ArrayList<>();
				for(Faction f : FactionManager.factions) {
					completions.add(f.getId());
				}
				return completions;
			} else if(cmd.getName().equalsIgnoreCase("faction")
					&& Cache.provincesEnabled
					&& args.length == 3
					&& args[0].equalsIgnoreCase("forceconstruct")) {
				return completeConstructKinds(args, 2);
			} else if(cmd.getName().equalsIgnoreCase("faction")
					&& Cache.provincesEnabled
					&& args.length >= 4
					&& args[0].equalsIgnoreCase("forceconstruct")) {
                return List.of();
			} else if(cmd.getName().equalsIgnoreCase("faction")
					&& args.length == 2
					&& args[0].equalsIgnoreCase("forceregiment")) {
				List<String> completions = new ArrayList<>();
				for(Faction f : FactionManager.factions) {
					completions.add(f.getId());
				}
				return completions;
			} else if(cmd.getName().equalsIgnoreCase("faction")
					&& args.length == 3
					&& args[0].equalsIgnoreCase("forceregiment")) {
				List<String> completions = new ArrayList<>();
				completions.add("give");
				completions.add("take");
				return completions;
			} else if(cmd.getName().equalsIgnoreCase("faction")
					&& args.length == 4
					&& args[0].equalsIgnoreCase("forceregiment")) {
				List<String> completions = new ArrayList<>();
				Faction f = FactionManager.getByString(args[1]);
				if(f != null) {
					for(net.tfminecraft.simplefactions.army.Regiment r : f.getMilitary().getRegiments()) {
						if(!r.isLevy() && !r.isMercenary()) {
							completions.add(r.getId());
						}
					}
				}
				return completions;
			} else if(cmd.getName().equalsIgnoreCase("faction")
					&& args.length == 5
					&& args[0].equalsIgnoreCase("forceregiment")) {
                return List.of();
			} else if (cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("forcejoin")) {
				List<String> completions = new ArrayList<String>();
				// Suggest faction names
				for (Faction f : FactionManager.factions) {
					if (f.getId().toLowerCase().startsWith(args[1].toLowerCase())) {
						completions.add(f.getId());
					}
				}
				return completions;
			} 
			else if (cmd.getName().equalsIgnoreCase("faction") && args.length == 3 && args[0].equalsIgnoreCase("forcejoin")) {
				List<String> completions = new ArrayList<String>();
				// Suggest players who are valid to be forcejoined
				Faction f = FactionManager.getByString(args[1]);
				if (f != null) {
					for (Player pl : Bukkit.getOnlinePlayers()) {
						String name = pl.getName();
						String key = Members.key(pl);
						if (key != null && name.toLowerCase().startsWith(args[2].toLowerCase())
							&& !f.getMembers().contains(key)
							&& FactionManager.getByMember(key) == null) {
							completions.add(name);
						}
					}
				}
				return completions;
			} if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("forcewithdraw")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						completions.add(f.getId());
					}
					
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 3 && args[0].equalsIgnoreCase("forcewithdraw")){
				if(sender instanceof Player){
                    return List.of();
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("addprestigemodifier")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						completions.add(f.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 3 && args[0].equalsIgnoreCase("addprestigemodifier")){
				if(sender instanceof Player){
                    return List.of();
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 4 && args[0].equalsIgnoreCase("addprestigemodifier")){
				if(sender instanceof Player){
                    return List.of();
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("forcedelete")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						completions.add(f.getId());
					}
					
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("destroytitle")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Title t : TitleManager.getAllOwnedTitles()) {
						completions.add(t.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("granttitle")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						completions.add(f.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 3 && args[0].equalsIgnoreCase("granttitle")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Title t : TitleManager.getAllUnownedTitles()) {
						completions.add(t.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("transfersubject")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						if(RelationManager.getOverlord(f) == null) continue;
						completions.add(f.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 3 && args[0].equalsIgnoreCase("transfersubject")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						if(f.getId().equalsIgnoreCase(args[1])) continue;
						completions.add(f.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setrelation")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						completions.add(f.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 3 && args[0].equalsIgnoreCase("setrelation")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						if(f.getId().equalsIgnoreCase(args[1])) continue;
						completions.add(f.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 4 && args[0].equalsIgnoreCase("setrelation")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(RelationType type : RelationLoader.getDiplomaticTypes()) {
						completions.add(type.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("settreaty")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						completions.add(f.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 3 && args[0].equalsIgnoreCase("settreaty")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						if(f.getId().equalsIgnoreCase(args[1])) continue;
						completions.add(f.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 4 && args[0].equalsIgnoreCase("settreaty")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(RelationType type : RelationLoader.getTreatyTypes()) {
						completions.add(type.getId());
					}
					for(RelationType type : RelationLoader.getPoliticalTreatyTypes()) {
						completions.add(type.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setpower")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						completions.add(f.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setlaw")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						completions.add(f.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 3 && args[0].equalsIgnoreCase("setlaw")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(LawGroup group : LawLoader.getList()) {
						completions.add(group.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 4 && args[0].equalsIgnoreCase("setlaw")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					LawGroup group = LawLoader.getByString(args[2]);
					if(group != null) {
						for(String lawId : group.getLaws().keySet()) {
							completions.add(lawId);
						}
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setstance")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Guild g : FactionManager.getAllGuilds()) {
						completions.add(g.getId());
					}
					for(Faction f : FactionManager.factions) {
						if(!completions.contains(f.getId())) {
							completions.add(f.getId());
						}
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 3 && args[0].equalsIgnoreCase("setstance")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					completions.add("oppose");
					completions.add("neutral");
					completions.add("support");
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("usurp")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						completions.add(f.getId());
					}
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 3 && args[0].equalsIgnoreCase("usurp")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						if(f.getId().equalsIgnoreCase(args[1])) continue;
						if(f.getTitles().size() == 0) continue;
						completions.add(f.getId());
					}
					return completions;
				}
			}
		}
        return List.of();
    }
}
