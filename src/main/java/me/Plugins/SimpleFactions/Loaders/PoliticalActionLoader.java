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

import me.Plugins.SimpleFactions.government.movement.Action;
import me.Plugins.SimpleFactions.government.movement.PoliticalAction;

public class PoliticalActionLoader {
    public static Map<Action, PoliticalAction> map = new LinkedHashMap<>();
	public static Map<Action, PoliticalAction> get(){
		return map;
	}

    public static List<PoliticalAction> getList(){
		return new ArrayList<>(map.values());
	}
	public static PoliticalAction getByAction(Action id) {
		for(PoliticalAction r : map.values()) {
			if(r.getAction() == id) return r;
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
		map.put(Action.NONE, new PoliticalAction(Action.NONE));
		for(String key : list) {
			PoliticalAction r = new PoliticalAction(key, config.getConfigurationSection(key));
			map.put(r.getAction(), r);
		}
	}
}
