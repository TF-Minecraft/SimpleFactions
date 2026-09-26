package net.tfminecraft.simplefactions.guild.income;

import net.tfminecraft.simplefactions.Cache;

/**
 * Share of a province's trade income a guild pays as trade upkeep.
 *
 * <p>The share is the province's trade factor times the guild's
 * {@code trade_upkeep} modifier, capped at {@link Cache#maxTradeUpkeep} so a
 * guild always keeps part of what it collects and never pays to trade.
 */
public final class TradeUpkeep {

	public static final double DEFAULT_MAX = 0.75;

	private TradeUpkeep() {
	}

	/** Negative limits fall back to the default; 0 stays the opt-out. */
	public static double sanitizeMax(double max) {
		return max < 0 ? DEFAULT_MAX : max;
	}

	public static double rate(double tradeFactor, double upkeepFactor) {
		return rate(tradeFactor, upkeepFactor, Cache.maxTradeUpkeep);
	}

	static double rate(double tradeFactor, double upkeepFactor, double max) {
		double rate = Math.max(0.0, tradeFactor * upkeepFactor);
		if (max <= 0) {
			return rate;
		}
		return Math.min(rate, max);
	}
}
