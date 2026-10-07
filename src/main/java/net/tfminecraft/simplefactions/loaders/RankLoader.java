package net.tfminecraft.simplefactions.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.simplefactions.objects.PrestigeRank;

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
			if(java.util.Objects.equals(r.getLevel(), i)) return r;
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
		List<PrestigeRank> loaded = new ArrayList<>();
		try {
			config.load(configFile);
			for (String key : config.getKeys(false)) {
				var section = config.getConfigurationSection(key);
				if (section == null) {
					throw new IllegalArgumentException("Rank " + key + " must be a section");
				}
				loaded.add(new PrestigeRank(key, section));
			}
			if (loaded.isEmpty()) {
				throw new IllegalArgumentException("At least one rank is required");
			}
		} catch (IOException | InvalidConfigurationException | RuntimeException error) {
			java.util.logging.Logger.getLogger(RankLoader.class.getName())
					.warning("Could not load ranks from " + configFile + ": " + error.getMessage());
			return;
		}
		ranks.clear();
		ranks.addAll(loaded);
	}
}
