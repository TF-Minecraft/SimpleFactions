package net.tfminecraft.simplefactions.diplomacy.coalition;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.JsonUtil;

/**
 * Every coalition and every faction's leave cooldown, saved to {@code Cache/coalitions.json}
 * after each change. An unreadable file is kept for staff and never saved over.
 */
public final class CoalitionRegistry {
	private static final String FILE_NAME = "coalitions.json";

	public static final class SavedCoalition {
		public String id;
		public String name;
		public String leader;
		public String target;
		public boolean offensive;
		public Map<String, Long> members = new LinkedHashMap<>();
		public Map<String, Long> invites = new LinkedHashMap<>();
		public List<String> banner;
		public long createdAt;
		public Long targetLostAt;
		public long targetLossPausedMillis;
		public Long singleMemberSince;
	}

	public static final class SavedState {
		public List<SavedCoalition> coalitions = new ArrayList<>();
		/** Faction id to when it may join or create a coalition again. */
		public Map<String, Long> cooldowns = new HashMap<>();
	}

	private static final Map<String, Coalition> coalitions = new LinkedHashMap<>();
	private static final Map<String, Long> cooldowns = new HashMap<>();
	private static Clock clock = Clock.systemUTC();
	private static File file;
	private static boolean saveBlocked;

	private CoalitionRegistry() {}

	public static synchronized void bind(File folder) {
		file = folder == null ? null : new File(folder, FILE_NAME);
	}

	public static synchronized void load() {
		Map<String, Coalition> next = new LinkedHashMap<>();
		Map<String, Long> nextCooldowns = new HashMap<>();
		try {
			if (file != null && file.exists()) {
				SavedState state = JsonUtil.readJson(file, SavedState.class);
				if (state == null) throw new IOException("Coalition file contains no state");
				if (state.coalitions != null) {
					for (SavedCoalition saved : state.coalitions) {
						Coalition coalition = restore(saved);
						if (coalition != null) next.put(coalition.getId(), coalition);
					}
				}
				if (state.cooldowns != null) {
					state.cooldowns.forEach((id, until) -> {
						if (id != null && until != null) nextCooldowns.put(id.toLowerCase(Locale.ROOT), until);
					});
				}
			}
		} catch (IOException | RuntimeException ex) {
			saveBlocked = true;
			log("Could not read coalitions", ex);
			return;
		}
		coalitions.clear();
		coalitions.putAll(next);
		cooldowns.clear();
		cooldowns.putAll(nextCooldowns);
		saveBlocked = false;
	}

	public static synchronized void save() {
		if (saveBlocked || file == null) return;
		try {
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) parent.mkdirs();
			SavedState state = new SavedState();
			for (Coalition coalition : coalitions.values()) state.coalitions.add(saved(coalition));
			state.cooldowns.putAll(cooldowns);
			JsonUtil.writeJsonAtomic(file, state);
		} catch (IOException ex) {
			log("Could not save coalitions", ex);
		}
	}

	private static Coalition restore(SavedCoalition saved) {
		if (saved == null || saved.id == null || saved.name == null || saved.leader == null
				|| saved.target == null) {
			return null;
		}
		Coalition coalition = new Coalition(saved.id, saved.name, saved.leader, saved.target, saved.createdAt);
		if (saved.members != null) {
			saved.members.forEach((id, joined) -> {
				if (id != null && joined != null) coalition.addMember(id, joined);
			});
		}
		if (saved.invites != null) {
			saved.invites.forEach((id, until) -> {
				if (id != null && until != null) coalition.invite(id, until);
			});
		}
		coalition.setOffensive(saved.offensive);
		coalition.setBannerPatterns(saved.banner);
		coalition.setTargetLostAt(saved.targetLostAt);
		coalition.setTargetLossPausedMillis(saved.targetLossPausedMillis);
		coalition.setSingleMemberSince(saved.singleMemberSince);
		return coalition;
	}

	private static SavedCoalition saved(Coalition coalition) {
		SavedCoalition saved = new SavedCoalition();
		saved.id = coalition.getId();
		saved.name = coalition.getName();
		saved.leader = coalition.getLeaderId();
		saved.target = coalition.getTargetId();
		saved.offensive = coalition.isOffensive();
		saved.members.putAll(coalition.getMembers());
		saved.invites.putAll(coalition.getInvites());
		saved.banner = coalition.getBannerPatterns();
		saved.createdAt = coalition.getCreatedAt();
		saved.targetLostAt = coalition.getTargetLostAt();
		saved.targetLossPausedMillis = coalition.getTargetLossPausedMillis();
		saved.singleMemberSince = coalition.getSingleMemberSince();
		return saved;
	}

	public static synchronized List<Coalition> all() {
		return new ArrayList<>(coalitions.values());
	}

	public static synchronized Coalition get(String id) {
		return id == null ? null : coalitions.get(id);
	}

	public static synchronized void add(Coalition coalition) {
		coalitions.put(coalition.getId(), coalition);
	}

	public static synchronized boolean remove(Coalition coalition) {
		return coalition != null && coalitions.remove(coalition.getId()) != null;
	}

	public static synchronized String newId() {
		String id;
		do {
			id = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		} while (coalitions.containsKey(id));
		return id;
	}

	/** When the faction may join or create a coalition again, or null when it already may. */
	public static synchronized Long cooldownUntil(String factionId) {
		if (factionId == null) return null;
		String id = factionId.toLowerCase(Locale.ROOT);
		Long until = cooldowns.get(id);
		if (until == null) return null;
		if (until <= now()) {
			cooldowns.remove(id);
			return null;
		}
		return until;
	}

	public static synchronized void setCooldown(String factionId, long until) {
		cooldowns.put(factionId.toLowerCase(Locale.ROOT), until);
	}

	public static synchronized boolean clearCooldown(String factionId) {
		return factionId != null && cooldowns.remove(factionId.toLowerCase(Locale.ROOT)) != null;
	}

	public static synchronized long now() {
		return clock.millis();
	}

	private static void log(String message, Throwable error) {
		SimpleFactions plugin = SimpleFactions.getInstance();
		Logger logger = plugin != null ? plugin.getLogger() : Logger.getLogger(CoalitionRegistry.class.getName());
		logger.log(Level.WARNING, message, error);
	}

	/** Test hook: the clock coalition times are measured against. Null restores the system clock. */
	public static synchronized void useClock(Clock next) {
		clock = next == null ? Clock.systemUTC() : next;
	}

	/** Test hook: forgets every coalition, cooldown, the file and the clock. */
	public static synchronized void reset() {
		coalitions.clear();
		cooldowns.clear();
		file = null;
		saveBlocked = false;
		clock = Clock.systemUTC();
	}
}
