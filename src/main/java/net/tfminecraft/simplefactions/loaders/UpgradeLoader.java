package net.tfminecraft.simplefactions.loaders;

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

import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;

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
        Map<String, Upgrade> loaded = new java.util.LinkedHashMap<>();
        try {
            config.load(configFile);
            for (String key : config.getKeys(false)) {
                loaded.put(key, new Upgrade(key, config.getConfigurationSection(key)));
            }
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            throw new IllegalStateException("Cannot load definitions from " + configFile, e);
        }
        map.clear();
        map.putAll(loaded);
    }
}
