package me.Plugins.SimpleFactions.prestige;

import me.Plugins.SimpleFactions.Cache;

/**
 * What one member's time on the server is worth in prestige.
 *
 * <p>The curve doubles every 100 online hours, so a veteran outweighs a fresh
 * recruit without a large roster of newcomers being worthless. It flattens at
 * {@link Cache#maxPlaytimePrestigeExponent} so no single member can carry a
 * nation forever: at the default 5 that is 32, reached at 500 hours.
 */
public final class PlaytimePrestige {

	private static final double SECONDS_PER_HOUR = 3600.0;
	private static final double HOURS_PER_DOUBLING = 100.0;

	private PlaytimePrestige() {
	}

	/** Prestige for a member with this much online time. A fresh character is worth 2^0. */
	public static double contribution(long playtimeSeconds) {
		if (playtimeSeconds <= 0) {
			return 1.0;
		}
		double hours = playtimeSeconds / SECONDS_PER_HOUR;
		double exponent = Math.min(hours / HOURS_PER_DOUBLING, Cache.maxPlaytimePrestigeExponent);
		return Math.pow(2.0, exponent);
	}

	/** The most one member can ever be worth, for readouts and tests. */
	public static double maxContribution() {
		return Math.pow(2.0, Cache.maxPlaytimePrestigeExponent);
	}
}
