package me.Plugins.SimpleFactions.Diplomacy;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class RelationType {
	private String id;
	private String name;
	private String prefix;
	private int target;
	private int limit;
	
	private boolean visible;
	private boolean def;
	
	private boolean settable;
	private boolean mutual;
	
	private boolean vassal;
	private boolean overlord;
	
	private boolean lock;
	
	private String link;
	private double baseCost;
	
	private List<FactionModifier> giveModifiers = new ArrayList<>();
	private List<FactionModifier> recieveModifiers = new ArrayList<>();

	private boolean isTradeAgreement;
	private boolean treaty;
	private boolean blocksWar;
	private boolean blocksShops;
	private boolean clearTreaty;
	private List<FactionModifier> tradeEffectsUs = new ArrayList<>();
	private List<FactionModifier> tradeEffectsThem = new ArrayList<>();
	
	private Threshold threshold;

	private boolean elevationTarget;
	private boolean canPickForWar;
	
	public RelationType(String key, ConfigurationSection config) {
		id = key;
		name = StringFormatter.formatHex(config.getString("name", "None"));
		prefix = StringFormatter.formatHex(config.getString("prefix", "#a89977Our "));
		baseCost = config.getDouble("cost", 0.0);
		target = config.getInt("target", 0);
		limit = config.getInt("limit", -1);
		def = config.getBoolean("default", false);
		visible = config.getBoolean("visible", true);
		settable = config.getBoolean("settable", true);
		mutual = config.getBoolean("mutual", false);
		link = config.getString("link", key);
		vassal = config.getBoolean("vassal", false);
		overlord = config.getBoolean("overlord", false);
		lock = config.getBoolean("lock", false);
		elevationTarget = config.getBoolean("elevation-target", false);
		canPickForWar = config.getBoolean("can-pick-for-war", true);
		isTradeAgreement = config.getBoolean("trade-agreement", false);
		treaty = config.getBoolean("treaty", false);
		blocksWar = config.getBoolean("blocks-war", false);
		blocksShops = config.getBoolean("blocks-shops", false);
		clearTreaty = config.getBoolean("clear", false);
		if(config.isConfigurationSection("threshold")) {
			threshold = new Threshold(config.getConfigurationSection("threshold"));
		}
		if(config.contains("give-modifiers")) {
			FactionModifier.addFromConfig(config, "give-modifiers", giveModifiers);
		}
		if(config.contains("recieve-modifiers")) {
			FactionModifier.addFromConfig(config, "recieve-modifiers", recieveModifiers);
		}
		if(config.contains("trade-effects-us")) {
			FactionModifier.addFromConfig(config, "trade-effects-us", tradeEffectsUs);
		}
		if(config.contains("trade-effects-them")) {
			FactionModifier.addFromConfig(config, "trade-effects-them", tradeEffectsThem);
		}
	}

	public boolean isTradeAgreement() {
		return isTradeAgreement;
	}

	public boolean isTreaty() {
		return treaty;
	}

	public boolean blocksWar() {
		return blocksWar;
	}

	public boolean blocksShops() {
		return blocksShops;
	}

	public boolean isClearTreaty() {
		return clearTreaty;
	}

	public boolean isElevationTarget() {
		return elevationTarget;
	}

	public boolean canPickForWar() {
		return canPickForWar;
	}

	public double getBaseCost() {
		return baseCost;
	}
	
	public boolean hasThreshold() {
		return threshold != null;
	}
	
	public Threshold getThreshold() {
		return threshold;
	}
	
	public boolean fulfilled(int opinion) {
		return threshold.fulfilled(opinion);
	}
	
	public String getFormattedThresholdType() {
		return threshold.getFormattedType();
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}
	
	public String getPrefix() {
		return prefix;
	}
	
	public String getFull() {
		return prefix+name;
	}

	public int getTarget() {
		return target;
	}
	
	public boolean isDefault() {
		return def;
	}
	
	public boolean isVisible() {
		return visible;
	}

	public boolean isSettable() {
		return settable;
	}

	public boolean isMutual() {
		return mutual;
	}
	
	public boolean isVassalage() {
		return vassal;
	}
	
	public boolean isOverlord() {
		return overlord;
	}
	
	public boolean hasLock() {
		return lock;
	}
	
	public boolean shouldUpdateMap() {
		return overlord || vassal;
	}
	
	public boolean willReset() {
		return mutual || overlord || vassal || hasLink();
	}
	
	public boolean hasGiveModifiers() {
		return giveModifiers.size() > 0;
	}
	
	public List<FactionModifier> getGiveModifiers() {
		return giveModifiers;
	}
	
	public boolean hasRecieveModifiers() {
		return recieveModifiers.size() > 0;
	}
	
	public List<FactionModifier> getRecieveModifiers() {
		return recieveModifiers;
	}

	public boolean hasTradeEffectsUs() {
		return getTradeEffectsUs().size() > 0;
	}

	public List<FactionModifier> getTradeEffectsUs() {
		List<FactionModifier> usEffects = new ArrayList<>(tradeEffectsUs);
		if(isMutual()) {
			usEffects.addAll(getLink().getTradeEffectsThemRaw());
		}
		return usEffects;
	}

	public List<FactionModifier> getTradeEffectsUsRaw() {
		return tradeEffectsUs;
	}

	public boolean hasTradeEffectsThem() {
		return getTradeEffectsThem().size() > 0;
	}

	public List<FactionModifier> getTradeEffectsThem() {
		List<FactionModifier> themEffects = new ArrayList<>(tradeEffectsThem);
		if(isMutual()) {
			themEffects.addAll(getLink().getTradeEffectsUsRaw());
		}
		return themEffects;
	}

	public List<FactionModifier> getTradeEffectsThemRaw() {
		return tradeEffectsThem;
	}
	
	public boolean hasLink() {
		return !id.equalsIgnoreCase(link);
	}

	public String getLinkString() {
		return link;
	}
	
	public RelationType getLink() {
		return RelationLoader.getType(link);
	}

	public boolean hasLimit() {
		return limit >= 0;
	}

	public int getLimit() {
		return limit;
	}
}
