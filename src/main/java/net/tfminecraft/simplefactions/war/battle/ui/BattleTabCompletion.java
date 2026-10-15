package net.tfminecraft.simplefactions.war.battle.ui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.ui.BattlePermissions;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;

public class BattleTabCompletion implements TabCompleter{

	private boolean isKnownSubcommand(String action, String... names) {
		for (String name : names) {
			if (action.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

    @Override
    public List<String> onTabComplete (CommandSender sender, Command cmd, String label, String[] args){
        String prefix = args.length == 0 ? "" : args[args.length - 1];
        return complete(sender, cmd, args).stream()
                .filter(value -> value.regionMatches(true, 0, prefix, 0, prefix.length())).distinct().toList();
    }

    private List<String> complete(CommandSender sender, Command cmd, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if(cmd.getName().equalsIgnoreCase("warband") && args.length < 2) {
            if(sender instanceof Player){
                List<String> completions = new ArrayList<>();
                
                if(WarbandManager.getByMemberId(((Player) sender).getUniqueId()) == null && Members.key((Player) sender) != null)
                    completions.add("join");
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
                return List.of();
            }
        } else if(cmd.getName().equalsIgnoreCase("warband") && args.length == 2 && args[0].equalsIgnoreCase("delete")){
            if(sender instanceof Player){
            	Player p = (Player) sender;
            	Warband w = WarbandManager.getByLeader(p);
            	List<String> completions = new ArrayList<String>();
                if(BattlePermissions.isAdmin(sender)) {
                    for(Warband target : WarbandManager.get()) completions.add(target.getId());
                } else if(w != null && !w.isFaction()) {
            		completions.add(w.getId());
            	}
                return completions;
            }
        } else if(cmd.getName().equalsIgnoreCase("warband") && args.length == 2 && args[0].equalsIgnoreCase("join")) {
            Player player = (Player) sender;
            if(WarbandManager.getByMemberId(player.getUniqueId()) != null || Members.key(player) == null) return List.of();
            var faction = FactionManager.getByMember(Members.key(player));
            List<String> ids = new ArrayList<>();
            for(Warband band : WarbandManager.get()) {
                if(band.isFaction() ? faction == null : band.isLocked() && !band.isInvited(player)) continue;
                if(CampaignWarbandSignupService.signupFailure(player.getUniqueId(), Members.key(player),
                        band, faction, player, CampaignClock.now()) == null) ids.add(band.getId());
            }
            return ids;
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
        if(cmd.getName().equalsIgnoreCase("battle") && args.length < 2) {
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
                return List.of();
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
                return List.of();
        	} else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 2 && 
                isKnownSubcommand(args[0], "edit", "delete", "addside", "addpoint", "setlives",
                    "setspawn", "setjail", "setcontestmin", "setcontestmax", "setcontestduration",
                    "setraidtarget", "setdefenderlives")) {
                if(sender instanceof Player){
                    List<String> completions = new ArrayList<>();
                    for(Battle b : BattleManager.get()) {
                    	completions.add(b.getId());
                    }
                    return completions;
                }
            } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 3 && args[0].equalsIgnoreCase("addside")) {
                return List.of();
            } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 3 && args[0].equalsIgnoreCase("setlives")){
                if(sender instanceof Player){
                    return List.of();
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
                    return List.of();
            	}
            	List<String> completions = new ArrayList<>();
            	for(BattleSide s : b.getSides()) {
            		completions.add(s.getId());
            	}
            	return completions;
            } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 3 && args[0].equalsIgnoreCase("setcontestduration")){
                return List.of();
            } else if(cmd.getName().equalsIgnoreCase("battle") && args.length == 3 && args[0].equalsIgnoreCase("setdefenderlives")){
                return List.of();
            }
        }
        return List.of();
    }
}
