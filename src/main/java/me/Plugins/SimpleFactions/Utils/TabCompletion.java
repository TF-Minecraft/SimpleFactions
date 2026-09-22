package me.Plugins.SimpleFactions.Utils;


import me.Plugins.SimpleFactions.vehicles.VehicleFactionCommands.VehicleTabCompletions;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.LawLoader;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.RequestManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Request.MercenaryInviteRequest;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.laws.LawGroup;

public class TabCompletion implements TabCompleter{

	private boolean eitherCommand(Command cmd) {
		if(cmd.getName().equalsIgnoreCase("faction") || cmd.getName().equalsIgnoreCase("guild")) {
			return true;
		}
		return false;
	}

	private List<String> completeCompany(Player p, String[] args) {
		List<String> completions = new ArrayList<>();
		Guild guild = FactionManager.getGuildByLeader(p.getName());
		if(args.length <= 1) {
			if(guild != null && guild.getCompany() == null) completions.add("found");
			if(guild != null && guild.hasCompany()) {
				completions.add("invite");
				completions.add("kick");
				completions.add("expand");
				completions.add("draft");
				completions.add("offer");
				completions.add("contracts");
			}
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
			return filtered(completions, args[1]);
		}
		if(args.length == 3 && args[0].equalsIgnoreCase("admin") && Permissions.isAdmin(p)) {
			for(Guild g : FactionManager.getAllGuilds()) {
				if(g == null || g.getCompany() == null) continue;
				if(g.getCompany().getName() != null) {
					completions.add(g.getCompany().getName());
				}
				completions.add(g.getId());
			}
			return filtered(completions, args[2]);
		}
		if(args.length == 4 && args[0].equalsIgnoreCase("admin") && Permissions.isAdmin(p)) {
			completions.add("1");
			return completions;
		}
		if(args.length == 2 && args[0].equalsIgnoreCase("kick")) {
			if(guild != null && guild.getCompany() != null) {
				completions.addAll(guild.getCompany().getEnlisted());
			}
			return filtered(completions, args[1]);
		}
		if(args.length == 2 && args[0].equalsIgnoreCase("invite")) {
			for(Player online : Bukkit.getOnlinePlayers()) {
				completions.add(online.getName());
			}
			return filtered(completions, args[1]);
		}
		if(args.length == 2 && args[0].equalsIgnoreCase("offer")) {
			for(me.Plugins.SimpleFactions.Objects.Faction f : FactionManager.factions) {
				if(f.getName() != null) completions.add(f.getName());
			}
			return filtered(completions, args[1]);
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
		if(args.length == 2 && args[0].equalsIgnoreCase("hire")) {
			for(me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany company
					: me.Plugins.SimpleFactions.mercenary.contract.MercenaryMarket.listing()) {
				if(company.getName() != null) completions.add(company.getName());
			}
			return filtered(completions, args[1]);
		}
		return completions;
	}

	private List<String> filtered(List<String> completions, String prefix) {
		String lower = prefix == null ? "" : prefix.toLowerCase();
		completions.removeIf(s -> !s.toLowerCase().startsWith(lower));
		return completions;
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
		Faction f = FactionManager.getByLeader(p.getName());
		if (f == null) {
			return completions;
		}
		for (Installation installation : f.getInstallationHandler().getAll()) {
			completions.add(installation.getId());
		}
		if (includePendingConstruction && f.getInstallationHandler().getPendingConstruction() != null) {
			completions.add(f.getInstallationHandler().getPendingConstruction().getId());
		}
		return me.Plugins.SimpleFactions.vehicles.VehicleFactionCommands.VehicleTabCompletions.filter(completions, prefix);
	}

    @Override
    public List<String> onTabComplete (CommandSender sender, Command cmd, String label, String[] args){
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
				if(FactionManager.getGuildByMember(p.getName()) != null) completions.add("menu");
				completions.add("create");
				completions.add("join");
				completions.add("menu");
				completions.add("setbank");
				completions.add("deposit");
				completions.add("withdraw");
				if(Cache.provincesEnabled) {
					completions.add("setcapital");
				}
				if(FactionManager.getGuildByLeader(p.getName()) != null) {
					completions.add("invite");
					completions.add("setleader");
					completions.add("rename");
					completions.add("setbanner");
				}
				if(Permissions.isAdmin(sender)) {
					completions.add("dummify");
					completions.add("dummyleader");
				}
				return completions;
			}
		}
		else if(cmd.getName().equalsIgnoreCase("faction")
				&& args.length >= 1
				&& args.length <= 2
				&& args[0].equalsIgnoreCase("construct")) {
			if(!Cache.provincesEnabled) {
				return new ArrayList<>();
			}
			if(sender instanceof Player) {
				Player p = (Player) sender;
				if(FactionManager.getByLeader(p.getName()) == null) {
					return new ArrayList<>();
				}
				return completeConstructKinds(args);
			}
		}
		else if(cmd.getName().equalsIgnoreCase("faction")
				&& args.length >= 1
				&& args.length <= 2
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
				&& args.length >= 1
				&& args[0].equalsIgnoreCase("vehicle")) {
			if(sender instanceof Player) {
				Player p = (Player) sender;
				if(args.length <= 2) {
					return me.Plugins.SimpleFactions.vehicles.VehicleFactionCommands.VehicleTabCompletions.subcommands(
							args.length >= 2 ? args[1] : "");
				}
				if(args.length == 3 && args[1].equalsIgnoreCase("transfer")) {
					return completeInstallationIds(p, args[2], false);
				}
				if(args.length == 3 && args[1].equalsIgnoreCase("maintenance")) {
					return me.Plugins.SimpleFactions.vehicles.VehicleFactionCommands.VehicleTabCompletions.maintenanceActions(args[2]);
				}
			}
		}
		else if(cmd.getName().equalsIgnoreCase("faction")
				&& args.length >= 1
				&& args.length <= 2
				&& args[0].equalsIgnoreCase("transfervehicle")) {
			if(sender instanceof Player) {
				Player p = (Player) sender;
				return completeTransferVehicleIds(p, args);
			}
		}
		else if(cmd.getName().equalsIgnoreCase("faction")
				&& args.length >= 1
				&& args.length <= 2
				&& args[0].equalsIgnoreCase("findvehicles")) {
			if(sender instanceof Player) {
				Player p = (Player) sender;
				return completeTransferVehicleIds(p, args);
			}
		}
		else if(cmd.getName().equalsIgnoreCase("faction") && args.length >= 0 && args.length < 2 ) {
			if(sender instanceof Player){
				Player p = (Player) sender;
				List<String> completions = new ArrayList<>();
				completions.add("list");
				if(FactionManager.getByMember(p.getName()) != null) completions.add("menu");
				completions.add("create");
				completions.add("accept");
				completions.add("join");
				
				if(FactionManager.getByLeader(p.getName()) != null) {
					if(Cache.provincesEnabled) {
						completions.add("claim");
						completions.add("construct");
						completions.add("deconstruct");
						completions.add("unclaim");
						completions.add("setcapital");
					}
					completions.add("vehicle");
					completions.add("transfervehicle");
					completions.add("findvehicles");
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
					completions.add("forceleader");
					completions.add("forcejoin");
					completions.add("forcewithdraw");
					completions.add("forceregiment");
					completions.add("addprestigemodifier");
					//completions.add("addwealthmodifier");
					completions.add("getglobalwealth");
					if(Cache.provincesEnabled) {
						completions.add("forceconstruct");
						completions.add("queueallnations");
						completions.add("fullregen");
						completions.add("reloadtitles");
						completions.add("destroytitle");
						completions.add("granttitle");
						completions.add("usurp");
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
				List<String> completions = new ArrayList<String>();
				completions.add("<id>");
				
				return completions;
			}
		} else if(eitherCommand(cmd) && args.length == 2 && args[0].equalsIgnoreCase("setcapital")) {
			if(!Cache.provincesEnabled) {
				return new ArrayList<>();
			}
			if(sender instanceof Player) {
				Player p = (Player) sender;
				Faction f = null;
				if(cmd.getName().equalsIgnoreCase("guild")) {
					Guild g = FactionManager.getGuildByLeader(p.getName());
					if(g != null) {
						f = g.getFaction();
					}
				} else {
					f = FactionManager.getByLeader(p.getName());
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
				Faction f = FactionManager.getByMember(p.getName());
				List<String> completions = new ArrayList<String>();
				if(f != null) {
					completions.add(f.getId());
				}
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("guild") && args.length == 2 && args[0].equalsIgnoreCase("delete")){
			if(sender instanceof Player){
				Player p = (Player) sender;
				Guild g = FactionManager.getGuildByMember(p.getName());
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
		} else if(eitherCommand(cmd) && args.length == 2 && args[0].equalsIgnoreCase("invite")){
			if(sender instanceof Player){
				List<String> completions = new ArrayList<String>();
				for(Player p : Bukkit.getOnlinePlayers()) {
					completions.add(p.getName());
				}
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("kick")){
			if(sender instanceof Player){
				Player p = (Player) sender;
				Faction f = FactionManager.getByMember(p.getName());
				List<String> completions = new ArrayList<String>();
				if(f != null) {
					completions.addAll(f.getMembers());
					completions.remove(f.getLeader());
				}
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setleader")){
			if(sender instanceof Player){
				Player p = (Player) sender;
				Faction f = FactionManager.getByMember(p.getName());
				List<String> completions = new ArrayList<String>();
				if(f != null) {
					for(String member : f.getMembers()) {
						if(f.canBecomeLeader(member)) completions.add(member);
					}
				}
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("rename")){
			if(sender instanceof Player){
				List<String> completions = new ArrayList<String>();
				completions.add("<id>");
				
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setculture")){
			if(sender instanceof Player){
				List<String> completions = new ArrayList<String>();
				completions.add("<culture>");
				
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setreligion")){
			if(sender instanceof Player){
				List<String> completions = new ArrayList<String>();
				completions.add("<religion>");
				
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setrulingsystem")){
			if(sender instanceof Player){
				List<String> completions = new ArrayList<String>();
				completions.add("<ruling system>");
				
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setrulertitle")){
			if(sender instanceof Player){
				List<String> completions = new ArrayList<String>();
				completions.add("<ruler title>");
				
				return completions;
			}
		} else if(eitherCommand(cmd) && args.length == 2 && args[0].equalsIgnoreCase("setbanner")){
			if(sender instanceof Player){
				List<String> completions = new ArrayList<String>();
				
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("claim")){
			if(sender instanceof Player){
				List<String> completions = new ArrayList<String>();
				
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length >= 3 && args[0].equalsIgnoreCase("construct")){
			if(sender instanceof Player){
				List<String> completions = new ArrayList<>();
				completions.add("<name>");
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("unclaim")){
			if(sender instanceof Player){
				List<String> completions = new ArrayList<String>();
				
				return completions;
			}
		} else if(eitherCommand(cmd) && args.length == 2 && args[0].equalsIgnoreCase("accept")){
			if(sender instanceof Player){
				List<String> completions = new ArrayList<String>();
				
				return completions;
			}
		} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("setcolour")){
			if(sender instanceof Player){
				List<String> completions = new ArrayList<String>();
				completions.add("R,G,B");
				return completions;
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
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 3 && args[0].equalsIgnoreCase("forceleader")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					
					Faction f = FactionManager.getByString(args[1]);
					if(f != null) {
						for(String member : f.getMembers()) {
							if(f.canBecomeLeader(member)) completions.add(member);
						}
					}
					return completions;
				}
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
				List<String> completions = new ArrayList<>();
				completions.add("<name>");
				return completions;
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
					for(me.Plugins.SimpleFactions.Army.Regiment r : f.getMilitary().getRegiments()) {
						if(!r.isLevy()) {
							completions.add(r.getId());
						}
					}
				}
				return completions;
			} else if(cmd.getName().equalsIgnoreCase("faction")
					&& args.length == 5
					&& args[0].equalsIgnoreCase("forceregiment")) {
				List<String> completions = new ArrayList<>();
				completions.add("1");
				return completions;
			} else if (args.length == 2 && args[0].equalsIgnoreCase("forcejoin")) {
				List<String> completions = new ArrayList<String>();
				// Suggest faction names
				for (Faction f : FactionManager.factions) {
					if (f.getId().toLowerCase().startsWith(args[1].toLowerCase())) {
						completions.add(f.getId());
					}
				}
				return completions;
			} 
			else if (args.length == 3 && args[0].equalsIgnoreCase("forcejoin")) {
				List<String> completions = new ArrayList<String>();
				// Suggest players who are valid to be forcejoined
				Faction f = FactionManager.getByString(args[1]);
				if (f != null) {
					for (Player pl : Bukkit.getOnlinePlayers()) {
						String name = pl.getName();
						if (name.toLowerCase().startsWith(args[2].toLowerCase())
							&& !f.getMembers().contains(name)
							&& FactionManager.getByMember(name) == null) {
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
					List<String> completions = new ArrayList<String>();
					completions.add("1.0");
					return completions;
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
					List<String> completions = new ArrayList<String>();
					completions.add("<type>");
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 4 && args[0].equalsIgnoreCase("addprestigemodifier")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					completions.add("1.0");
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("forcedelete")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					for(Faction f : FactionManager.factions) {
						completions.add(f.getId());
					}
					
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("addwealthmodifier")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					completions.add("<type>");
					
					return completions;
				}
			} else if(cmd.getName().equalsIgnoreCase("faction") && args.length == 3 && args[0].equalsIgnoreCase("addwealthmodifier")){
				if(sender instanceof Player){
					List<String> completions = new ArrayList<String>();
					completions.add("<amount>");
					
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
    	return null;
    }
}
