package me.Plugins.SimpleFactions.REST;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Map.ProvinceGrid;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.api.GatewayClient;

public class RestServer {
	private static final Gson gson = new Gson();
	private static final String REGEN_HASH = "47a4921f7506514aec2d1471b424d8ae";

	public static List<String> fetchBannerList() {
		try {
			GatewayClient.Result result = GatewayClient.request("GET", "/generator/banner", null);
			if (!result.ok) {
				System.out.println("[SimpleFactions] fetchBannerList failed: " + result.error);
				return null;
			}

			Type listType = new TypeToken<ArrayList<String>>() {}.getType();
			return gson.fromJson(result.body, listType);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static int getProvince(Player p) {
		if (!Cache.provincesEnabled || !Cache.mapEnabled) return -2;
		SimpleFactions plugin = SimpleFactions.getInstance();
		if (plugin == null) return -2;
		ProvinceGrid grid = plugin.getProvinceGrid();
		if (grid == null) return -2;
		return grid.getAt(p.getLocation().getBlockX(), p.getLocation().getBlockZ());
	}

	public static void upload(String mode, File file) {
		if (!Cache.mapEnabled) return;
		if (mode.equals("chronicle") && !Cache.chronicleEnabled) return;

		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			var payload = JsonParser.parseReader(reader);

			validate(mode, payload);

			String path = "/" + Cache.mapRef + "/data/upload/" + mode;
			GatewayClient.Result result = GatewayClient.request(
				"POST",
				path,
				payload.toString()
			);

			if (result.ok) {
				System.out.println(mode + " data uploaded");
			} else {
				System.out.println("Upload failed for " + mode + ": " + result.error);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static void validate(String mode, JsonElement payload) {
		if (mode.equals("nation") && !payload.isJsonObject())
			throw new IllegalStateException("nation upload must be JSON object");

		if ((mode.equals("provinces") || mode.equals("guilds")) && !payload.isJsonArray())
			throw new IllegalStateException(mode + " upload must be JSON array");

		if (mode.equals("map_markers") && !payload.isJsonObject())
			throw new IllegalStateException("map_markers upload must be JSON object");

		if (mode.equals("map_markers")) {
			var obj = payload.getAsJsonObject();
			if (!obj.has("settlements") || !obj.get("settlements").isJsonArray())
				throw new IllegalStateException("map_markers upload must include settlements array");
			if (obj.has("installations") && !obj.get("installations").isJsonArray())
				throw new IllegalStateException("map_markers installations must be a JSON array");
			if (obj.has("forts") && !obj.get("forts").isJsonArray())
				throw new IllegalStateException("map_markers forts must be a JSON array");
		}

		if (mode.equals("chronicle")) {
			if (!payload.isJsonObject())
				throw new IllegalStateException("chronicle upload must be JSON object");
			var obj = payload.getAsJsonObject();
			if (!obj.has("captured_at"))
				throw new IllegalStateException("chronicle upload must include captured_at");
			if (!obj.has("factions") || !obj.get("factions").isJsonArray())
				throw new IllegalStateException("chronicle upload must include factions array");
		}

		if (mode.equals("regions") && !payload.isJsonObject())
			throw new IllegalStateException("regions upload must be JSON object");
	}

	public static void commenceRegen(String regenType) {
		if (!Cache.mapEnabled) return;

		try {
			String path = "/" + Cache.mapRef + "/" + REGEN_HASH + "/api/regenerate/" + regenType;
			GatewayClient.Result result = GatewayClient.request("GET", path, null);
			System.out.println("Regeneration request response: " + (result.ok ? "OK" : result.error));

			if (result.ok) {
				System.out.println("Regeneration triggered successfully.");
			} else {
				System.out.println("Regeneration failed: " + result.error);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
