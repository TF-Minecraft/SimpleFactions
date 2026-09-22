package me.Plugins.SimpleFactions.prestige;

import java.util.Collection;

/**
 * The playtime half of the Members prestige term. Headcount alone rewards mass
 * recruitment, so each member also contributes for the time their character has
 * actually spent online.
 *
 * <p>Playtime lives in RPCharacters, so it arrives through a {@link Probe}. The
 * default probe knows nobody, which keeps the term at zero for tests and for
 * servers running without that plugin. Production swaps in
 * {@link RpCharactersPlaytimeProbe}.
 *
 * <p>This is read from {@code Faction.updatePrestige()}, which runs for every
 * faction on every bank mutation, so a probe must answer from memory. Never do
 * disk or network work behind it.
 */
public final class MemberPlaytime {

	public interface Probe {
		/** Knows nobody, so the whole term stays out of the way. */
		Probe NONE = player -> null;

		/** Online seconds for this player, or null when the player is unknown. */
		Integer secondsFor(String player);
	}

	private static Probe probe = Probe.NONE;

	private MemberPlaytime() {
	}

	public static void setProbe(Probe newProbe) {
		probe = newProbe == null ? Probe.NONE : newProbe;
	}

	public static void reset() {
		probe = Probe.NONE;
	}

	/** False while the default probe is installed, so unconfigured servers can tell. */
	public static boolean isTracked() {
		return probe != Probe.NONE;
	}

	public static Integer secondsFor(String player) {
		if (player == null || player.isBlank()) return null;
		return probe.secondsFor(player);
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
