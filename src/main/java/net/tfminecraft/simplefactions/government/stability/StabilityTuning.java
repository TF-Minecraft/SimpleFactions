package net.tfminecraft.simplefactions.government.stability;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Knobs for legitimacy, the weak state, community scale, and the support tithe.
 * Missing config keys keep these defaults.
 */
public final class StabilityTuning {

	public static final StabilityTuning DEFAULTS = new StabilityTuning();

	private static StabilityTuning active = DEFAULTS;

	public double legitimacyElection = 70;
	public double legitimacyCommunity = 80;
	public double legitimacyCouncil = 35;
	public double legitimacyAutocracy = 10;
	public double neutralWeight = 0.35;
	public double illegitimateBelow = 50;
	public double movementGainMultiplier = 2;
	public int communityFreeProvinces = 10;
	public double communityProvinceCost = 2;
	public double weakStateAutocracy = 1.0;
	public double weakStateOligarchy = 0.5;
	public double weakStatePlutocracy = 0;
	public double weakStateCommunity = 0;
	public double weakStateDemocracy = 0.2;
	public double electedWeakStateFactor = 0.5;
	public double foreignTradeMax = 0.5;
	public double slowDebuffSpan = 80;
	public double slowAdminLoss = 0.5;
	public double slowUpkeepGain = 1.0;
	public double slowDeJureMax = 30;
	public double slowPrestigeMax = 25;

	public static StabilityTuning get() {
		return active;
	}

	public static void reset() {
		active = DEFAULTS;
	}

	public static void load(FileConfiguration config) {
		if (config == null) {
			reset();
			return;
		}
		StabilityTuning tuning = new StabilityTuning();
		tuning.legitimacyElection = config.getDouble("stability.legitimacy-election", tuning.legitimacyElection);
		tuning.legitimacyCommunity = config.getDouble("stability.legitimacy-community", tuning.legitimacyCommunity);
		tuning.legitimacyCouncil = config.getDouble("stability.legitimacy-council", tuning.legitimacyCouncil);
		tuning.legitimacyAutocracy = config.getDouble("stability.legitimacy-autocracy", tuning.legitimacyAutocracy);
		tuning.neutralWeight = config.getDouble("stability.neutral-weight", tuning.neutralWeight);
		tuning.illegitimateBelow = config.getDouble("stability.illegitimate-below", tuning.illegitimateBelow);
		tuning.movementGainMultiplier = config.getDouble("stability.movement-gain-multiplier", tuning.movementGainMultiplier);
		tuning.communityFreeProvinces = config.getInt("stability.community-free-provinces", tuning.communityFreeProvinces);
		tuning.communityProvinceCost = config.getDouble("stability.community-province-cost", tuning.communityProvinceCost);
		tuning.weakStateAutocracy = config.getDouble("stability.weak-state-autocracy", tuning.weakStateAutocracy);
		tuning.weakStateOligarchy = config.getDouble("stability.weak-state-oligarchy", tuning.weakStateOligarchy);
		tuning.weakStatePlutocracy = config.getDouble("stability.weak-state-plutocracy", tuning.weakStatePlutocracy);
		tuning.weakStateCommunity = config.getDouble("stability.weak-state-community", tuning.weakStateCommunity);
		tuning.weakStateDemocracy = config.getDouble("stability.weak-state-democracy", tuning.weakStateDemocracy);
		tuning.electedWeakStateFactor = config.getDouble("stability.elected-weak-state-factor", tuning.electedWeakStateFactor);
		tuning.foreignTradeMax = config.getDouble("stability.foreign-trade-max", tuning.foreignTradeMax);
		tuning.slowDebuffSpan = config.getDouble("stability.slow-debuff-span", tuning.slowDebuffSpan);
		tuning.slowAdminLoss = config.getDouble("stability.slow-admin-loss", tuning.slowAdminLoss);
		tuning.slowUpkeepGain = config.getDouble("stability.slow-upkeep-gain", tuning.slowUpkeepGain);
		tuning.slowDeJureMax = config.getDouble("stability.slow-de-jure-max", tuning.slowDeJureMax);
		tuning.slowPrestigeMax = config.getDouble("stability.slow-prestige-max", tuning.slowPrestigeMax);
		active = tuning;
	}

	private StabilityTuning() {}
}
