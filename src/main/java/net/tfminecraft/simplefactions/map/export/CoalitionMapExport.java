package net.tfminecraft.simplefactions.map.export;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.tfminecraft.simplefactions.diplomacy.coalition.Coalition;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionRegistry;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionService;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionSettings;

/**
 * {@code MapAPI/coalitions.json} for the website's coalition map mode. Leader, target
 * and members are faction ids, which the website resolves against nation.json. A
 * coalition that lost its target sends no target, so nothing is hatched for it.
 */
public final class CoalitionMapExport {
	private CoalitionMapExport() {}

	public static JsonObject payload() {
		JsonArray coalitions = new JsonArray();
		// Coalitions switched off are not on the map either.
		if (CoalitionSettings.enabled) {
			for (Coalition coalition : CoalitionRegistry.all()) coalitions.add(row(coalition));
		}
		JsonObject payload = new JsonObject();
		payload.add("coalitions", coalitions);
		return payload;
	}

	private static JsonObject row(Coalition coalition) {
		JsonObject row = new JsonObject();
		row.addProperty("id", coalition.getId());
		row.addProperty("name", CoalitionService.displayName(coalition));
		row.addProperty("rgb", CoalitionService.colour(coalition));
		row.addProperty("leader", coalition.getLeaderId());
		if (CoalitionService.targetLost(coalition)) row.add("target", JsonNull.INSTANCE);
		else row.addProperty("target", coalition.getTargetId());
		row.addProperty("offensive", coalition.isOffensive());
		JsonArray members = new JsonArray();
		for (String id : coalition.getMemberIds()) members.add(id);
		row.add("members", members);
		JsonArray banner = new JsonArray();
		for (String pattern : CoalitionService.bannerPatterns(coalition)) banner.add(pattern);
		row.add("banner", banner);
		return row;
	}

	/** Writes the payload and returns it, so the caller can tell whether it changed. */
	public static String write(File out) throws IOException {
		String json = new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(payload());
		File parent = out.getParentFile();
		if (parent != null) Files.createDirectories(parent.toPath());
		Files.writeString(out.toPath(), json, StandardCharsets.UTF_8);
		return json;
	}
}
