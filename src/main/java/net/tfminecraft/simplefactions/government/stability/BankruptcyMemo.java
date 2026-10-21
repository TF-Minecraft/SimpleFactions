package net.tfminecraft.simplefactions.government.stability;

import java.util.IdentityHashMap;
import java.util.Map;

import net.tfminecraft.simplefactions.guild.Guild;

/**
 * Whether each realm guild is bankrupt, read once per trade recalculation. The flood asks for a
 * province owner's stability once per foreign guild it reaches, and each ask reads the realm
 * guild's treasury from the ledger. A treasury only changes through a ledger posting, and nothing
 * in a recalculation posts one, so every ask in one run gets the same answer. Everything else in
 * stability, including unrest that decays over time, is still read live on every ask.
 * Runs belong to one thread, so an async preview never sees the server's answers.
 */
public final class BankruptcyMemo {
	private static final ThreadLocal<Map<Guild, Boolean>> RUN = new ThreadLocal<>();

	private BankruptcyMemo() {}

	/** What {@link #begin} replaced, so {@link #end} can put it back. */
	public static final class Run {
		private final Map<Guild, Boolean> outer;

		private Run(Map<Guild, Boolean> outer) {
			this.outer = outer;
		}
	}

	/** Starts a run on this thread. A nested run, such as a preview, gets its own answers. */
	public static Run begin() {
		Run run = new Run(RUN.get());
		RUN.set(new IdentityHashMap<>());
		return run;
	}

	public static void end(Run run) {
		if (run.outer == null) RUN.remove();
		else RUN.set(run.outer);
	}

	/** {@link Guild#isBankrupt()}, read once per guild in a run and live outside one. */
	public static boolean isBankrupt(Guild guild) {
		Map<Guild, Boolean> run = RUN.get();
		if (run == null) return guild.isBankrupt();
		Boolean bankrupt = run.get(guild);
		if (bankrupt == null) {
			bankrupt = guild.isBankrupt();
			run.put(guild, bankrupt);
		}
		return bankrupt;
	}
}
