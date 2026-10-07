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

import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildType;

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
		if (id != null && id.equalsIgnoreCase("freight_yards")) {
			for (Branch branch : map.values()) {
				if (branch.getId().equalsIgnoreCase("counting_houses")) {
					return branch;
				}
			}
		}
		return null;
	}
	public static Branch getByGroup(Guild guild, int group) {
		return getByGroup(guild.getType(), group);
	}
	public static Branch getByGroup(GuildType type, int group) {
		for(Branch b : getList()) {
			if(b.getGroup() != group) continue;
			if(b.isAllowed(type)) return b;
		}
		return null;
	}
	public static void replaceDisallowedBranches(Map<Integer, Branch> branches, GuildType type) {
		for (Branch branch : branches.values()) {
			if (!branch.isAllowed(type)) {
				Branch replacement = getByGroup(type, branch.getGroup());
				if (replacement != null) {
					branches.put(branch.getGroup(), new Branch(replacement, branch.getLevel()));
				}
			}
		}
	}
	public void load(File configFile) {
        FileConfiguration config = new YamlConfiguration();
        Map<String, Branch> loaded = new java.util.LinkedHashMap<>();
        try {
            config.load(configFile);
            for (String key : config.getKeys(false)) {
                loaded.put(key, new Branch(key, config.getConfigurationSection(key)));
            }
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            throw new IllegalStateException("Cannot load definitions from " + configFile, e);
        }
        map.clear();
        map.putAll(loaded);
    }
}
