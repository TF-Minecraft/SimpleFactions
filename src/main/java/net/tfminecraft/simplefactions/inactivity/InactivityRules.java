package net.tfminecraft.simplefactions.inactivity;

import java.util.List;
import java.util.Locale;

/**
 * Inactivity is a mark, not a kick. After {@link #DAYS_UNTIL_INACTIVE} offline
 * days a member stays in the guild and is shown as inactive. A guild whose
 * real members are all inactive loses 1% of upgrade output every
 * {@link #DECAY_INTERVAL_MILLIS}. A faction in the same state loses 1% prestige
 * on that cadence and, while it holds more land than its prestige pays for,
 * sheds provinces in claim order. The capital is last, and it does go.
 */
public final class InactivityRules {
	public static final int DAYS_UNTIL_INACTIVE = 21;
	public static final long DECAY_INTERVAL_MILLIS = 4L * 60L * 60L * 1000L;
	public static final int DECAY_STEP_PERCENT = 1;
	public static final int DECAY_CAP_PERCENT = 100;

	private InactivityRules() {}

	public static boolean isTrackedMember(String name) {
		if (name == null || name.isBlank()) return false;
		return !name.toLowerCase(Locale.ROOT).startsWith("dummy_");
	}

	public static boolean isInactive(int daysOffline) {
		return daysOffline >= DAYS_UNTIL_INACTIVE;
	}

	/** True when every tracked member is inactive. A group with no real members is not inactive. */
	public static boolean isFullyInactive(int trackedMembers, int inactiveMembers) {
		return trackedMembers > 0 && inactiveMembers >= trackedMembers;
	}

	public static int clampPercent(int percent) {
		return Math.max(0, Math.min(DECAY_CAP_PERCENT, percent));
	}

	/** Share of upgrade output still applied. 0% debuff keeps full output; 100% stops it. */
	public static double outputFactor(int debuffPercent) {
		return (DECAY_CAP_PERCENT - clampPercent(debuffPercent)) / 100.0;
	}

	/**
	 * An inactive faction has too many provinces when it is over the normal cap,
	 * or when the only province left is the capital and prestige no longer covers
	 * one province. That last case is what lets the capital go.
	 */
	public static boolean hasTooManyProvinces(double prestige, int provinceCount, double provinceCost, boolean overProvinceCap) {
		if (provinceCount <= 0) return false;
		if (Double.isNaN(prestige) || Double.isInfinite(prestige)) prestige = 0;
		if (provinceCount == 1) {
			if (provinceCost <= 0 || Double.isNaN(provinceCost)) return false;
			return prestige < provinceCost;
		}
		return overProvinceCap;
	}

	/** Oldest claim first. The capital is skipped until it is the only province left. */
	public static Integer nextProvinceToLose(List<Integer> claimOrder, int capital) {
		if (claimOrder == null || claimOrder.isEmpty()) return null;
		Integer capitalId = null;
		for (Integer id : claimOrder) {
			if (id == null) continue;
			if (id == capital) {
				capitalId = id;
				continue;
			}
			return id;
		}
		return capitalId;
	}
}
