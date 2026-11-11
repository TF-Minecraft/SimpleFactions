package net.tfminecraft.simplefactions.war.wounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns one side's deaths in one battle into wounds, in three steps:
 * <ol>
 * <li>between forces, in proportion to the lives each brought;</li>
 * <li>inside a force, militia first, then its other regiments in proportion;</li>
 * <li>inside a regiment, law and paid slots in proportion.</li>
 * </ol>
 * No force takes more than it brought, so deaths beyond the pool (staff-added lives, deaths at
 * zero lives) cost nobody. The result only depends on its inputs.
 */
public final class WoundAllocator {
	private WoundAllocator() {}

	public static List<Wound> allocate(int deaths, List<BattleForce> forces) {
		List<Wound> wounds = new ArrayList<>();
		if (deaths <= 0 || forces == null || forces.isEmpty()) {
			return wounds;
		}
		long[] forceLives = new long[forces.size()];
		for (int i = 0; i < forces.size(); i++) {
			forceLives[i] = forces.get(i).lives();
		}
		long[] forceShares = share(deaths, forceLives);
		for (int i = 0; i < forces.size(); i++) {
			if (forceShares[i] > 0) {
				allocateForce(forces.get(i), forceShares[i], wounds);
			}
		}
		return wounds;
	}

	private static void allocateForce(BattleForce force, long share, List<Wound> wounds) {
		List<ForcePart> parts = force.parts();
		long[] militia = new long[parts.size()];
		long[] others = new long[parts.size()];
		long militiaLives = 0;
		for (int i = 0; i < parts.size(); i++) {
			long lives = (long) parts.get(i).slots() * force.livesPerSlot();
			if (parts.get(i).militia()) {
				militia[i] = lives;
				militiaLives += lives;
			} else {
				others[i] = lives;
			}
		}
		long onMilitia = Math.min(share, militiaLives);
		long[] militiaShares = share(onMilitia, militia);
		long[] otherShares = share(share - onMilitia, others);
		for (int i = 0; i < parts.size(); i++) {
			long partShare = militiaShares[i] + otherShares[i];
			if (partShare <= 0) {
				continue;
			}
			ForcePart part = parts.get(i);
			long[] split = share(partShare, new long[] {
					(long) part.lawSlots() * force.livesPerSlot(),
					(long) part.paidSlots() * force.livesPerSlot() });
			wounds.add(new Wound(force.kind(), force.ownerId(), part.regimentId(), (int) split[0], (int) split[1]));
		}
	}

	/**
	 * Splits {@code total} over {@code weights} by largest remainder, never giving an entry more
	 * than its weight. Ties go to the earlier entry, so the split is the same every time.
	 */
	public static long[] share(long total, long[] weights) {
		long[] shares = new long[weights.length];
		long weightSum = 0;
		for (long weight : weights) {
			weightSum += weight;
		}
		long amount = Math.min(Math.max(0, total), weightSum);
		if (amount == 0) {
			return shares;
		}
		double[] remainders = new double[weights.length];
		long assigned = 0;
		for (int i = 0; i < weights.length; i++) {
			double exact = (double) amount * weights[i] / weightSum;
			shares[i] = (long) Math.floor(exact);
			remainders[i] = exact - shares[i];
			assigned += shares[i];
		}
		for (long left = amount - assigned; left > 0; left--) {
			int best = -1;
			for (int i = 0; i < weights.length; i++) {
				if (shares[i] < weights[i] && (best < 0 || remainders[i] > remainders[best])) {
					best = i;
				}
			}
			shares[best]++;
			remainders[best] = -1;
		}
		return shares;
	}
}
