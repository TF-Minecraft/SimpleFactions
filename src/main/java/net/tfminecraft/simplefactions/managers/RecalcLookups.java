package net.tfminecraft.simplefactions.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Province owners and guilds by id, looked up once per trade recalculation on the server thread.
 * The flood asks for a province's owner and for guilds by id many times per province, and each ask
 * walks every faction: {@code TitleManager.getByProvince} scans each realm's province list and
 * {@code FactionManager.getGuildByString} each realm's guilds. On Main that was over half of what
 * was left of a recalculation after {@code StabilityMemo}. A recalculation only writes trade,
 * production, income and prosperity, so who owns a province and which guild has an id cannot
 * change during one, except that a missing realm guild may be created, so guild misses are not kept.
 *
 * <p>Same lifecycle as {@code StabilityMemo}: a nested run gets its own lookups, and runs off the
 * server thread (async previews) read live.
 */
public final class RecalcLookups {
	private static final ThreadLocal<Lookups> RUN = new ThreadLocal<>();

	private RecalcLookups() {}

	private static final class Lookups {
		private final Map<Integer, Faction> owners = new HashMap<>();
		private final Map<String, Guild> guilds = new HashMap<>();
	}

	/** What {@link #begin} replaced, so {@link #end} can put it back. */
	public static final class Run {
		private final Lookups outer;

		private Run(Lookups outer) {
			this.outer = outer;
		}
	}

	public static Run begin() {
		Run run = new Run(RUN.get());
		if (onServerThread()) RUN.set(new Lookups());
		else RUN.remove();
		return run;
	}

	public static void end(Run run) {
		if (run.outer == null) RUN.remove();
		else RUN.set(run.outer);
	}

	static Faction owner(int province, IntFunction<Faction> find) {
		Lookups run = RUN.get();
		if (run == null) return find.apply(province);
		if (run.owners.containsKey(province)) return run.owners.get(province);
		Faction owner = find.apply(province);
		run.owners.put(province, owner);
		return owner;
	}

	static Guild guild(String id, Function<String, Guild> find) {
		Lookups run = RUN.get();
		if (run == null || id == null) return find.apply(id);
		Guild guild = run.guilds.get(id);
		if (guild != null) return guild;
		guild = find.apply(id);
		// A miss is not kept: a realm's own guild can be created during a run (getOrCreateMainGuild).
		if (guild != null) run.guilds.put(id, guild);
		return guild;
	}

	private static boolean onServerThread() {
		return Bukkit.getServer() != null && Bukkit.isPrimaryThread();
	}
}
