package me.Plugins.SimpleFactions.Map.export;

import com.google.gson.JsonObject;

import me.Plugins.SimpleFactions.Cache;

/**
 * Season chapter slug and display name. Distinct from {@code map-reference} /
 * payload {@code map_id}, which are the live upload socket.
 */
public final class ChapterIdentity {
	public static final String UNKNOWN_ID = "unknown";
	public static final String UNKNOWN_NAME = "Unknown";

	private ChapterIdentity() {
	}

	public static String normalizeId(String raw) {
		if (raw == null) {
			return UNKNOWN_ID;
		}
		String id = raw.trim().toLowerCase();
		if (id.isEmpty() || !id.matches("[a-z0-9]+")) {
			return UNKNOWN_ID;
		}
		return id;
	}

	public static String normalizeName(String raw) {
		if (raw == null) {
			return UNKNOWN_NAME;
		}
		String name = raw.trim();
		return name.isEmpty() ? UNKNOWN_NAME : name;
	}

	public static void putOnEnvelope(JsonObject root) {
		String id = Cache.chapterId;
		String name = Cache.chapterName;
		root.addProperty("chapter_id", id == null || id.isEmpty() ? UNKNOWN_ID : id);
		root.addProperty("chapter_name", name == null || name.isEmpty() ? UNKNOWN_NAME : name);
	}
}
