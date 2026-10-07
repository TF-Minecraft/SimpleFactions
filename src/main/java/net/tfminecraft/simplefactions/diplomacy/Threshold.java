package net.tfminecraft.simplefactions.diplomacy;

import org.bukkit.configuration.ConfigurationSection;

public class Threshold {
	private int opinion;
	private String type;
	private boolean mutual;
	
	
	public Threshold(ConfigurationSection config) {
		type = config.getString("mode", "higher_than_or_equal_to");
		if (!type.equalsIgnoreCase("lower_than_or_equal_to")
				&& !type.equalsIgnoreCase("higher_than_or_equal_to")) {
			throw new IllegalArgumentException("Unknown opinion threshold mode: " + type);
		}
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
		return type.equalsIgnoreCase("lower_than_or_equal_to") ? i <= opinion : i >= opinion;
	}

	public String getFormattedType() {
		return (new String(type)).replace("_", " ");
	}
	
	public String getFormattedShort() {
		return type.equalsIgnoreCase("lower_than_or_equal_to") ? "<=" : ">=";
	}
}
