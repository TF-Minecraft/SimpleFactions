package me.Plugins.SimpleFactions.Loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.SimpleFactions.laws.LawGroup;


public class LawLoader {
    public static Map<String, LawGroup> map = new LinkedHashMap<>();
	public static Map<String, LawGroup> get(){
		return map;
	}

    public static List<LawGroup> getList(){
		return new ArrayList<>(map.values());
	}
	public static LawGroup getByString(String id) {
		for(LawGroup r : map.values()) {
			if(r.getId().equalsIgnoreCase(id)) return r;
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

		List<String> list = new ArrayList<>(set);
		
		for(String key : list) {
			LawGroup r = new LawGroup(key, config.getConfigurationSection(key));
			map.put(key, r);
		}
	}
}
