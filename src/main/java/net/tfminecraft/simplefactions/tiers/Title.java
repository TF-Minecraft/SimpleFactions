package net.tfminecraft.simplefactions.tiers;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class Title {
	private Tier tier;
	private String id;
	private String name;
	private String rgb;
	private boolean titleComplete = false;
	
	private boolean composite = false;
	private List<Integer> provinces = new ArrayList<>();
	
	private List<String> titles = new ArrayList<>();
	
	public Title(Tier tier, String id, JsonObject json) {
		this.tier = tier;
	    this.id = id; // Use the key from the JSON map

	    if (json.has("name")) this.name = json.get("name").getAsString();
	    if (json.has("rgb")) this.rgb = json.get("rgb").getAsString();

	    if (json.has("provinces")) {
	        JsonArray provinceArray = json.getAsJsonArray("provinces");
	        for (JsonElement el : provinceArray) {
	            this.provinces.add(el.getAsInt());
	        }
	        this.composite = false;
	    }

	    if (json.has("titles")) {
	        JsonArray titleArray = json.getAsJsonArray("titles");
	        for (JsonElement el : titleArray) {
	            this.titles.add(el.getAsString());
	        }
	        this.composite = true;
	    }
	    
	    if (json.has("title-complete")) this.titleComplete = Boolean.parseBoolean(json.get("title-complete").getAsString());

	}
	
	public Tier getTier() {
		return tier;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getRgb() {
		return rgb;
	}
	
	public boolean isTitleComplete() {
		return titleComplete;
	}

	public boolean isComposite() {
		return composite;
	}

	public List<Integer> getProvinces() {
		return provinces;
	}

	public List<String> getTitles() {
		return titles;
	}
	
	public boolean canGrant(Faction f) {
		return f.getTitles(tier).size() >= 2;
	}
	
	public void destroy(Faction f, List<Integer> ownedProvinces, List<Title> ownedTitles) {
		if(!ownedTitles.contains(this)) return;
		if(canBeHeld(f, ownedProvinces, ownedTitles)) return;
		Title parent = TitleManager.getParent(this);
		ownedTitles.remove(this);
		f.removeTitle(this);
		Player p = Bukkit.getPlayerExact(f.getLeader());
		if(p != null && p.isOnline()) {
			p.sendMessage("§cYou lost the title §e"+name+" §cdue to a lack of provinces or required titles");
		}
		if(parent != null) parent.destroy(f, ownedProvinces, ownedTitles);
	}
	
	public boolean canBeHeld(Faction f) {
		List<Integer> ownedProvinces = TitleManager.getProvinces(f);
		List<Title> ownedTitles = TitleManager.getTitles(f);
		return canBeHeld(f, ownedProvinces, ownedTitles);
	}
	
	public boolean canBeHeld(Faction f, List<Integer> ownedProvinces, List<Title> ownedTitles) {
		int current = getCurrentAmount(f, ownedProvinces, ownedTitles);
		if(current <= 0) return false;
		if(tier.getTier() == 2 && f.getTitles(tier).size() > f.getMembers().size()) return false;
		return true;
	}
	
	public boolean canBeCreatedBy(Faction f, List<Integer> ownedProvinces, List<Title> ownedTitles, double deJure) {
	    int titleCount = getCurrentAmount(f, ownedProvinces, ownedTitles);
	    int requiredTitles = getDeJureNeeded(deJure);
	    int ownedNestedProvinces = nestedProvinceCheck(ownedProvinces, TitleManager.getProvinces(this));
	    int requiredProvinces = compositeNeeded(deJure);
		if(tier.getTier() == 2 && f.getTitles(tier).size() >= f.getMembers().size()) return false;
	    if (!composite) {
	        if(titleComplete) return titleCount >= provinces.size();
	        return titleCount >= getDeJureNeeded(deJure);
	    }
	    if (titleComplete) {
	        return titleCount >= titles.size() && ownedNestedProvinces >= requiredProvinces;
	    }

	    return titleCount >= requiredTitles && ownedNestedProvinces >= requiredProvinces;
	}
	
	public int getDeJureNeeded(double deJure) {
		deJure = deJure/100.0;
		int amount = composite ? titles.size() : provinces.size();
		amount = (int) (amount > 2 ? Math.round(amount*deJure) : Math.ceil(amount*deJure));
		if(amount < 1) amount = 1;
		return amount;
	}
	
	public int compositeNeeded(double deJure) {
		deJure = deJure/100.0;
		int amount = composite ? TitleManager.getProvinces(this).size() : provinces.size();
		amount = (int) (amount > 2 ? Math.round(amount*deJure) : Math.ceil(amount*deJure));
		if(amount < 1) amount = 1;
		return amount;
	}
	
	public void getGUIString(Faction f, List<Integer> ownedProvinces, List<Title> ownedTitles, double deJure, List<String> lore) {
	    int needed = getNeededAmount();
	    int deJureNeeded = getDeJureNeeded(deJure);
	    int current = getCurrentAmount(f, ownedProvinces, ownedTitles);
	    int compositeNeeded = compositeNeeded(deJure);
	    
	    List<Integer> nestedProvinces = TitleManager.getProvinces(this);
	    int ownedProvincesCount = nestedProvinceCheck(ownedProvinces, nestedProvinces);

	    if (!composite) {
	        lore.add(StringFormatter.formatHex("#cab58eYou need at least: #f5f5f5" + deJureNeeded + "/" + needed + " #cab58eProvinces #535955[" + deJure + "%]"));
	        if (current > 0) {
	            int percent = (int) Math.round((current * 1.0) / (needed * 1.0) * 100);
	            lore.add(StringFormatter.formatHex("#767a77(Currently: #a1aba3" + current + "/" + needed + " #767a77Provinces #535955[" + percent + "%]#767a77)"));
	        }
	    } else {
	        lore.add(StringFormatter.formatHex("#cab58e§lRequired Titles:"));
	        if(titleComplete) lore.add(StringFormatter.formatHex("#767a77Requires all #a1aba3" + needed + " #535955[100%]"));
	        else lore.add(StringFormatter.formatHex("#767a77At least #a1aba3" + deJureNeeded + " #535955[" + deJure + "%]"));
	        for (String requiredId : titles) {
	            Title req = TitleLoader.getById(requiredId);
	            boolean has = ownedTitles.stream().anyMatch(owned -> owned.getId().equalsIgnoreCase(requiredId));
	            String color = has ? "#73e089" : "#e07373";
	            lore.add(StringFormatter.formatHex(color + "- " + req.getName() + " §7(" + req.getTier().getName() + "§7)"));
	        }
	        lore.add(""); // spacer
	        int percent = (int) Math.round((ownedProvincesCount * 1.0) / (nestedProvinces.size() * 1.0) * 100);
	        lore.add(StringFormatter.formatHex("#cab58e§lAdditional Requirements:"));
	        lore.add(StringFormatter.formatHex("#767a77Must control at least #a1aba3" + compositeNeeded + "/" + nestedProvinces.size() + " #767a77provinces total #535955[" + deJure + "%]"));
	        lore.add(StringFormatter.formatHex("#767a77Currently: #a1aba3" + ownedProvincesCount+ " #535955[" + percent + "%]"));
	    }
	}

	public int getNeededAmount() {
	    return composite ? titles.size() : provinces.size();
	}

	public int getCurrentAmount(Faction f, List<Integer> ownedProvinces, List<Title> ownedTitles) {
	    if (composite) {
	        int count = 0;
	        for (String requiredId : titles) {
	            for (Title ownedTitle : ownedTitles) {
	                if (ownedTitle.getId().equalsIgnoreCase(requiredId)) {
	                    count++;
	                    break;
	                }
	            }
	        }
	        return count;
	    } else {
	        int count = 0;
	        for (Integer provinceId : provinces) {
	            if (ownedProvinces.contains(provinceId)) {
	                count++;
	            }
	        }
	        return count;
	    }
	}
	
	public int nestedProvinceCheck(List<Integer> ownedProvinces, List<Integer> provinces) {
		int count = 0;
        for (Integer provinceId : provinces) {
            if (ownedProvinces.contains(provinceId)) {
                count++;
            }
        }
        return count;
	}
}
