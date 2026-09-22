package me.Plugins.SimpleFactions.Loaders;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;

public class BattleTemplateLoader {
	private static final Map<String, BattleTemplate> templates = new HashMap<>();

	public BattleTemplateLoader() {
	}

	public static BattleTemplate getByName(String id) {
		if (id == null) {
			return null;
		}
		BattleTemplate exact = templates.get(id);
		if (exact != null) {
			return exact;
		}
		for (Map.Entry<String, BattleTemplate> entry : templates.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(id)) {
				return entry.getValue();
			}
		}
		return null;
	}

	public static Map<String, BattleTemplate> getAll() {
		return Map.copyOf(templates);
	}

	public static void resetForTests() {
		templates.clear();
	}

	public static void putForTests(BattleTemplate template) {
		if (template != null) {
			templates.put(template.getId(), template);
		}
	}

	public void load(File configFile) {
		templates.clear();
		if (configFile == null) {
			return;
		}
		FileConfiguration config = new YamlConfiguration();
		try {
			config.load(configFile);
		} catch (IOException | InvalidConfigurationException e) {
			if (Bukkit.getServer() != null) {
				Bukkit.getLogger().warning("[SimpleFactions] Failed to load battle-templates.yml: " + e.getMessage());
			}
			e.printStackTrace();
			return;
		}
		Set<String> keys = config.getKeys(false);
		for (String key : keys) {
			try {
				templates.put(key, new BattleTemplate(key, config.getConfigurationSection(key)));
			} catch (Exception e) {
				if (Bukkit.getServer() != null) {
					Bukkit.getLogger().warning("[SimpleFactions] Failed to load battle template '" + key + "': " + e.getMessage());
				}
				e.printStackTrace();
			}
		}
		if (Bukkit.getServer() != null) {
			Bukkit.getLogger().info("[SimpleFactions] Loaded " + templates.size() + " battle template(s) from battle-templates.yml");
		}
	}
}
