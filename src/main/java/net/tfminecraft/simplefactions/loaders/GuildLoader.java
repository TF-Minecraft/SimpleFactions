package net.tfminecraft.simplefactions.loaders;

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

import net.tfminecraft.simplefactions.guild.GuildType;

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
        Map<String, GuildType> loaded = new java.util.LinkedHashMap<>();
        try {
            config.load(configFile);
            for (String key : config.getKeys(false)) {
                loaded.put(key, new GuildType(key, config.getConfigurationSection(key)));
            }
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            e.printStackTrace();
            return;
        }
        map.clear();
        map.putAll(loaded);
    }
}
