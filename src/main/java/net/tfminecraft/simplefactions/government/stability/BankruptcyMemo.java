package net.tfminecraft.simplefactions.government.stability;

import java.util.IdentityHashMap;
import java.util.Map;

import org.bukkit.Bukkit;

import net.tfminecraft.denareconomy.DenarEconomy;
import net.tfminecraft.denareconomy.ledger.Ledger;
import net.tfminecraft.simplefactions.guild.Guild;

/**
 * Whether each realm guild is bankrupt, read once per trade recalculation on the server thread.
 * The flood asks for a province owner's stability once per foreign guild it reaches, and each ask
 * reads the realm guild's treasury from the ledger. During a run on the server thread nothing
 * else can post to a treasury or remove a bank, and nothing in a recalculation does, so every ask
 * in that run gets the same answer. Everything else in stability, including unrest that decays
 * over time, is still read live on every ask.
 *
 * <p>Runs off the server thread (async previews) read live as before: the server can change a
 * guild's bank while they run. If the ledger is closed, every ask reads live too, because a
 * closed ledger reads every balance as zero.
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
		if (onServerThread()) RUN.set(new IdentityHashMap<>());
		else RUN.remove();
		return run;
	}

	public static void end(Run run) {
		if (run.outer == null) RUN.remove();
		else RUN.set(run.outer);
	}

	/** {@link Guild#isBankrupt()}, read once per guild in a server-thread run and live otherwise. */
	public static boolean isBankrupt(Guild guild) {
		Map<Guild, Boolean> run = RUN.get();
		if (run == null) return guild.isBankrupt();
		if (!ledgerOpen()) {
			// A failed read closes the ledger for good, and answers from before it are stale.
			run.clear();
			return guild.isBankrupt();
		}
		Boolean bankrupt = run.get(guild);
		if (bankrupt == null) {
			bankrupt = guild.isBankrupt();
			// Keep only answers the ledger stayed open for; this very read may have closed it.
			if (ledgerOpen()) run.put(guild, bankrupt);
		}
		return bankrupt;
	}

	private static boolean onServerThread() {
		return Bukkit.getServer() != null && Bukkit.isPrimaryThread();
	}

	private static boolean ledgerOpen() {
		Ledger ledger = DenarEconomy.getLedger();
		return ledger != null && ledger.isOpen();
	}
}
