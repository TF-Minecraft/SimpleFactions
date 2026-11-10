package net.tfminecraft.simplefactions.war.history;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * War events and battle records waiting for the website, one JSON line each. The file is only
 * appended to, except when acked items are removed, so a crash or a website outage loses nothing.
 * Pending lines are also kept in memory so the sender never rereads the file.
 */
public final class WarHistoryOutbox {
	/** What each line carries: a war event or a battle record. */
	public enum Kind {
		EVENT("event"),
		BATTLE("battle");

		private final String json;

		Kind(String json) {
			this.json = json;
		}

		static Kind fromJson(String value) {
			for (Kind kind : values()) {
				if (kind.json.equals(value)) {
					return kind;
				}
			}
			return null;
		}
	}

	/** One pending item: its id, and its JSON exactly as it is posted. */
	public record Item(Kind kind, String id, String json) {
	}

	/** Past this many pending lines the log warns, so a website outage does not go unnoticed. */
	static final int WARN_PENDING = 10_000;

	private final Path file;
	private final Consumer<String> warn;
	private final int warnAt;
	private final Map<String, Item> pending = new LinkedHashMap<>();
	private boolean warned;

	public WarHistoryOutbox(Path file, Consumer<String> warn) {
		this(file, warn, WARN_PENDING);
	}

	WarHistoryOutbox(Path file, Consumer<String> warn, int warnAt) {
		this.file = file;
		this.warn = warn;
		this.warnAt = warnAt;
	}

	/** Reads what an earlier run left; an unreadable line is skipped and dropped at the next rewrite. */
	public synchronized void load() {
		pending.clear();
		if (!Files.exists(file)) {
			return;
		}
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		} catch (IOException unreadable) {
			warn.accept("Could not read the war history outbox: " + unreadable.getMessage());
			return;
		}
		int skipped = 0;
		for (String line : lines) {
			Item item = parse(line);
			if (item == null) {
				skipped += line.isBlank() ? 0 : 1;
				continue;
			}
			pending.putIfAbsent(key(item.kind(), item.id()), item);
		}
		if (skipped > 0) {
			warn.accept("Skipped " + skipped + " unreadable war history outbox lines.");
		}
		checkSize();
	}

	/** Queues an item unless one with the same id is already waiting. */
	public synchronized boolean append(Kind kind, String id, JsonElement item) {
		if (kind == null || id == null || item == null || pending.containsKey(key(kind, id))) {
			return false;
		}
		Item entry = new Item(kind, id, WarHistoryJson.GSON.toJson(item));
		pending.put(key(kind, id), entry);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, line(entry) + "\n", StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException failed) {
			// Still in memory: the next successful rewrite saves it.
			warn.accept("Could not write to the war history outbox: " + failed.getMessage());
		}
		checkSize();
		return true;
	}

	/** The oldest pending items of one kind, at most {@code max}. */
	public synchronized List<Item> peek(Kind kind, int max) {
		List<Item> batch = new ArrayList<>();
		for (Item item : pending.values()) {
			if (batch.size() >= max) {
				break;
			}
			if (item.kind() == kind) {
				batch.add(item);
			}
		}
		return batch;
	}

	/** Drops acked items and rewrites the file; returns how many were removed. */
	public synchronized int remove(Kind kind, Collection<String> ids) {
		int removed = 0;
		for (String id : ids) {
			if (id != null && pending.remove(key(kind, id)) != null) {
				removed++;
			}
		}
		if (removed > 0) {
			rewrite();
		}
		if (pending.size() <= warnAt) {
			warned = false;
		}
		return removed;
	}

	public synchronized int size(Kind kind) {
		int count = 0;
		for (Item item : pending.values()) {
			if (item.kind() == kind) {
				count++;
			}
		}
		return count;
	}

	public synchronized int size() {
		return pending.size();
	}

	private void rewrite() {
		Path temp = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
		try {
			Files.createDirectories(file.getParent());
			StringBuilder out = new StringBuilder();
			for (Item item : pending.values()) {
				out.append(line(item)).append('\n');
			}
			Files.writeString(temp, out, StandardCharsets.UTF_8);
			// The temp file sits beside the outbox, so the move is a rename on the same disk.
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException failed) {
			warn.accept("Could not rewrite the war history outbox: " + failed.getMessage());
		} finally {
			// A stray temp file is harmless, but do not leave one behind on a failed move.
			temp.toFile().delete();
		}
	}

	private void checkSize() {
		if (pending.size() > warnAt && !warned) {
			warned = true;
			warn.accept("The war history outbox holds " + pending.size()
					+ " items. Is the website reachable through TFMCWeb?");
		}
	}

	private static String line(Item item) {
		return "{\"kind\":\"" + item.kind().json + "\",\"id\":" + WarHistoryJson.GSON.toJson(item.id())
				+ ",\"item\":" + item.json() + "}";
	}

	static Item parse(String line) {
		try {
			JsonElement parsed = JsonParser.parseString(line);
			if (!parsed.isJsonObject()) {
				return null;
			}
			JsonObject object = parsed.getAsJsonObject();
			Kind kind = object.has("kind") ? Kind.fromJson(object.get("kind").getAsString()) : null;
			JsonElement id = object.get("id");
			JsonElement item = object.get("item");
			if (kind == null || id == null || !id.isJsonPrimitive() || item == null || !item.isJsonObject()) {
				return null;
			}
			return new Item(kind, id.getAsString(), WarHistoryJson.GSON.toJson(item));
		} catch (RuntimeException malformed) {
			return null;
		}
	}

	private static String key(Kind kind, String id) {
		return kind.json + ":" + id;
	}
}
