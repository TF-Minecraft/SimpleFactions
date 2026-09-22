package me.Plugins.SimpleFactions.Diplomacy;

import org.bukkit.Bukkit;

import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.enums.FactionModifiers;

public class Relation {
	private RelationType type;
	private Attitude attitude;
	private boolean base = false;
	
	private int opinion = 0;
	
	public Relation(RelationType type, Attitude a) {
		this.type = type;
		this.attitude = a;
		this.opinion = 0;
	}
	
	public Relation(RelationType type, Attitude a, int opinion) {
		this.type = type;
		this.attitude = a;
		this.opinion = opinion;
	}
	
	public Relation(Relation another) {
		this.type = another.getType();
		this.attitude = another.getAttitude();
		this.opinion = another.getOpinion();
	}
	
	public Relation() {
		base = true;
		this.type = RelationLoader.getDefaultType();
		this.attitude = RelationLoader.getDefaultAttitude();
		this.opinion = 0;
	}
	
	public boolean isDefault() {
		return base;
	}

	public RelationType getType() {
		return type;
	}

	public void setType(RelationType type) {
		this.type = type;
	}

	public Attitude getAttitude() {
		return attitude;
	}

	public void setAttitude(Attitude attitude) {
		this.attitude = attitude;
	}
	
	private int calculate(String id, DiplomacyHandler handler) {
		int tradeOpinion = 0;
		if(handler.hasTradeRelation(id)) {
			RelationType trade = handler.getTradeRelation(id);
			tradeOpinion = trade.getTarget();
		}
		int treatyOpinion = 0;
		if(handler.hasTreatyRelation(id)) {
			RelationType treaty = handler.getTreatyRelation(id);
			treatyOpinion = treaty.getTarget();
		}
		return attitude.getTarget()+type.getTarget()+tradeOpinion+treatyOpinion;
	}
	
	public int getOpinion() {
		return opinion;
	}
	
	public double getGiveModifier(FactionModifiers m) {
		for(FactionModifier mod : type.getGiveModifiers()) {
			if(mod.getType().equals(m)) return mod.getAmount();
		}
		return 0.0;
	}
	public double getRecieveModifier(FactionModifiers m) {
		for(FactionModifier mod : type.getRecieveModifiers()) {
			if(mod.getType().equals(m)) return mod.getAmount();
		}
		return 0.0;
	}
	
	public void tick(String id, DiplomacyHandler handler) {
		int target = calculate(id, handler);
		if(opinion == target) return;
		if(opinion < target) {
			opinion++;
		} else {
			opinion--;
		}
	}
}
