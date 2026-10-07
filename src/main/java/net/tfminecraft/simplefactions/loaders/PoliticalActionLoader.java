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

import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;

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
        Map<Action, PoliticalAction> loaded = new java.util.LinkedHashMap<>();
        try {
            config.load(configFile);
            loaded.put(Action.NONE, new PoliticalAction(Action.NONE));
            for (String key : config.getKeys(false)) {
                PoliticalAction action = new PoliticalAction(key, config.getConfigurationSection(key));
                loaded.put(action.getAction(), action);
            }
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            e.printStackTrace();
            return;
        }
        map.clear();
        map.putAll(loaded);
    }
}
