package net.tfminecraft.simplefactions.government.stability;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Knobs for legitimacy, economic strain, community scale, and the support tithe.
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
	public double diversityAutocracy = 35;
	public double diversityOligarchy = 55;
	public double diversityDemocracy = 75;
	public double diversityCommunity = 80;
	public double diversityPlutocracy = 90;
	public double diversityFreeTrade = 10;
	public double diversityDecentralized = 5;
	public double diversityMercantilism = 0;
	public double diversityProtectionism = -10;
	public double diversityIsolationism = -20;
	public double diversityOpenBorders = 5;
	public double diversityClosedBorders = 0;
	public double diversityFavour = -10;
	public double diversityRepress = 10;
	public double diversityMin = 10;
	public double diversityMax = 100;
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
		tuning.diversityAutocracy = config.getDouble("stability.diversity-autocracy", tuning.diversityAutocracy);
		tuning.diversityOligarchy = config.getDouble("stability.diversity-oligarchy", tuning.diversityOligarchy);
		tuning.diversityDemocracy = config.getDouble("stability.diversity-democracy", tuning.diversityDemocracy);
		tuning.diversityCommunity = config.getDouble("stability.diversity-community", tuning.diversityCommunity);
		tuning.diversityPlutocracy = config.getDouble("stability.diversity-plutocracy", tuning.diversityPlutocracy);
		tuning.diversityFreeTrade = config.getDouble("stability.diversity-free-trade", tuning.diversityFreeTrade);
		tuning.diversityDecentralized = config.getDouble("stability.diversity-decentralized", tuning.diversityDecentralized);
		tuning.diversityMercantilism = config.getDouble("stability.diversity-mercantilism", tuning.diversityMercantilism);
		tuning.diversityProtectionism = config.getDouble("stability.diversity-protectionism", tuning.diversityProtectionism);
		tuning.diversityIsolationism = config.getDouble("stability.diversity-isolationism", tuning.diversityIsolationism);
		tuning.diversityOpenBorders = config.getDouble("stability.diversity-open-borders", tuning.diversityOpenBorders);
		tuning.diversityClosedBorders = config.getDouble("stability.diversity-closed-borders", tuning.diversityClosedBorders);
		tuning.diversityFavour = config.getDouble("stability.diversity-favour", tuning.diversityFavour);
		tuning.diversityRepress = config.getDouble("stability.diversity-repress", tuning.diversityRepress);
		tuning.diversityMin = config.getDouble("stability.diversity-min", tuning.diversityMin);
		tuning.diversityMax = config.getDouble("stability.diversity-max", tuning.diversityMax);
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
