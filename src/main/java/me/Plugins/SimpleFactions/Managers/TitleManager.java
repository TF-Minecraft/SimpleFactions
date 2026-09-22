package me.Plugins.SimpleFactions.Managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.WordUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Loaders.TierLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Tiers.Tier;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.Utils.RandomRGB;

public class TitleManager implements Listener{
	Formatter format = new Formatter();
	public static HashMap<Player, Tier> isFormingTitle = new HashMap<>();

	public static boolean overProvinceCap(Faction f) {
		return f.getPrestige() <  Math.max(0, f.getProvinces().size()-1)*Cache.provinceCost;
	}
	
	@EventHandler
	public void formTitle(AsyncPlayerChatEvent e) {
		Player p = e.getPlayer();
		if(!isFormingTitle.containsKey(p)) return;
		e.setCancelled(true);
		Faction f = FactionManager.getByLeader(p.getName());
		if(f == null) {
			isFormingTitle.remove(p);
			return;
		}
		
		String message = e.getMessage().replace(" ", "_");
		Tier tier = isFormingTitle.get(p);
		
		List<String> titleStringList = new ArrayList<>();
		Tier lower = TierLoader.getByLevel(tier.getTier()-1);
		if(lower != null) {
			for(Title title : f.getFreeTitles(lower)) {
				titleStringList.add(title.getId());
			}
		}
		String id = format.formatId(message);
		if(TitleLoader.getById(id) != null) {
			p.sendMessage("§cA title with that ID already exists!");
			isFormingTitle.remove(p);
			return;
		}
		String name = WordUtils.capitalize(format.formatId(message).replace("_", " "));
		Title newTitle = TitleLoader.createNewTitle(tier, id, name, RandomRGB.similarButDistinct(f.getRGB()), f.getUntitledProvinces(), titleStringList, false);
		if(newTitle == null) {
			isFormingTitle.remove(p);
			return;
		}
		p.sendMessage("§aFormed the "+tier.getName()+" §7"+name);
		f.addTitle(newTitle);
		FactionManager.getMap().enqueue(tier.getId(), newTitle.getRgb());
		isFormingTitle.remove(p);
	}
	
	public static double getClaimCost(Faction f) {
	    return f.getProvinces().size()*Cache.provinceCost;
	}

	
	public static int getRealmSize(Faction f) {
		int added = 0;
		List<Faction> subjects = RelationManager.getSubjects(f);
		if(subjects.size() > 0) {
			for(Faction s : subjects) {
				if (s != null) {
				    added += getRealmSize(s);
				}
			}
		}
		return f.getProvinces().size()+added;
	}

	public static Faction getByProvince(int province) {
		for(Faction f : FactionManager.factions) {
			if(f.hasProvince(province)) return f;
		}
		return null;
	}
	
	public static List<Integer> getProvinces(Faction f) {
	    List<Integer> allProvinces = new ArrayList<>(f.getProvinces());

	    List<Faction> subjects = RelationManager.getSubjects(f);
	    for (Faction s : subjects) {
	        if (s != null) {
	            allProvinces.addAll(getProvinces(s));
	        }
	    }

	    return allProvinces;
	}
	
	public static List<Integer> getAllUntitledProvinces(Faction f){
		List<Integer> provinces = new ArrayList<>();
		for(int p : getProvinces(f)) {
			if(TitleLoader.getByProvince(p) == null) provinces.add(p);
		}
		for(Faction subject : RelationManager.getSubjects(f)) {
			for(int p : subject.getProvinces()) {
				if(TitleLoader.getByProvince(p) == null) provinces.add(p);
			}
		}
		return provinces;
	}
	
	public static List<Integer> getProvinces(Title t) {
	    List<Integer> allProvinces = new ArrayList<>(t.getProvinces());

	    List<String> titles = t.getTitles();
	    for (String s : titles) {
	        Title lower = TitleLoader.getById(s);
	        if (lower != null) {
	            allProvinces.addAll(getProvinces(lower));
	        }
	    }

	    return allProvinces;
	}
	
	public static List<Title> getTitles(Faction f) {
	    List<Title> all = new ArrayList<>(f.getTitles());

	    List<Faction> subjects = RelationManager.getSubjects(f);
	    for (Faction s : subjects) {
	        if (s != null) {
	            all.addAll(getTitles(s));
	        }
	    }

	    return all;
	}

	public static Faction getOwner(Title title){
		for(Faction f : FactionManager.factions) {
			if(getTitles(f).contains(title)) return f;
		}
		return null;
	}

	public static List<Title> getAllOwnedTitles(){
		List<Title> list = new ArrayList<>();
		for(Title t : TitleLoader.getTitles()){
			if(getOwner(t) != null) list.add(t);
		}
		return list;
	}
	public static List<Title> getAllUnownedTitles(){
		List<Title> list = new ArrayList<>();
		for(Title t : TitleLoader.getTitles()){
			if(getOwner(t) == null) list.add(t);
		}
		return list;
	}
	
	public static List<Title> getGrantableTitles(Faction sender, Faction reciever, Tier tier){
		List<Title> titles = new ArrayList<>();
		for(Title t : sender.getTitles()) {
			if(!t.getTier().getId().equalsIgnoreCase(tier.getId())) continue;
			if(!t.canGrant(sender)) continue;
			if(!t.canBeHeld(reciever)) continue;
			titles.add(t);
		}
		return titles;
	}

	public static boolean titleIsInRealm(Faction f, String title){
		for(Title t : getTitles(f)){
			if(t.getId().equalsIgnoreCase(title)) return true;
		}
		return false;
	}
	
	public static Title getParent(Title t) {
		for(Title c : TitleLoader.getTitles()) {
			for(String s : c.getTitles()) {
				if(TitleLoader.getById(s).equals(t)) return c;
			}
		}
		return null;
	}
}
