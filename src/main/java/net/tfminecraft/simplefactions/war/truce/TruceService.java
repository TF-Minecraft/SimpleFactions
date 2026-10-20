package net.tfminecraft.simplefactions.war.truce;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;

/**
 * Truces between factions that fought on opposite sides of a finished war, and the
 * cooldown a civil war leaves on its host. Both run on wall-clock time and expire
 * on their own; expired entries are dropped on load and whenever they are looked up.
 */
public final class TruceService {
	private static final String FILE_NAME = "truces.json";

	/** One truce. Ids are stored lowercase and in sorted order, so a pair has one entry. */
	public record Truce(String firstId, String secondId, Instant until, int warId) {
		public String otherId(String factionId) {
			return firstId.equalsIgnoreCase(factionId) ? secondId : firstId;
		}
	}

	public static final class SavedTruce {
		public String first;
		public String second;
		public String until;
		public int warId;
	}

	public static final class SavedCooldown {
		public String until;
		public int warId;
	}

	public static final class SavedState {
		public List<SavedTruce> truces = new ArrayList<>();
		/** Host faction id to the end of its civil war cooldown. */
		public Map<String, SavedCooldown> civilWarCooldowns = new HashMap<>();
	}

	private static final Map<String, Truce> truces = new LinkedHashMap<>();
	private static final Map<String, SavedCooldown> cooldowns = new HashMap<>();
	private static Clock clock = Clock.systemUTC();
	private static File file;
	private static boolean saveBlocked;

	private TruceService() {}

	/** Uses {@code folder/truces.json} for {@link #load()} and {@link #save()}. */
	public static synchronized void bind(File folder) {
		file = folder == null ? null : new File(folder, FILE_NAME);
	}

	public static synchronized void load() {
		Map<String, Truce> nextTruces = new LinkedHashMap<>();
		Map<String, SavedCooldown> nextCooldowns = new HashMap<>();
		try {
			if (file != null && file.exists()) {
				SavedState state = JsonUtil.readJson(file, SavedState.class);
				if (state == null) throw new IOException("Truce file contains no state");
				if (state.truces != null) {
					for (SavedTruce saved : state.truces) {
						if (saved == null) continue;
						Instant until = parse(saved.until);
						String key = key(saved.first, saved.second);
						if (until == null || key == null) continue;
						String[] ids = key.split("\\|", 2);
						nextTruces.put(key, new Truce(ids[0], ids[1], until, saved.warId));
					}
				}
				if (state.civilWarCooldowns != null) {
					state.civilWarCooldowns.forEach((id, saved) -> {
						if (id == null || saved == null || parse(saved.until) == null) return;
						nextCooldowns.put(id.toLowerCase(Locale.ROOT), saved);
					});
				}
			}
		} catch (IOException | RuntimeException ex) {
			// Keep the unreadable file for staff; truces made this run are not saved over it.
			saveBlocked = true;
			log("Could not read truces", ex);
			return;
		}
		truces.clear();
		truces.putAll(nextTruces);
		cooldowns.clear();
		cooldowns.putAll(nextCooldowns);
		saveBlocked = false;
		purge();
	}

