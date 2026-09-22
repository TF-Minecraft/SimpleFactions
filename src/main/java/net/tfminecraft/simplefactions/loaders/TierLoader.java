package net.tfminecraft.simplefactions.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.simplefactions.tiers.Tier;

public class TierLoader {
	public static List<Tier> oList = new ArrayList<>();
	public static List<Tier> get(){
		return oList;
	}
	
	public static Tier getLowest() {
	    if (oList.isEmpty()) return null;

	    Tier lowest = oList.get(0);
	    for (Tier tier : oList) {
	        if (tier.getTier() < lowest.getTier()) {
	            lowest = tier;
	        }
	    }
	    return lowest;
	}
	public static Tier getByString(String id) {
		for(Tier r : oList) {
			if(r.getId().equalsIgnoreCase(id)) return r;
		}
		return null;
	}
	public static Tier getByLevel(int i) {
		for(Tier r : oList) {
			if(r.getTier() == i) return r;
		}
		return null;
	}
	public void load(File configFile) {
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
		Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			Tier r = new Tier(key, config.getConfigurationSection(key));
			oList.add(r);
		}
	}
}
