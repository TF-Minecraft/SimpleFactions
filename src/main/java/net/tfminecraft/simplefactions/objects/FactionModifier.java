package net.tfminecraft.simplefactions.objects;

import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class FactionModifier {
	protected FactionModifiers type;
	protected double amount;
	protected Faction from;
	private ModifierScale.Kind scale = ModifierScale.Kind.NONE;
	private double atWeaker;
	private double atEqual;
	private double atStronger;

	public FactionModifier(String m) {
		if (m == null || m.indexOf('(') < 1 || !m.endsWith(")")) {
			throw new IllegalArgumentException("Invalid faction modifier: " + m);
		}
		int open = m.indexOf('(');
		amount = Double.parseDouble(m.substring(open + 1, m.length() - 1));
		if (!Double.isFinite(amount)) {
			throw new IllegalArgumentException("Modifier amount must be finite: " + m);
		}
		type = modifierType(m.substring(0, open));
		atEqual = amount;
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
			try {
				return new FactionModifier(s);
			} catch (IllegalArgumentException invalid) {
				return null;
			}
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
			type = modifierType(String.valueOf(typeRaw));
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

	private static FactionModifiers modifierType(String value) {
		String id = value.toUpperCase(java.util.Locale.ROOT);
		if (id.equals("INFRASTRUCTURE_ACCESS")) {
			id = "INSTALLATION_ACCESS";
		}
		return FactionModifiers.valueOf(id);
	}

	private static double doubleVal(Object o, double fallback) {
		if (o instanceof Number n) {
			double value = n.doubleValue();
			return Double.isFinite(value) ? value : fallback;
		}
		if (o instanceof String s) {
			try {
				double value = Double.parseDouble(s);
				return Double.isFinite(value) ? value : fallback;
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
		amount = Math.round(amount*100)/100.0;
	}

	private String prefix() {
		return StringFormatter.formatHex(switch (type) {
			case LEVY -> "#d45131Levy Contribution";
			case MILITARY_UPKEEP -> "#b39088Military Upkeep";
			case NODE_SPEED -> "#92d96cNode Speed";
			case PRESTIGE -> "#3e7fb5Prestige to Overlord";
			case PRESTIGE_BONUS -> "#409dc2Prestige Bonus";
			case PRESTIGE_MALUS -> "#d46a6aPrestige Malus";
			case TRIBUTE -> "#d49024Tribute";
			case TAX_MULTIPLIER -> "#5acca2Tax Multiplier";
			case DE_JURE -> "#7bd481De Jure Requirement";
			case STABILITY_INFLUENCE -> "#d64d66Stability Influence";
			case TRADE_POWER -> "#92d665Trade Power";
			case PRODUCTION -> "#f2c94cProduction";
			case INSTALLATION_ACCESS -> "#92d6baInstallation Access";
			case DIPLOMATIC_CAPACITY_MULTIPLIER -> "#56ccf2Diplomatic Capacity Multiplier";
			case ADMIN_POWER_MULTIPLIER -> "#ebde54Admin Power Multiplier";
			case ADMIN_POWER_GAIN_MULTIPLIER -> "#d1b347Admin Power Gain Multiplier";
		});
	}

	private String suffix(double displayed, Region region) {
		double shownAmount = displayed;
		boolean signed = isMultiplier() && displayed > 0;
		if (type == FactionModifiers.INSTALLATION_ACCESS) {
			shownAmount = displayed * 100.0;
			signed = region == Region.FOREIGN_TERRITORY && displayed > 0;
		}
		String color = isBeneficial(displayed) ? "#87d65c" : "#d65c5c";
		String shown = FormatterRound(shownAmount);
		if (type == FactionModifiers.PRESTIGE_MALUS && displayed > 0) {
			shown = "-" + shown;
		}
		return StringFormatter.formatHex(
			"§7(" + color
			+ (signed ? "+" : "")
			+ shown + "%§7)"
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
		return getString(owner, null);
	}

	public String getString(Faction owner, Region region) {
		double displayed = resolve(owner);
		String extra = "";
		if (scale == ModifierScale.Kind.RELATIVE_PRESTIGE) {
			extra = StringFormatter.formatHex(" #a39ba8(vs their prestige)");
		}
		return prefix()+"§e: "+suffix(displayed, region)+extra;
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
