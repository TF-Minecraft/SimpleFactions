package net.tfminecraft.simplefactions.map.export;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

import com.google.gson.JsonObject;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.map.MapFile;

/**
 * Writes the chronicle snapshot to disk for upload. Runs on the main thread as part of
 * the 300s live payload; see MapSystem.
 */
public final class ChronicleExport {
	private ChronicleExport() {
	}

	public static void export(File out) throws IOException {
		// Not pretty printed: this one ships 288 times a day.
		new MapFile(out, build(), false).write();
	}

	/** The chronicle snapshot, read from live state on the server thread; writing it can happen later. */
	public static JsonObject build() {
		// Prestige is derived and never recomputed on a timer, so a snapshot taken without
		// this would carry whatever the last triggering event happened to leave behind.
		// Wealth is already maintained eagerly on every bank mutation, and looping
		// updateWealth here would be O(n^2) since it cascades into updateAllPrestige.
		FactionManager.updateAllPrestigeConverged();

		return ChronicleSnapshot.build(
				FactionManager.factions,
				FactionManager.getDay(),
				FactionManager.getTimer(),
				Instant.now());
	}
}
