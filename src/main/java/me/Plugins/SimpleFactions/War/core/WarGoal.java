package me.Plugins.SimpleFactions.War.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.Goal;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class WarGoal {
	private String id;
	private String name;
	private Goal type;
	private int cost;
	
	private List<String> desc = new ArrayList<>();
	
	private List<String> targeters = new ArrayList<>();
	
	private boolean stackable;
	
	public WarGoal(String key, ConfigurationSection config) {
		id = key;
		name = StringFormatter.formatHex(config.getString("name", key));
		try {
			type = Goal.valueOf(key.toUpperCase());
		} catch(Exception e){
			e.printStackTrace();
			type = Goal.ANNEX;
		}
		cost = config.getInt("cost", 1);
		if(config.contains("target")) {
			targeters = config.getStringList("target");
		}
		if(config.contains("desc")) {
			for(String s : config.getStringList("desc")) {
				desc.add(StringFormatter.formatHex(s));
			}
		}
		stackable = config.getBoolean("stackable", false);
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Goal getType() {
		return type;
	}

	public int getCost() {
		return cost;
	}
	
	public List<String> getDescription() {
		return desc;
	}

	public List<String> getTargeters() {
		return targeters;
	}
	
	public boolean canTarget(War war, Faction from, Faction target) {
		HashMap<Faction, WarGoal> otherGoals = war.getWarGoalsOn(target);
		Collection<WarGoal> goals = otherGoals.values();
		if(goals.contains(this) && !stackable) return false;
		for(String targeter : targeters) {
			if(targeter.equalsIgnoreCase("overlord")) {
				if(RelationManager.getOverlord(from) == null) return false;
				if(!RelationManager.getOverlord(from).equalsIgnoreCase(target.getId())) return false;
			} else if(targeter.equalsIgnoreCase("overlords")) {
				if(RelationManager.getSubjects(target).size() == 0) return false;
			} else if(targeter.equalsIgnoreCase("independent")) {
				if(RelationManager.getOverlord(target) != null) return false;
			} else if(targeter.equalsIgnoreCase("lower_tier")) {
				if(from.getTier().getTier() < target.getTier().getTier()) return false;
			} else if(targeter.equalsIgnoreCase("higher_tier")) {
				if(from.getTier().getTier() > target.getTier().getTier()) return false;
			}
		}
		return true;
	}
}
