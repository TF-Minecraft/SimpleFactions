package net.tfminecraft.simplefactions.objects;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class PrestigeRank {
	String id;
	String name;
	Integer level;
	Double min;
	Double percentage;
	Boolean an;
	
	private List<FactionModifier> modifiers = new ArrayList<>();
	
	public boolean hasModifiers() {
		return modifiers.size() > 0;
	}
	
	public List<FactionModifier> getModifiers(){
		return modifiers;
	}
	
	public Boolean getAn() {
		return an;
	}
	public void setAn(Boolean an) {
		this.an = an;
	}
	public Integer getLevel() {
		return level;
	}
	public void setLevel(Integer level) {
		this.level = level;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Double getMin() {
		return min;
	}
	public void setMin(Double min) {
		this.min = min;
	}
	public Double getPercentage() {
		return percentage;
	}
	public void setPercentage(Double percentage) {
		this.percentage = percentage;
	}
	public PrestigeRank(String key, ConfigurationSection config) {
		this.id = key;
		this.name = StringFormatter.formatHex(config.getString("name"));
		this.level = config.getInt("level");
		this.min = config.getDouble("minimum-prestige");
		this.percentage = config.getDouble("percentage-of-highest");
		if(config.contains("an")) {
			this.an = config.getBoolean("an");
		} else {
			this.an = false;
		}
		if(config.contains("modifiers")) {
			for(String s : config.getStringList("modifiers")) {
				modifiers.add(new FactionModifier(s));
			}
		}
	}
}
