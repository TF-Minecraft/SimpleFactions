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

	private TradeUpkeep() {
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
