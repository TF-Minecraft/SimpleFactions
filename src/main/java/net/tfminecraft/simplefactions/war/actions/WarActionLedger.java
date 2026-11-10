package net.tfminecraft.simplefactions.war.actions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The results of the last {@value #MAX} actions applied, kept in {@code WarHistory/actions-applied.json}.
 * An action the website delivers again (its {@code /applied} post was lost) is answered from here and
 * never applied twice, across restarts too.
 */
final class WarActionLedger {
	static final int MAX = 1000;

	private final Path file;
	private final Consumer<String> warn;
	private final Map<Long, WarActionResult> results = new LinkedHashMap<>(16, 0.75f, false) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, WarActionResult> eldest) {
			return size() > MAX;
		}
	};

	WarActionLedger(Path file, Consumer<String> warn) {
		this.file = file;
		this.warn = warn;
	}

	/** Reads the file an earlier run left; an unreadable file or row is skipped. */
	synchronized void load() {
		results.clear();
		if (!Files.exists(file)) {
			return;
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			JsonArray rows = root.isJsonObject() && root.getAsJsonObject().has("results")
					&& root.getAsJsonObject().get("results").isJsonArray()
							? root.getAsJsonObject().getAsJsonArray("results") : new JsonArray();
			for (JsonElement row : rows) {
				WarActionResult result = WarActionResult.fromJson(row);
				if (result != null) {
					results.put(result.id(), result);
				}
			}
		} catch (IOException | RuntimeException unreadable) {
			warn.accept("Could not read " + file.getFileName() + ", so actions applied before this start are not remembered: "
					+ unreadable.getMessage());
		}
	}

	synchronized WarActionResult get(long id) {
		return results.get(id);
	}

	synchronized void put(WarActionResult result) {
		results.put(result.id(), result);
	}

	synchronized int size() {
		return results.size();
	}

	/** Rewrites the file beside itself and renames it into place, so a crash keeps the old one. */
	synchronized void save() {
		JsonArray rows = new JsonArray();
		results.values().forEach(result -> rows.add(result.toJson()));
		JsonObject root = new JsonObject();
		root.add("results", rows);
		Path temp = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(temp, WarActionResult.GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException failed) {
			warn.accept("Could not save " + file.getFileName() + ": " + failed.getMessage());
		} finally {
			temp.toFile().delete();
		}
	}
}
