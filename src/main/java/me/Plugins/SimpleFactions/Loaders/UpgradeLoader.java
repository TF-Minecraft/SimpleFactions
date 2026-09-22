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

import me.Plugins.SimpleFactions.Guild.upgrade.Upgrade;

public class UpgradeLoader {
    public static Map<String, Upgrade> map = new LinkedHashMap<>();
	public static Map<String, Upgrade> get(){
		return map;
	}

    public static List<Upgrade> getList(){
		return new ArrayList<>(map.values());
	}
	public static Upgrade getByString(String id) {
		for(Upgrade r : map.values()) {
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
			Upgrade r = new Upgrade(key, config.getConfigurationSection(key));
			map.put(key, r);
		}
	}
}
