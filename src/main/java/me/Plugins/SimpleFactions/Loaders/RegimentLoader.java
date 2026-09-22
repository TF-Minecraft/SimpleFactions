package me.Plugins.SimpleFactions.Loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.SimpleFactions.Army.Regiment;

public class RegimentLoader {
	public static List<Regiment> oList = new ArrayList<>();
	public static List<Regiment> getRegiments(){
		return oList;
	}
	public static Regiment getByString(String id) {
		for(Regiment r : oList) {
			if(r.getId().equalsIgnoreCase(id)) return r;
		}
		return null;
	}
	/** Prototype for mercenary company regiments; callers clone it rather than share it. */
	public static Regiment getMercenaryRegiment() {
		for(Regiment r : oList) {
			if(r.isMercenary()) return r;
		}
		return null;
	}
	public void loadRegiments(File configFile) {
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
		Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			Regiment r = new Regiment(key, config.getConfigurationSection(key));
			oList.add(r);
		}
	}
}
