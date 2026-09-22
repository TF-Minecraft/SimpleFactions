package me.Plugins.SimpleFactions.Map.export;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import me.Plugins.SimpleFactions.Managers.FactionManager;

/**
 * Writes the chronicle snapshot to disk for upload. Runs on the main thread as part of
 * the 300s live payload; see MapSystem.
 */
public final class ChronicleExport {
	private ChronicleExport() {
	}

	public static void export(File out) throws IOException {
		// Prestige is derived and never recomputed on a timer, so a snapshot taken without
		// this would carry whatever the last triggering event happened to leave behind.
		// Wealth is already maintained eagerly on every bank mutation, and looping
		// updateWealth here would be O(n^2) since it cascades into updateAllPrestige.
		FactionManager.updateAllPrestigeConverged();

		JsonObject root = ChronicleSnapshot.build(
				FactionManager.factions,
				FactionManager.getDay(),
				FactionManager.getTimer(),
				Instant.now());

		File parent = out.getParentFile();
		if (parent != null) {
			parent.mkdirs();
		}

		// Not pretty printed: this one ships 288 times a day.
		try (FileWriter writer = new FileWriter(out, StandardCharsets.UTF_8)) {
			new Gson().toJson(root, writer);
		}
	}
}
