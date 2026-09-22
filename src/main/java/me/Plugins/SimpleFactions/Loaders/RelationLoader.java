package me.Plugins.SimpleFactions.Loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.SimpleFactions.Diplomacy.Attitude;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;

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
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
		Set<String> set = config.getConfigurationSection("types").getKeys(false);

		List<String> list = new ArrayList<String>(set);
		types.clear();
		for(String key : list) {
			RelationType r = new RelationType(key, config.getConfigurationSection("types."+key));
			System.out.println("loaded relationtype "+r.getId());
			types.add(r);
		}
	}
	public void loadAttitudes(File configFile) {
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
		Set<String> set = config.getConfigurationSection("attitudes").getKeys(false);

		List<String> list = new ArrayList<String>(set);
		attitudes.clear();
		for(String key : list) {
			Attitude a = new Attitude(key, config.getConfigurationSection("attitudes."+key));
			attitudes.add(a);
		}
	}
}
