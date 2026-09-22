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

import me.Plugins.SimpleFactions.Guild.Branch.Branch;
import me.Plugins.SimpleFactions.Guild.Guild;

public class BranchLoader {
    public static Map<String, Branch> map = new HashMap<>();
	public static Map<String, Branch> get(){
		return map;
	}

    public static List<Branch> getList(){
		return new ArrayList<>(map.values());
	}
	public static Branch getByString(String id) {
		for(Branch r : map.values()) {
			if(r.getId().equalsIgnoreCase(id)) return r;
		}
		return null;
	}
	public static Branch getByGroup(Guild guild, int group) {
		for(Branch b : getList()) {
			if(b.getGroup() != group) continue;
			if(b.isAllowed(guild.getType())) return b;
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
			Branch r = new Branch(key, config.getConfigurationSection(key));
			map.put(key, r);
		}
	}
}
