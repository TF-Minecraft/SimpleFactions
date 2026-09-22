package me.Plugins.SimpleFactions.Loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.SimpleFactions.Guild.GuildType;

public class GuildLoader {
    public static Map<String, GuildType> map = new HashMap<>();
	public static Map<String, GuildType> get(){
		return map;
	}

    public static List<GuildType> getList(){
		return new ArrayList<>(map.values());
	}
	public static GuildType getByString(String id) {
		for(GuildType r : map.values()) {
			if(r.getId().equalsIgnoreCase(id)) return r;
		}
		return null;
	}

	public static GuildType getBaseType() {
		for(GuildType t : getList()) {
			if(t.isBase()) return t;
		}
		return getList().get(0);
	}

	public static GuildType getDefaultType() {
		for(GuildType t : getList()) {
			if(t.isDefault()) return t;
		}
		return getList().get(0);
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
			GuildType r = new GuildType(key, config.getConfigurationSection(key));
			map.put(key, r);
		}
	}
}
