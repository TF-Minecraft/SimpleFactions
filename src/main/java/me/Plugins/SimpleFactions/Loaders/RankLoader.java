package me.Plugins.SimpleFactions.Loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.SimpleFactions.Objects.PrestigeRank;

public class RankLoader {
	public static List<PrestigeRank> ranks = new ArrayList<PrestigeRank>();
	public static List<PrestigeRank> getRanks(){
		return ranks;
	}
	public static PrestigeRank getLowest(){
		Collections.sort(ranks, new Comparator<PrestigeRank>() {
		    @Override
		    public int compare(PrestigeRank c1, PrestigeRank c2) {
		        return Integer.compare(c1.getLevel(), c2.getLevel());
		    }
		});
		return ranks.get(0);
	}
	public static PrestigeRank getByLevel(Integer i) {
		for(PrestigeRank r : ranks) {
			if(r.getLevel() == i) return r;
		}
		return null;
	}
	public static PrestigeRank getByString(String id) {
		for(PrestigeRank r : ranks) {
			if(r.getId().equalsIgnoreCase(id)) return r;
		}
		return null;
	}
	public void loadRanks(File configFile) {
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
		Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		ranks.clear();
		for(String key : list) {
			PrestigeRank r = new PrestigeRank(key, config.getConfigurationSection(key));
			ranks.add(r);
		}
	}
}
