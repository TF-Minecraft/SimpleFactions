package me.Plugins.SimpleFactions.War.resolution;

import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.government.StabilityModifier;

public final class PillageTradeHit {
	public static final String NAME = "Pillage";
	public static final int POWER_TICKS_PER_DAY = 24;

	private PillageTradeHit() {}

	public static double decayPerPowerTick(double percent, int days) {
		if (days <= 0) {
			return Math.abs(percent);
		}
		return Math.abs(percent) / (days * (double) POWER_TICKS_PER_DAY);
	}

	public static double decayFromConfig() {
		return decayPerPowerTick(Cache.pillageTradeHitPercent, Cache.pillageTradeHitDays);
	}

	public static double applyToIncome(Guild guild, double income) {
		if (guild == null) {
			return income;
		}
		double multiplier = Math.max(0, 1 + percent(guild) / 100.0);
		return income * multiplier;
	}

	public static double percent(Guild guild) {
		double total = 0;
		if (guild == null || guild.getPillageHits() == null) {
			return 0;
		}
		for (StabilityModifier modifier : guild.getPillageHits()) {
			if (modifier != null) {
				total += modifier.getModifier();
			}
		}
		return total;
	}

	public static String breakdownLine(Guild guild) {
		double p = percent(guild);
		if (p == 0) {
			return null;
		}
		long rounded = Math.round(p);
		String signed = rounded > 0 ? "+" + rounded : Long.toString(rounded);
		return "#d4c9aePillage: #c45749" + signed + "%";
	}

	public static String ledgerSuffix(Guild guild) {
		if (percent(guild) == 0) {
			return "";
		}
		return " §7(§cPillaged§7)";
	}

	public static void attach(Guild guild, double percent, double decay) {
		if (guild == null || guild.getPillageHits() == null) {
			return;
		}
		List<StabilityModifier> hits = guild.getPillageHits();
		hits.removeIf(modifier -> modifier != null && NAME.equals(modifier.getName()));
		hits.add(new StabilityModifier(NAME, percent, decay));
	}

	public static void tick(Guild guild) {
		if (guild == null || guild.getPillageHits() == null || guild.getPillageHits().isEmpty()) {
			return;
		}
		for (StabilityModifier modifier : new ArrayList<>(guild.getPillageHits())) {
			if (modifier == null || modifier.tick()) {
				guild.getPillageHits().remove(modifier);
			}
		}
	}
}
