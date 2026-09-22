package net.tfminecraft.simplefactions.tiers;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class Tier {
	private String id;
	private String name;
	private int prestige;
	private int tier;
	private List<String> aliases = new ArrayList<>();
	private int index = -1;

	private int formCost = 0;
	
	
	public Tier(String key, ConfigurationSection config) {
		id = key;
		name = StringFormatter.formatHex(config.getString("name", key));
		prestige = config.getInt("prestige", 0);
		tier = config.getInt("tier", -1);
		if(config.contains("aliases")) {
			for(String s : config.getStringList("aliases")) {
				aliases.add(StringFormatter.formatHex(s));
			}
		}
		formCost = config.getInt("form-cost", 0);
	}
	
	public Tier(Tier another, int i) {
		id = another.getId();
		name = another.getName();
		prestige = another.getPrestige();
		tier = another.getTier();
		aliases = another.getAliases();
		index = i;
		formCost = another.getFormCost();
	}

	public boolean canForm() {
		return formCost > 0;
	}

	public int getFormCost() {
		return formCost;
	}

	public String getId() {
		return id;
	}


	public String getName() {
		return name;
	}


	public int getPrestige() {
		return prestige;
	}


	public int getTier() {
		return tier;
	}
	
	public boolean hasAliases() {
		return aliases.size() > 0;
	}

	public List<String> getAliases() {
		return aliases;
	}
	
	public String getFormattedName() {
		if(index == -1) return name;
		return getCurrentAlias()+" §7("+name+"§7)";
	}
	
	public String getCurrentAlias() {
		return aliases.get(index);
	}
	
	public int getIndex() {
		return index;
	}
	
	public void setIndex(int i) {
		index = i;
	}
}
