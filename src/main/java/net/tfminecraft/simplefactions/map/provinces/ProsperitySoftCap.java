package net.tfminecraft.simplefactions.map.provinces;

import net.tfminecraft.simplefactions.Cache;

/**
 * Slows a province's prosperity past {@link Cache#prosperitySoftCap}.
 *
 * <p>Up to the cap nothing changes. Past it, the excess grows with a square
 * root, so the next point is worth full value at the seam and less after:
 * half at cap + 3 x scale, a third at cap + 8 x scale. There is no ceiling;
 * it just gets harder and harder to add prosperity.
 */
public final class ProsperitySoftCap {

	private ProsperitySoftCap() {
	}

	public static double apply(double prosperity) {
		return diminish(prosperity, Cache.prosperitySoftCap, Cache.prosperitySoftCapScale);
	}

	static double diminish(double prosperity, double softCap, double scale) {
		if (softCap <= 0 || scale <= 0 || prosperity <= softCap) {
			return prosperity;
		}
		double extra = prosperity - softCap;
		return softCap + 2.0 * scale * (Math.sqrt(1.0 + extra / scale) - 1.0);
	}
}
