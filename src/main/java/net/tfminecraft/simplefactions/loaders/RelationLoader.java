package net.tfminecraft.simplefactions.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.diplomacy.Attitude;
import net.tfminecraft.simplefactions.diplomacy.RelationType;

public class RelationLoader {
	public static List<RelationType> types = new ArrayList<>();
	public static List<Attitude> attitudes = new ArrayList<>();
	public static List<RelationType> getTypes(){
		return types;
	}
	public static List<Attitude> getAttitudes(){
		return attitudes;
	}
	
	public static RelationType getDefaultType() {
		for(RelationType r : types) {
			if(r.isDefault()) return r;
		}
		return types.get(0);
	}

	public static RelationType getElevationTarget() {
		for(RelationType r : types) {
			if(r.isElevationTarget()) return r;
		}
		return null;
	}

	public static List<RelationType> getWarPickableVassalTypes() {
		List<RelationType> pickable = new ArrayList<>();
		for (RelationType r : types) {
			if (r.isVassalage() && r.canPickForWar()) {
				pickable.add(r);
			}
		}
		return pickable;
	}

	public static List<RelationType> getDiplomaticTypes() {
		List<RelationType> diplomatic = new ArrayList<>();
		for (RelationType r : types) {
			if (!r.isTradeAgreement() && !r.isTreaty()) {
				diplomatic.add(r);
			}
		}
		return diplomatic;
	}

	public static List<RelationType> getTreatyTypes() {
		List<RelationType> treaties = new ArrayList<>();
		for (RelationType r : types) {
			if (r.isTradeAgreement()) {
				treaties.add(r);
			}
		}
		return treaties;
	}

	public static List<RelationType> getPoliticalTreatyTypes() {
		List<RelationType> treaties = new ArrayList<>();
		for (RelationType r : types) {
			if (r.isTreaty()) {
				treaties.add(r);
			}
		}
		return treaties;
	}

	public static boolean isWarPickableVassal(RelationType type) {
		if (type == null || type.getId() == null) {
			return false;
		}
		for (RelationType pickable : getWarPickableVassalTypes()) {
			if (type.getId().equalsIgnoreCase(pickable.getId())) {
				return true;
			}
		}
		return false;
	}
	
	public static Attitude getDefaultAttitude() {
		for(Attitude a : attitudes) {
			if(a.isDefault()) return a;
		}
		return attitudes.get(0);
	}
	
	public static RelationType getType(String id) {
		for(RelationType r : types) {
			if(r.getId().equalsIgnoreCase(id)) return r;
		}
		return null;
	}
	public static Attitude getAttitude(String id) {
		for(Attitude a : attitudes) {
			if(a.getId().equalsIgnoreCase(id)) return a;
		}
		return null;
	}
	public void loadRelationTypes(File configFile) {
		FileConfiguration config = readConfiguration(configFile);
		if (config == null) return;
		try {
			var section = config.getConfigurationSection("types");
			if (section == null) throw new IllegalArgumentException("Missing relation types section");
			List<RelationType> staged = new ArrayList<>();
			for (String key : section.getKeys(false)) {
				var row = section.getConfigurationSection(key);
				if (row == null) throw new IllegalArgumentException("Invalid relation type: " + key);
				staged.add(new RelationType(key, row));
			}
			types.clear();
			types.addAll(staged);
			for (RelationType relation : staged) logger().info("loaded relationtype " + relation.getId());
		} catch (IllegalArgumentException e) {
			logger().warning("Could not load relation types: " + e.getMessage());
		}
	}

	public void loadAttitudes(File configFile) {
		FileConfiguration config = readConfiguration(configFile);
		if (config == null) return;
		try {
			var section = config.getConfigurationSection("attitudes");
			if (section == null) throw new IllegalArgumentException("Missing attitudes section");
			List<Attitude> staged = new ArrayList<>();
			for (String key : section.getKeys(false)) {
				var row = section.getConfigurationSection(key);
				if (row == null) throw new IllegalArgumentException("Invalid attitude: " + key);
				staged.add(new Attitude(key, row));
			}
			attitudes.clear();
			attitudes.addAll(staged);
		} catch (IllegalArgumentException e) {
			logger().warning("Could not load attitudes: " + e.getMessage());
		}
	}

	private static FileConfiguration readConfiguration(File file) {
		FileConfiguration config = new YamlConfiguration();
		try {
			config.load(file);
			return config;
		} catch (IOException | InvalidConfigurationException | IllegalArgumentException e) {
			logger().warning("Could not read diplomacy configuration: " + e.getMessage());
			return null;
		}
	}

	private static Logger logger() {
		SimpleFactions plugin = SimpleFactions.getInstance();
		return plugin != null ? plugin.getLogger() : Logger.getLogger(RelationLoader.class.getName());
	}
}