	public static synchronized void save() {
		if (saveBlocked || file == null) return;
		try {
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) parent.mkdirs();
			SavedState state = new SavedState();
			for (Truce truce : truces.values()) {
				SavedTruce saved = new SavedTruce();
				saved.first = truce.firstId();
				saved.second = truce.secondId();
				saved.until = truce.until().toString();
				saved.warId = truce.warId();
				state.truces.add(saved);
			}
			state.civilWarCooldowns.putAll(cooldowns);
			JsonUtil.writeJsonAtomic(file, state);
		} catch (IOException ex) {
			log("Could not save truces", ex);
		}
	}

	/**
	 * Records truces for a war that is ending. Must run while the war is still active,
	 * before its sides are torn down. Staff ending a war leaves no truce behind.
	 */
	public static synchronized void onWarEnded(War war, WarEndReason reason) {
		if (war == null || reason == null || reason == WarEndReason.ADMIN_END) return;
		Duration length = Duration.ofDays(Math.max(0, Cache.warTruceDays));
		if (length.isZero()) return;
		Instant until = clock.instant().plus(length);
		if (CivilWarBorderLock.isCivilWar(war)) {
			// The rebel side is a temporary faction, so the host gets a cooldown instead.
			String hostId = civilWarHostId(war);
			if (hostId != null) recordCivilWarCooldown(hostId, until, war.getId());
		} else {
			for (Faction attacker : BattleSideMembers.collectParticipatingFactions(war.getAttackers())) {
				for (Faction defender : BattleSideMembers.collectParticipatingFactions(war.getDefenders())) {
					record(attacker.getId(), defender.getId(), until, war.getId());
				}
			}
		}
		save();
	}

	/** Adds a truce, keeping the later expiry when the pair already has one. */
	public static synchronized boolean record(String firstId, String secondId, Instant until, int warId) {
		String key = key(firstId, secondId);
		if (key == null || until == null) return false;
		Truce existing = truces.get(key);
		if (existing != null && !existing.until().isBefore(until)) return false;
		String[] ids = key.split("\\|", 2);
		truces.put(key, new Truce(ids[0], ids[1], until, warId));
		return true;
	}

	public static synchronized void recordCivilWarCooldown(String hostId, Instant until, int warId) {
		if (hostId == null || hostId.isBlank() || until == null) return;
		String id = hostId.toLowerCase(Locale.ROOT);
		SavedCooldown existing = cooldowns.get(id);
		Instant current = existing == null ? null : parse(existing.until);
		if (current != null && !current.isBefore(until)) return;
		SavedCooldown cooldown = new SavedCooldown();
		cooldown.until = until.toString();
		cooldown.warId = warId;
		cooldowns.put(id, cooldown);
	}

	public static boolean hasTruce(Faction a, Faction b) {
		return getTruce(a, b) != null;
	}

	/** @return when the truce between the two ends, or null when they have none */
	public static Instant getTruce(Faction a, Faction b) {
		if (a == null || b == null) return null;
		Truce truce = find(a.getId(), b.getId());
		return truce == null ? null : truce.until();
	}

	public static synchronized Truce find(String firstId, String secondId) {
		String key = key(firstId, secondId);
		if (key == null) return null;
		Truce truce = truces.get(key);
		if (truce == null) return null;
		if (!truce.until().isAfter(clock.instant())) {
			truces.remove(key);
			save();
			return null;
		}
		return truce;
	}

	/** @return time left on the truce, or {@link Duration#ZERO} when there is none */
	public static Duration remaining(Faction a, Faction b) {
		return remainingUntil(getTruce(a, b));
	}

	/** @return when the host may start another civil war, or null when it already may */
	public static synchronized Instant civilWarCooldownUntil(Faction host) {
		if (host == null || host.getId() == null) return null;
		String id = host.getId().toLowerCase(Locale.ROOT);
		SavedCooldown cooldown = cooldowns.get(id);
		if (cooldown == null) return null;
		Instant until = parse(cooldown.until);
		if (until == null || !until.isAfter(clock.instant())) {
			cooldowns.remove(id);
			save();
			return null;
		}
		return until;
	}

	public static Duration civilWarCooldownRemaining(Faction host) {
		return remainingUntil(civilWarCooldownUntil(host));
	}

	public static boolean clear(Faction a, Faction b) {
		return a != null && b != null && clear(a.getId(), b.getId());
	}

	public static synchronized boolean clear(String firstId, String secondId) {
		String key = key(firstId, secondId);
		if (key == null || truces.remove(key) == null) return false;
		save();
		return true;
	}

	/** Active truces involving the faction, soonest to expire first. */
	public static List<Truce> listFor(Faction faction) {
		if (faction == null || faction.getId() == null) return List.of();
		return list(faction.getId());
	}

	/** Active truces, soonest to expire first; a null id lists every truce. */
	public static synchronized List<Truce> list(String factionId) {
		purge();
		List<Truce> result = new ArrayList<>();
		for (Truce truce : truces.values()) {
			if (factionId == null
					|| truce.firstId().equalsIgnoreCase(factionId)
					|| truce.secondId().equalsIgnoreCase(factionId)) {
				result.add(truce);
			}
		}
		result.sort(Comparator.comparing(Truce::until));
		return result;
	}

	/** Time left until the instant, never negative. */
	public static synchronized Duration remainingUntil(Instant until) {
		if (until == null) return Duration.ZERO;
		Duration left = Duration.between(clock.instant(), until);
		return left.isNegative() ? Duration.ZERO : left;
	}

	/** {@code 6d 4h} for a day or more, {@code 3h 12m} for an hour or more, else {@code 12m}. */
	public static String formatRemaining(Duration remaining) {
		if (remaining == null || remaining.isNegative()) remaining = Duration.ZERO;
		// Round up to the minute so a truce never reads as 0m while it still holds.
		long minutes = (remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0) + 59) / 60;
		long days = minutes / (24 * 60);
		long hours = (minutes / 60) % 24;
		long mins = minutes % 60;
		if (days > 0) return days + "d " + hours + "h";
		if (hours > 0) return hours + "h " + mins + "m";
		return mins + "m";
	}

	static String civilWarHostId(War war) {
		return war.getCivilWarSnapshot() != null ? war.getCivilWarSnapshot().getHostFactionId() : null;
	}

	private static void purge() {
		Instant now = clock.instant();
		boolean changed = truces.values().removeIf(truce -> !truce.until().isAfter(now));
		changed |= cooldowns.values().removeIf(cooldown -> {
			Instant until = parse(cooldown.until);
			return until == null || !until.isAfter(now);
		});
		if (changed) save();
	}

	private static String key(String firstId, String secondId) {
		if (firstId == null || secondId == null || firstId.isBlank() || secondId.isBlank()) return null;
		String first = firstId.toLowerCase(Locale.ROOT);
		String second = secondId.toLowerCase(Locale.ROOT);
		if (first.equals(second)) return null;
		return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
	}

	private static Instant parse(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			return Instant.parse(value);
		} catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static void log(String message, Throwable error) {
		SimpleFactions plugin = SimpleFactions.getInstance();
		Logger logger = plugin != null ? plugin.getLogger() : Logger.getLogger(TruceService.class.getName());
		logger.log(Level.WARNING, message, error);
	}

	/** Test hook: the clock truces are measured against. Null restores the system clock. */
	public static synchronized void useClock(Clock next) {
		clock = next == null ? Clock.systemUTC() : next;
	}

	/** Test hook: forgets every truce, the file and the clock. */
	public static synchronized void reset() {
		truces.clear();
		cooldowns.clear();
		file = null;
		saveBlocked = false;
		clock = Clock.systemUTC();
	}
}
