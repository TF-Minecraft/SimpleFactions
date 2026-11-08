package net.tfminecraft.simplefactions.war.stats;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.JsonUtil;

/**
 * Finished battles, one JSON file each under {@code BattleStats/}. Only a small
 * summary of every battle stays in memory; full records are read when viewed.
 */
public final class BattleStatsArchive {
	static final String FOLDER = "BattleStats";
	private static final int CACHE_SIZE = 16;

	/** What the lists need without opening each file. */
	public record Summary(String id, String name, long endedAt, String warKey, String warName,
			String winner, Set<String> fighters) {}

	private static final List<Summary> INDEX = new CopyOnWriteArrayList<>();
	private static final Map<String, BattleRecord> CACHE = Collections.synchronizedMap(
			new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, BattleRecord> eldest) {
					return size() > CACHE_SIZE;
				}
			});

	private BattleStatsArchive() {
	}

	/** Reads the summaries off the main thread at startup. */
	public static void loadAsync() {
		Bukkit.getScheduler().runTaskAsynchronously(SimpleFactions.plugin, BattleStatsArchive::loadNow);
	}

	static void loadNow() {
		File folder = folder();
		File[] files = folder == null ? null : folder.listFiles((dir, name) -> name.endsWith(".json"));
		if (files == null) {
			return;
		}
		for (File file : files) {
			try {
				BattleRecord record = JsonUtil.readJson(file, BattleRecord.class);
				if (record != null && record.id != null && find(record.id) == null) {
					INDEX.add(summary(record));
				}
			} catch (IOException | RuntimeException unreadable) {
				log("Skipped unreadable battle stats file " + file.getName(), unreadable);
			}
		}
	}

	/** Saves a finished battle; one small file write per battle. */
	public static void save(BattleRecord record) {
		INDEX.add(summary(record));
		CACHE.put(record.id, record);
		File folder = folder();
		if (folder == null) return;
		try {
			java.nio.file.Files.createDirectories(folder.toPath());
			JsonUtil.writeJsonAtomic(new File(folder, record.id + ".json"), record);
		} catch (IOException | RuntimeException failed) {
			log("Could not save battle stats " + record.id, failed);
		}
	}

	/** A finished battle by its record ID, or null. */
	public static BattleRecord get(String id) {
		if (id == null || !id.equals(BattleRecord.safeId(id)) || find(id) == null || folder() == null) {
			return null;
		}
		BattleRecord cached = CACHE.get(id);
		if (cached != null) {
			return cached;
		}
		try {
			BattleRecord record = JsonUtil.readJson(new File(folder(), id + ".json"), BattleRecord.class);
			if (record != null) {
				CACHE.put(id, record);
			}
			return record;
		} catch (IOException | RuntimeException unreadable) {
			log("Could not read battle stats " + id, unreadable);
			return null;
		}
	}

	/** Newest first; only battles the character fought in, or all of them for a null key. */
	public static List<Summary> list(String characterKey) {
		List<Summary> result = new ArrayList<>();
		for (Summary summary : INDEX) {
			if (characterKey == null || summary.fighters().contains(characterKey)) {
				result.add(summary);
			}
		}
		result.sort(Comparator.comparingLong(Summary::endedAt).reversed());
		return result;
	}

	private static Summary find(String id) {
		for (Summary summary : INDEX) {
			if (summary.id().equals(id)) {
				return summary;
			}
		}
		return null;
	}

	private static Summary summary(BattleRecord record) {
		Set<String> keys = new java.util.HashSet<>();
		for (FighterStats fighter : record.fighters) {
			keys.add(fighter.key);
		}
		return new Summary(record.id, record.name, record.endedAt, record.warKey, record.warName,
				record.winner, Set.copyOf(keys));
	}

	/** Null without a plugin data folder, so nothing is written to the working directory. */
	private static File folder() {
		File data = SimpleFactions.plugin == null ? null : SimpleFactions.plugin.getDataFolder();
		return data == null ? null : new File(data, FOLDER);
	}

	private static void log(String message, Exception cause) {
		SimpleFactions.plugin.getLogger().log(Level.WARNING, message, cause);
	}

	static void resetForTests() {
		INDEX.clear();
		CACHE.clear();
	}
}
