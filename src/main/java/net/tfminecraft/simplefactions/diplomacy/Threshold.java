package net.tfminecraft.simplefactions.diplomacy;

import org.bukkit.configuration.ConfigurationSection;

public class Threshold {
	private int opinion;
	private String type;
	private boolean mutual;
	
	
	public Threshold(ConfigurationSection config) {
		type = config.getString("mode", "higher_than_or_equal_to");
		opinion = config.getInt("amount", 20);
		mutual = config.getBoolean("mutual", false);
	}
	
	public int getOpinion() {
		return opinion;
	}
	
	public boolean isMutual() {
		return mutual;
	}
	
	public boolean fulfilled(int i) {
		if(type.equalsIgnoreCase("lower_than_or_equal_to")) {
			if(i > opinion) return false;
		}
		else if(type.equalsIgnoreCase("higher_than_or_equal_to")) {
			if(i < opinion) return false;
		}
		return true;
	}
	
	public String getFormattedType() {
		return (new String(type)).replace("_", " ");
	}
	
	public String getFormattedShort() {
		if(type.equalsIgnoreCase("lower_than_or_equal_to")) {
			return "<=";
		}
		else if(type.equalsIgnoreCase("higher_than_or_equal_to")) {
			return ">=";
		}
		return "=";
	}
}
