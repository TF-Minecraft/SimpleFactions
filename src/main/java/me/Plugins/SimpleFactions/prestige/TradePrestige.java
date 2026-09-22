package me.Plugins.SimpleFactions.prestige;

import me.Plugins.SimpleFactions.Cache;

/**
 * Prestige from a faction's own guilds' trade power.
 *
 * <p>Below {@link Cache#prestigeFromTradeSoftCap} the conversion is linear
 * ({@link Cache#prestigePerTradePower} per point). Past that, the next point
 * is still worth full value at the seam, then the marginal rate decays toward
 * {@link Cache#prestigeFromTradeFalloff} over each further cap-sized band.
 * It never reaches a hard stop; it just slows down hard around the cap.
 */
public final class TradePrestige {

	private TradePrestige() {
	}

	public static double fromTradePower(double tradePower) {
		if (tradePower <= 0 || Cache.prestigePerTradePower <= 0) {
			return 0.0;
		}
		return diminish(
				tradePower * Cache.prestigePerTradePower,
				Cache.prestigeFromTradeSoftCap,
				Cache.prestigeFromTradeFalloff);
	}

	static double diminish(double linear, double softCap, double falloff) {
		if (linear <= 0) {
			return 0.0;
		}
		if (softCap <= 0 || falloff >= 1.0) {
			return linear;
		}
		if (linear <= softCap) {
			return linear;
		}
		if (falloff <= 0) {
			return softCap;
		}
		double extra = linear - softCap;
		double extraPrestige = softCap * (Math.pow(falloff, extra / softCap) - 1.0) / Math.log(falloff);
		return softCap + extraPrestige;
	}
}
