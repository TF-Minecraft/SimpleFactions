package me.Plugins.SimpleFactions.Diplomacy;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class Attitude {
	private String id;
	private String name;
	private int target;
	private double baseCost;
	private boolean def;
	private final List<FactionModifier> recieveModifiers = new ArrayList<>();

	public Attitude(String key, ConfigurationSection config) {
		id = key;
		name = StringFormatter.formatHex(config.getString("name", "None"));
		target = config.getInt("target", 0);
		baseCost = config.getDouble("cost", 0);
		def = config.getBoolean("default", false);
		FactionModifier.addFromConfig(config, "recieve-modifiers", recieveModifiers);
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getTarget() {
		return target;
	}

	public double getBaseCost() {
		return baseCost;
	}

	public boolean isDefault() {
		return def;
	}

	public boolean hasRecieveModifiers() {
		return !recieveModifiers.isEmpty();
	}

	public List<FactionModifier> getRecieveModifiers() {
		return recieveModifiers;
	}
}
