package me.Plugins.SimpleFactions.Objects;

import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.enums.FactionModifiers;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class FactionModifier {
	protected FactionModifiers type;
	protected double amount;
	protected Faction from;
	private ModifierScale.Kind scale = ModifierScale.Kind.NONE;
	private double atWeaker;
	private double atEqual;
	private double atStronger;

	public FactionModifier(String m) {
	    try {
	        amount = Double.parseDouble(m.split("\\(")[1].replace(")", ""));
	        type = FactionModifiers.valueOf(m.split("\\(")[0].toUpperCase());
			atEqual = amount;
	    } catch(Exception e) {
	        e.printStackTrace();
	    }
	}

	public FactionModifier(FactionModifiers type, double amount) {
		this.type = type;
		this.amount = amount;
		this.atEqual = amount;
	}

	public FactionModifier(Faction from, FactionModifier m) {
		this.from = from;
		this.type = m.type;
		this.amount = m.amount;
		this.scale = m.scale;
		this.atWeaker = m.atWeaker;
		this.atEqual = m.atEqual;
		this.atStronger = m.atStronger;
	}

	public FactionModifier(Faction from, FactionModifiers type, double amount) {
		this.from = from;
		this.type = type;
		this.amount = amount;
		this.atEqual = amount;
	}

	public static FactionModifier fromYamlEntry(Object entry) {
		if (entry instanceof String s) {
			return new FactionModifier(s);
		}
		if (entry instanceof Map<?, ?> map) {
			return fromMap(map);
		}
		return null;
	}

	public static void addFromConfig(ConfigurationSection config, String key, List<FactionModifier> out) {
		if (config == null || !config.contains(key)) {
			return;
		}
		List<?> list = config.getList(key);
		if (list == null || list.isEmpty()) {
			for (String s : config.getStringList(key)) {
				out.add(new FactionModifier(s));
			}
			return;
		}
		for (Object entry : list) {
			FactionModifier mod = fromYamlEntry(entry);
			if (mod != null && mod.type != null) {
				out.add(mod);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static FactionModifier fromMap(Map<?, ?> map) {
		Object typeRaw = map.get("type");
		if (typeRaw == null) {
			return null;
		}
		FactionModifiers type;
		try {
			type = FactionModifiers.valueOf(String.valueOf(typeRaw).toUpperCase());
		} catch (IllegalArgumentException ex) {
			return null;
		}
		ModifierScale.Kind kind = ModifierScale.kindFrom(stringVal(map.get("scale")));
		double atEqual = doubleVal(map.get("at_equal"), doubleVal(map.get("amount"), 0));
		FactionModifier mod = new FactionModifier(type, atEqual);
		mod.scale = kind;
		mod.atWeaker = doubleVal(map.get("at_weaker"), atEqual);
		mod.atEqual = atEqual;
		mod.atStronger = doubleVal(map.get("at_stronger"), atEqual);
		mod.amount = atEqual;
		return mod;
	}

	private static String stringVal(Object o) {
		return o == null ? null : String.valueOf(o);
	}

	private static double doubleVal(Object o, double fallback) {
		if (o instanceof Number n) {
			return n.doubleValue();
		}
		if (o instanceof String s) {
			try {
				return Double.parseDouble(s);
			} catch (NumberFormatException ignored) {
				return fallback;
			}
		}
		return fallback;
	}

	public Faction getFrom() {
		return from;
	}

	public void edit(double d) {
		amount += d;
		fix();
	}

	private void fix() {
		amount = Math.round(amount*100)/100;
	}

	private String prefix() {
		String prefix = "";
		switch(type) {
			case LEVY:
				prefix = "#d45131Levy Contribution";
				break;
			case MILITARY_UPKEEP:
				prefix = "#b39088Military Upkeep";
				break;
			case NODE_SPEED:
				prefix = "#92d96cNode Speed";
				break;
			case PRESTIGE:
				prefix = "#3e7fb5Prestige to Overlord";
				break;
			case PRESTIGE_BONUS:
				prefix = "#409dc2Prestige Bonus";
				break;
			case TRIBUTE:
				prefix = "#d49024Tribute";
				break;
			case TAX_MULTIPLIER:
				prefix = "#5acca2Tax Multiplier";
				break;
			case DE_JURE:
				prefix = "#7bd481De Jure Requirement";
				break;
			case STABILITY_INFLUENCE:
				prefix = "#d64d66Stability Influence";
				break;
			case TRADE_POWER:
				prefix = "#92d665Trade Power";
				break;
			case PRODUCTION:
				prefix = "#f2c94cProduction";
				break;
			case DIPLOMATIC_CAPACITY_MULTIPLIER:
				prefix = "#56ccf2Diplomatic Capacity Multiplier";
				break;
			case ADMIN_POWER_MULTIPLIER:
				prefix = "#ebde54Admin Power Multiplier";
				break;
			case ADMIN_POWER_GAIN_MULTIPLIER:
				prefix = "#d1b347Admin Power Gain Multiplier";
				break;
			default:
				prefix = "#c7b381Unknown Modifier";
				break;
		}
		return StringFormatter.formatHex(prefix);
	}

	private String suffix(double displayed) {
		String color = isBeneficial(displayed) ? "#87d65c" : "#d65c5c";
		return StringFormatter.formatHex(
			"§7(" + color
			+ (isMultiplier() && displayed > 0 ? "+" : "")
			+ FormatterRound(displayed) + "%§7)"
		);
	}

	private static String FormatterRound(double displayed) {
		double rounded = Math.round(displayed * 100.0) / 100.0;
		if (rounded == (long) rounded) {
			return String.valueOf((long) rounded);
		}
		return String.valueOf(rounded);
	}

	public boolean isMultiplier() {
		return type == FactionModifiers.TAX_MULTIPLIER;
	}

	public String getString() {
		return getString(null);
	}

	public String getString(Faction owner) {
		double displayed = resolve(owner);
		String extra = "";
		if (scale == ModifierScale.Kind.RELATIVE_PRESTIGE) {
			extra = StringFormatter.formatHex(" #a39ba8(vs their prestige)");
		}
		return prefix()+"§e: "+suffix(displayed)+extra;
	}

	public FactionModifiers getType() {
		return type;
	}

	public double getAmount() {
		if(type == FactionModifiers.DE_JURE && Cache.deJureRequirement+amount < 20) return 20-Cache.deJureRequirement;
		return amount;
	}

	public double resolve(Faction owner) {
		if (scale != ModifierScale.Kind.RELATIVE_PRESTIGE || from == null || owner == null) {
			return getAmount();
		}
		double theirs = from.getPrestige() == null ? 0 : from.getPrestige();
		double ours = owner.getPrestige() == null ? 0 : owner.getPrestige();
		return ModifierScale.relativePrestige(theirs, ours, atWeaker, atEqual, atStronger);
	}

	public ModifierScale.Kind getScale() {
		return scale;
	}

	private boolean isBeneficial(double displayed) {
		boolean positive = displayed > 0;
		boolean goodOutcome = type.isPositiveGood() ? positive : !positive;
		return goodOutcome;
	}

	@Override
	public String toString() {
		return type.name() + "{from=" + (from != null ? from.getId() : "null") + ", amount=" + amount + "}";
	}
}
