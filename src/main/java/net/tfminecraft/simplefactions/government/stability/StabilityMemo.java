package net.tfminecraft.simplefactions.government.stability;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.tfminecraft.simplefactions.objects.Faction;

/**
 * One stability report per realm for the length of a trade recalculation. The flood asks for a
 * province owner's stability once per foreign guild it reaches, and each ask reads the realm
 * guild's treasury. Nothing the flood writes feeds stability, so every ask in one run gets the
 * same report.
 */
public final class StabilityMemo {
	private static final ThreadLocal<Map<Faction, StabilityReport>> RUN = new ThreadLocal<>();

	private StabilityMemo() {}

	/** Starts a run on this thread. False when one is already running, so only the outer run ends it. */
	public static boolean begin() {
		if (RUN.get() != null) return false;
		RUN.set(new IdentityHashMap<>());
		return true;
	}

	public static void end(boolean began) {
		if (began) RUN.remove();
	}

	/** The faction's report for this run, or a fresh one outside a run. */
	public static StabilityReport report(Faction faction, Supplier<StabilityReport> assess) {
		Map<Faction, StabilityReport> run = RUN.get();
		if (run == null || faction == null) return assess.get();
		StabilityReport report = run.get(faction);
		if (report == null) {
			// get/put rather than computeIfAbsent: assessing one realm may ask about another.
			report = assess.get();
			run.put(faction, report);
		}
		return report;
	}
}
