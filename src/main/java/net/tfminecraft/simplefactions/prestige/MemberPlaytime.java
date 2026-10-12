package net.tfminecraft.simplefactions.prestige;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.Characters;

/**
 * The playtime half of the Members prestige term. Headcount alone rewards mass
 * recruitment, so each member also contributes for the time their character has
 * actually spent online.
 *
 * <p>Playtime arrives through a {@link Probe}. The default probe knows nobody;
 * production installs the shared character lookup with {@link #useSharedIdentity()}.
 *
 * <p>This is read from {@code Faction.updatePrestige()}, which runs for every
 * faction on every bank mutation, so a probe must answer from memory. Never do
 * disk or network work behind it. {@link #withinPass(Runnable)} asks the probe
 * once per member for a whole prestige pass.
 */
public final class MemberPlaytime {

	public interface Probe {
		/** Knows nobody, so the whole term stays out of the way. */
		Probe NONE = player -> null;

		/** Online seconds for this player, or null when the player is unknown. */
		Integer secondsFor(String player);
	}

	private static Probe probe = Probe.NONE;
	/** Answers for the running prestige pass, including unknown members. */
	private static final ThreadLocal<Map<String, Integer>> pass = new ThreadLocal<>();

	private MemberPlaytime() {
	}

	public static void setProbe(Probe newProbe) {
		probe = newProbe == null ? Probe.NONE : newProbe;
	}

	public static void reset() {
		probe = Probe.NONE;
	}

	/** Uses ready online characters and cached offline accounts' last-played characters. */
	public static void useSharedIdentity() {
		setProbe(MemberPlaytime::sharedSecondsFor);
	}

	private static Integer sharedSecondsFor(String name) {
		if (Bukkit.getServer() == null || !Characters.available()) return null;
		Player online = Bukkit.getPlayerExact(name);
		CharacterInfo info;
		if (online != null) {
			CharacterSession session = Characters.currentSession(online).orElse(null);
			if (session == null || !session.ref().ownerId().equals(online.getUniqueId())) return null;
			info = Characters.info(session.ref().characterId()).orElse(null);
			if (info == null || !info.ref().equals(session.ref()) || !online.isOnline()
					|| Bukkit.getPlayer(online.getUniqueId()) != online || !Characters.isCurrent(session)) return null;
		} else {
			OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
			if (offline == null) return null;
			info = Characters.lastPlayed(offline.getUniqueId()).orElse(null);
			if (info == null || !info.ref().ownerId().equals(offline.getUniqueId())) return null;
		}
		return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, info.playtimeSeconds()));
	}

	/** False while the default probe is installed, so unconfigured servers can tell. */
	public static boolean isTracked() {
		return probe != Probe.NONE;
	}

	public static Integer secondsFor(String player) {
		if (player == null || player.isBlank()) return null;
		Map<String, Integer> answers = pass.get();
		if (answers == null) return probe.secondsFor(player);
		if (answers.containsKey(player)) return answers.get(player);
		Integer seconds = probe.secondsFor(player);
		answers.put(player, seconds);
		return seconds;
	}

	/** Runs a prestige pass that looks each member up once; nested passes share the outer answers. */
	public static void withinPass(Runnable work) {
		if (pass.get() != null) {
			work.run();
			return;
		}
		pass.set(new HashMap<>());
		try {
			work.run();
		} finally {
			pass.remove();
		}
	}

	/**
	 * Summed contribution across a roster. Members the probe does not recognise are
	 * skipped rather than counted as fresh, so a faction is not paid for names that
	 * have never held a character.
	 */
	public static double totalFor(Collection<String> members) {
		if (members == null || members.isEmpty()) return 0.0;
		double total = 0.0;
		for (String member : members) {
			Integer seconds = secondsFor(member);
			if (seconds == null) continue;
			total += PlaytimePrestige.contribution(seconds);
		}
		return total;
	}
}
