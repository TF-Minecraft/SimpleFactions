package me.Plugins.SimpleFactions.Loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.SimpleFactions.Guild.upgrade.Upgrade;

/**
 * Mercenary company upgrades live in their own file so they never reach
 * {@link UpgradeLoader}, whose entries are copied onto every guild.
 */
public class CompanyUpgradeLoader {
    private static final Map<String, Upgrade> map = new LinkedHashMap<>();

    public static Map<String, Upgrade> get() {
        return map;
    }

    public static List<Upgrade> getList() {
        return new ArrayList<>(map.values());
    }

    public static Upgrade getByString(String id) {
        for (Upgrade u : map.values()) {
            if (u.getId().equalsIgnoreCase(id)) return u;
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
        map.clear();
        for (String key : new ArrayList<>(config.getKeys(false))) {
            map.put(key, new Upgrade(key, config.getConfigurationSection(key)));
        }
    }
}
