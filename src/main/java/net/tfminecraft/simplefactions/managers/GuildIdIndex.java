package net.tfminecraft.simplefactions.managers;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;

/**
 * Every guild by id, so {@code FactionManager.getGuildByString} does not walk every realm's guilds
 * on each call. Dowsing asks for a node's guild several times per node on every node pass; on Main
 * that walk cost 5.3 s an hour.
 *
 * <p>The index answers what the walk answers: the first guild, in realm order and then in
 * {@link GuildHandler#getGuilds()} order, whose id matches ignoring case. It is filled by that same
 * walk and used only while the walk would still meet the same guilds: the same guild handlers in
 * the same order, and no guild added to or removed from any handler since
 * ({@link GuildHandler#changes()}). A guild's id never changes. Whatever the index cannot answer
 * for goes to the walk: a null id, a call off the server thread (async previews read a copy), a
 * missing realm or handler, and a guild without an id.
 */
final class GuildIdIndex {
	/** Read and replaced on the server thread only. */
	private static GuildIdIndex latest;

	private final long changes = GuildHandler.changes();
	private final GuildHandler[] handlers;
	/** Ordered the way {@code equalsIgnoreCase} compares, so no lookup needs a lower-cased copy. */
	private final Map<String, Guild> guilds = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	private GuildIdIndex(int realms) {
		handlers = new GuildHandler[realms];
	}

	static Guild find(List<Faction> factions, String id) {
		if (id == null || !onServerThread()) return walk(factions, id);
		if (latest == null || !latest.current(factions)) latest = build(factions);
		if (latest == null) return walk(factions, id);
		Guild guild = latest.guilds.get(id);
		// equalsIgnoreCase stays the judge of a match, as in the walk.
		return guild == null || guild.getId().equalsIgnoreCase(id) ? guild : walk(factions, id);
	}

	private static Guild walk(List<Faction> factions, String id) {
		for (Faction f : factions) {
			Guild guild = f.getGuildHandler().findGuild(id);
			if (guild != null) return guild;
		}
		return null;
	}

	private boolean current(List<Faction> factions) {
		if (changes != GuildHandler.changes() || handlers.length != factions.size()) return false;
		for (int i = 0; i < handlers.length; i++) {
			Faction faction = factions.get(i);
			if (faction == null || faction.getGuildHandler() != handlers[i]) return false;
		}
		return true;
	}

	/** Null when the walk would meet something only it can answer for. */
	private static GuildIdIndex build(List<Faction> factions) {
		GuildIdIndex index = new GuildIdIndex(factions.size());
		for (int i = 0; i < index.handlers.length; i++) {
			Faction faction = factions.get(i);
			GuildHandler handler = faction == null ? null : faction.getGuildHandler();
			if (handler == null) return null;
			index.handlers[i] = handler;
			for (Guild guild : handler.getGuilds()) {
				String id = guild.getId();
				if (id == null) return null;
				// The first guild the walk meets wins, as it does there.
				index.guilds.putIfAbsent(id, guild);
			}
		}
		return index;
	}

	private static boolean onServerThread() {
		return Bukkit.getServer() != null && Bukkit.isPrimaryThread();
	}
}
