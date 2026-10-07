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

import net.tfminecraft.simplefactions.laws.LawGroup;


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
        Map<String, LawGroup> loaded = new java.util.LinkedHashMap<>();
        try {
            config.load(configFile);
            for (String key : config.getKeys(false)) {
                loaded.put(key, new LawGroup(key, config.getConfigurationSection(key)));
            }
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            e.printStackTrace();
            return;
        }
        map.clear();
        map.putAll(loaded);
    }
}
