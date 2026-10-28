package net.tfminecraft.simplefactions.government.stability;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Each faction's stability report, worked out once per trade recalculation on the server thread.
 * The flood asks for a province owner's stability (for foreign trade, tax efficiency and more)
 * every time it reaches that province, and each ask rebuilt the whole report: laws, guild bodies,
 * vassals and espionage effects. On Main that was about 40 % of a recalculation, which freezes the
 * server for over a second. A recalculation only writes trade, production, income and prosperity,
 * none of which a report reads, so every ask in one run gets the same report. Effects that expire
 * while the run is going (a run takes well under a second) are read as they were at its first ask.
 *
 * <p>Runs off the server thread (async previews) read live as before, like {@link BankruptcyMemo}.
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

	/** Starts a run on this thread. A nested run, such as a preview, gets its own reports. */
	public static Run begin() {
		Run run = new Run(RUN.get());
		if (onServerThread()) RUN.set(new IdentityHashMap<>());
		else RUN.remove();
		return run;
	}

	public static void end(Run run) {
		if (run.outer == null) RUN.remove();
		else RUN.set(run.outer);
	}

	/** The faction's report, built once per faction in a server-thread run and live otherwise. */
	static StabilityReport report(Faction faction, Supplier<StabilityReport> build) {
		Map<Faction, StabilityReport> run = RUN.get();
		if (run == null || faction == null) return build.get();
		StabilityReport report = run.get(faction);
		if (report == null) {
			report = build.get();
			run.put(faction, report);
		}
		return report;
	}

	private static boolean onServerThread() {
		return Bukkit.getServer() != null && Bukkit.isPrimaryThread();
	}
}
