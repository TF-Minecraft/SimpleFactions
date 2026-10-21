package net.tfminecraft.simplefactions.government.stability;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.tfminecraft.simplefactions.objects.Faction;

/**
 * One stability report per realm for the length of a trade recalculation. The flood asks for a
 * province owner's stability once per foreign guild it reaches, and each ask reads the realm
 * guild's treasury. Nothing the flood writes feeds stability, so every ask in one run gets the
 * same report. Runs belong to one thread, so an async preview never sees the server's reports.
 */
public final class StabilityMemo {
	private static final ThreadLocal<Map<Faction, StabilityReport>> RUN = new ThreadLocal<>();

	private StabilityMemo() {}

	/** What {@link #begin} replaced, so {@link #end} can put it back. */
	public static final class Run {
		private final Map<Faction, StabilityReport> outer;

		private Run(Map<Faction, StabilityReport> outer) {
			this.outer = outer;
		}
	}

	/**
	 * Starts a run on this thread. A nested run, such as a preview with its own law overlay,
	 * gets its own reports rather than the outer run's.
	 */
	public static Run begin() {
		Run run = new Run(RUN.get());
		RUN.set(new IdentityHashMap<>());
		return run;
	}

	public static void end(Run run) {
		if (run.outer == null) RUN.remove();
		else RUN.set(run.outer);
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
