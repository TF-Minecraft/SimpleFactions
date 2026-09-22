package me.Plugins.SimpleFactions.War.battle.ui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.ui.BattlePermissions;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;

public class BattleTabCompletion implements TabCompleter{

	private boolean isKnownSubcommand(String[] args, String... names) {
		if (args.length == 0) {
			return false;
		}
		for (String name : names) {
			if (args[0].equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

    @Override
    public List<String> onTabComplete (CommandSender sender, Command cmd, String label, String[] args){
    	if(cmd.getName().equalsIgnoreCase("warband") && args.length < 2 &&
        		!isKnownSubcommand(args, "create", "delete", "invite", "kick", "setleader", "list", "toggleopen", "leave", "retreat")){
            if(sender instanceof Player){
                List<String> completions = new ArrayList<>();
                
                completions.add("create");
                completions.add("delete");
                completions.add("invite");
                completions.add("kick");
                completions.add("setleader");
                completions.add("list");
                completions.add("toggleopen");
                completions.add("leave");
                completions.add("retreat");
                return completions;
            }
        } else if(cmd.getName().equalsIgnoreCase("warband") && args.length == 2 && args[0].equalsIgnoreCase("create")){
            if(sender instanceof Player){
            	List<String> completions = new ArrayList<String>();
            	completions.add("<id>");
                
                return completions;
            }
        } else if(cmd.getName().equalsIgnoreCase("warband") && args.length == 2 && args[0].equalsIgnoreCase("delete")){
            if(sender instanceof Player){
            	Player p = (Player) sender;
            	Warband w = WarbandManager.getByLeader(p);
            	List<String> completions = new ArrayList<String>();
            	if(w != null) {
            		completions.add(w.getId());
            	}
                return completions;
            }
        } else if(cmd.getName().equalsIgnoreCase("warband") && args.length == 2 && args[0].equalsIgnoreCase("invite")){
            if(sender instanceof Player){
            	List<String> completions = new ArrayList<String>();
            	for(Player p : Bukkit.getOnlinePlayers()) {
            		completions.add(p.getName());
            	}
                return completions;
            }
        } else if(cmd.getName().equalsIgnoreCase("warband") && args.length == 2 && args[0].equalsIgnoreCase("kick")){
            if(sender instanceof Player){
            	Player p = (Player) sender;
            	Warband w = WarbandManager.getByLeader(p);
            	List<String> completions = new ArrayList<String>();
            	if(w != null) {
            		for(Player m : w.getPlayers()) {
            			if(m.equals(w.getLeader())) continue;
            			completions.add(m.getName());
            		}
            	}
                return completions;
            }
        } else if(cmd.getName().equalsIgnoreCase("warband") && args.length == 2 && args[0].equalsIgnoreCase("setleader")){
            if(sender instanceof Player){
            	Player p = (Player) sender;
            	Warband w = WarbandManager.getByLeader(p);
            	List<String> completions = new ArrayList<String>();
            	if(w != null) {
            		for(Player m : w.getPlayers()) {
            			if(m.equals(w.getLeader())) continue;
            			completions.add(m.getName());
            		}
            	}
                return completions;
            }
        }
    	if(cmd.getName().equalsIgnoreCase("battle") && args.length < 2 &&
        		!isKnownSubcommand(args, "create", "edit", "delete", "addside", "addpoint", "setlives", "setspawn",
        				"setjail", "setcontestmin", "setcontestmax", "setcontestduration", "setraidtarget",
        				"setdefenderlives", "list", "join")){
            if(sender instanceof Player){
                List<String> completions = new ArrayList<>();
                completions.add("list");
                completions.add("join");
                if(BattlePermissions.isAdmin(sender)) {
	                completions.add("create");
	                completions.add("edit");
	                completions.add("delete");
	                completions.add("addside");
	                completions.add("addpoint");
	                completions.add("setlives");
	                completions.add("setspawn");
	                completions.add("setjail");
	                completions.add("setcontestmin");
	                completions.add("setcontestmax");
	                completions.add("setcontestduration");
	                completions.add("setraidtarget");
	                completions.add("setdefenderlives");
                }
                return completions;
            }
        } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 2 && args[0].equalsIgnoreCase("join")) {
        	List<String> completions = new ArrayList<>();
        	for(Battle b : BattleManager.get()) {
        		completions.add(b.getId());
        	}
        	return completions;
        } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 3 && args[0].equalsIgnoreCase("join")) {
        	Battle b = BattleManager.getByString(args[1]);
        	if (b == null) {
        		return null;
        	}
        	List<String> completions = new ArrayList<>();
        	for(BattleSide s : b.getSides()) {
        		completions.add(s.getId());
        	}
        	return completions;
        }
    	if(BattlePermissions.isAdmin(sender)) {
        	if(cmd.getName().equalsIgnoreCase("battle") && args.length == 2 && args[0].equalsIgnoreCase("create")) {
        		List<String> completions = new ArrayList<>();
        		completions.add("field");
        		completions.add("siege");
        		completions.add("raid");
        		return completions;
        	} else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 3 && args[0].equalsIgnoreCase("create")) {
        		List<String> completions = new ArrayList<>();
        		completions.add("<battleId>");
        		return completions;
        	} else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 2 && 
            		!(args[0].equalsIgnoreCase("create")
            				|| args[0].equalsIgnoreCase("edit") 
            				|| args[0].equalsIgnoreCase("addside") 
            				|| args[0].equalsIgnoreCase("addpoint") 
            				|| args[0].equalsIgnoreCase("setlives") 
            				|| args[0].equalsIgnoreCase("setspawn") 
            				|| args[0].equalsIgnoreCase("setjail")
            				|| args[0].equalsIgnoreCase("setcontestmin")
            				|| args[0].equalsIgnoreCase("setcontestmax")
            				|| args[0].equalsIgnoreCase("setcontestduration")
            				|| args[0].equalsIgnoreCase("setraidtarget")
            				|| args[0].equalsIgnoreCase("setdefenderlives")
            		)){
                if(sender instanceof Player){
                    List<String> completions = new ArrayList<>();
                    for(Battle b : BattleManager.get()) {
                    	completions.add(b.getId());
                    }
                    return completions;
                }
            } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            	List<String> completions = new ArrayList<>();
            	for(Battle b : BattleManager.get()) {
            		completions.add(b.getId());
            	}
            	return completions;
            } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            	List<String> completions = new ArrayList<>();
            	for(Battle b : BattleManager.get()) {
            		completions.add(b.getId());
            	}
            	return completions;
            } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 3 && 
            		!(args[0].equalsIgnoreCase("create")
            				|| args[0].equalsIgnoreCase("setspawn") 
            				|| args[0].equalsIgnoreCase("setjail") 
            		)){
                if(sender instanceof Player){
                    List<String> completions = new ArrayList<>();
                    Battle b = BattleManager.getByString(args[1]);
                    if(b != null) {
                    	for(BattleSide s : b.getSides()) {
                        	completions.add(s.getId());
                        }
                    }
                    return completions;
                }
            } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 3 && args[0].equalsIgnoreCase("setlives")){
                if(sender instanceof Player){
                	List<String> completions = new ArrayList<String>();
                	completions.add("5");
                	completions.add("10");
                	completions.add("20");
                	completions.add("50");
                    return completions;
                }
            } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 3 && args[0].equalsIgnoreCase("addpoint")){
                if(sender instanceof Player){
                	List<String> completions = new ArrayList<String>();
                	Battle b = BattleManager.getByString(args[1]);
                    if(b != null) {
                    	for(BattleSide s : b.getSides()) {
                        	completions.add(s.getId());
                        }
                    }
                    return completions;
                }
            } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 3 && 
            		(args[0].equalsIgnoreCase("setspawn") || args[0].equalsIgnoreCase("setjail"))) {
            	Battle b = BattleManager.getByString(args[1]);
            	if (b == null) {
            		return null;
            	}
            	List<String> completions = new ArrayList<>();
            	for(BattleSide s : b.getSides()) {
            		completions.add(s.getId());
            	}
            	return completions;
            } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 3 && args[0].equalsIgnoreCase("setcontestduration")){
            	List<String> completions = new ArrayList<>();
            	completions.add("60");
            	completions.add("120");
            	completions.add("180");
            	completions.add("240");
            	completions.add("300");
            	return completions;
            } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 3 && args[0].equalsIgnoreCase("setdefenderlives")){
            	List<String> completions = new ArrayList<>();
            	completions.add("5");
            	completions.add("10");
            	completions.add("20");
            	completions.add("25");
            	completions.add("50");
            	return completions;
            }
        }
        return null;
    }
}
