package net.tfminecraft.simplefactions.prestige;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.identity.Members;

/**
 * Online seconds per character for each recent UTC day, so member prestige can
 * tell which of an account's characters has been played lately. Days older than
 * {@link Cache#memberShareWindowDays} are dropped.
 *
 * <p>Reads are map lookups, so a prestige pass may ask on every bank mutation.
 */
public final class RecentPlaytime {

	/** Credited to the current character of each online player per tick. */
	public static final int TICK_SECONDS = 60;
	private static final long TICK_INTERVAL = TICK_SECONDS * 20L;
	private static final long AUTOSAVE_INTERVAL = 5L * 60L * 20L;
	private static final String FILE_NAME = "recent-playtime.json";

	public static final class SavedState {
		/** Character key to UTC epoch day to online seconds. */
		public Map<String, Map<Long, Integer>> characters = new HashMap<>();
	}

	private static final Map<String, Map<Long, Integer>> days = new HashMap<>();
	private static LongSupplier today = RecentPlaytime::utcToday;
	private static File file;
	private static boolean loaded;
	private static boolean saveBlocked;
	private static boolean dirty;
	private static BukkitTask tick;
	private static BukkitTask autosave;

	private RecentPlaytime() {
	}

	/** Uses {@code folder/recent-playtime.json} for {@link #load()} and {@link #save()}. */
	public static synchronized void bind(File folder) {
		file = folder == null ? null : new File(folder, FILE_NAME);
	}

	public static synchronized void load() {
		Map<String, Map<Long, Integer>> next = new HashMap<>();
		try {
			if (file != null && file.exists()) {
				SavedState state = JsonUtil.readJson(file, SavedState.class);
				if (state == null) throw new IOException("Recent playtime file contains no state");
				if (state.characters != null) {
					state.characters.forEach((key, perDay) -> {
						if (key != null && perDay != null) next.put(key, new HashMap<>(perDay));
					});
				}
			}
		} catch (IOException | RuntimeException ex) {
			// Keep the unreadable file for staff; counts made this run are not saved over it.
			loaded = true;
			saveBlocked = true;
			log("Could not read recent playtime", ex);
			return;
		}
		days.clear();
		days.putAll(next);
		loaded = true;
		saveBlocked = false;
		dirty = false;
		prune();
	}

	public static synchronized void save() {
		if (!loaded || saveBlocked || file == null) return;
		prune();
		try {
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) parent.mkdirs();
			SavedState state = new SavedState();
			days.forEach((key, perDay) -> state.characters.put(key, new HashMap<>(perDay)));
			JsonUtil.writeJsonAtomic(file, state);
			dirty = false;
		} catch (IOException ex) {
			log("Could not save recent playtime", ex);
		}
	}

	/** Credits each online player's current character every minute and saves every five. */
	public static synchronized void start(Plugin plugin) {
		stopTasks();
		tick = Bukkit.getScheduler().runTaskTimer(plugin, RecentPlaytime::creditOnline, TICK_INTERVAL, TICK_INTERVAL);
		autosave = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			if (isDirty()) save();
		}, AUTOSAVE_INTERVAL, AUTOSAVE_INTERVAL);
	}

	/** Stops crediting and saves what was counted. */
	public static synchronized void stop() {
		stopTasks();
		if (saveBlocked && dirty) log("Recent playtime counted this run is lost: the file could not be read at startup", null);
		save();
	}

	private static void stopTasks() {
		if (tick != null) tick.cancel();
		if (autosave != null) autosave.cancel();
		tick = null;
		autosave = null;
	}

	static void creditOnline() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			credit(Members.key(player), TICK_SECONDS);
		}
	}

	/** Adds online seconds to today for that character. */
	public static synchronized void credit(String key, int seconds) {
		if (key == null || key.isBlank() || seconds <= 0) return;
		long day = today.getAsLong();
		days.computeIfAbsent(key, ignored -> new HashMap<>())
				.merge(day, seconds, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
		dirty = true;
	}

	/** Online seconds for that character over the window, today included. */
	public static synchronized long seconds(String key) {
		if (key == null) return 0L;
		Map<Long, Integer> perDay = days.get(key);
		if (perDay == null) return 0L;
		long newest = today.getAsLong();
		long oldest = oldestDay();
		long total = 0L;
		for (Map.Entry<Long, Integer> entry : perDay.entrySet()) {
			Long day = entry.getKey();
			// Days ahead of the clock are kept on disk but not counted.
			if (day != null && day >= oldest && day <= newest && entry.getValue() != null) {
				total += Math.max(0, entry.getValue());
			}
		}
		return total;
	}

	private static void prune() {
		long oldest = oldestDay();
		Iterator<Map.Entry<String, Map<Long, Integer>>> characters = days.entrySet().iterator();
		while (characters.hasNext()) {
			Map<Long, Integer> perDay = characters.next().getValue();
			perDay.keySet().removeIf(day -> day == null || day < oldest);
			if (perDay.isEmpty()) characters.remove();
		}
	}

	private static long oldestDay() {
		return today.getAsLong() - Math.max(1, Cache.memberShareWindowDays) + 1;
	}

	private static synchronized boolean isDirty() {
		return dirty;
	}

	private static void log(String message, Throwable error) {
		SimpleFactions plugin = SimpleFactions.getInstance();
		Logger logger = plugin != null ? plugin.getLogger() : Logger.getLogger(RecentPlaytime.class.getName());
		logger.log(Level.WARNING, message, error);
	}

	private static long utcToday() {
		return LocalDate.now(ZoneOffset.UTC).toEpochDay();
	}

	/** Test hook: the UTC epoch day treated as today. Null restores the clock. */
	static synchronized void setToday(LongSupplier clock) {
		today = clock == null ? RecentPlaytime::utcToday : clock;
	}

	/** Test hook: forgets every count, the file and the clock. */
	public static synchronized void reset() {
		stopTasks();
		days.clear();
		file = null;
		loaded = false;
		saveBlocked = false;
		dirty = false;
		today = RecentPlaytime::utcToday;
	}
}
