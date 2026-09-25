package net.tfminecraft.simplefactions.loaders;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.map.MapRegion;

public final class RegionLoader {

	private static final File DEFAULT_FILE = new File("plugins/SimpleFactions/Input/regions.json");

	private static List<MapRegion> regions = new ArrayList<>();
	private static Map<String, MapRegion> byId = new HashMap<>();
	private static Map<Integer, MapRegion> byProvince = new HashMap<>();

	private static Logger logger() {
		SimpleFactions plugin = SimpleFactions.getInstance();
		return plugin != null ? plugin.getLogger() : Logger.getLogger(RegionLoader.class.getName());
	}

	private RegionLoader() {
	}

	public static void loadAll() {
		loadAll(DEFAULT_FILE);
	}

	public static void loadAll(File file) {
		regions = new ArrayList<>();
		byId = new HashMap<>();
		byProvince = new HashMap<>();

		if (file == null || !file.isFile()) {
			return;
		}

		try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				logger().warning("regions.json must be a JSON object");
				return;
			}
			loadFrom(parsed.getAsJsonObject());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static void loadFrom(JsonObject root) {
		regions = new ArrayList<>();
		byId = new HashMap<>();
		byProvince = new HashMap<>();
		if (root == null) {
			return;
		}

		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			String id = entry.getKey() == null ? "" : entry.getKey().trim();
			if (id.isEmpty() || !entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject data = entry.getValue().getAsJsonObject();
			String name = id;
			if (data.has("name") && data.get("name").isJsonPrimitive()) {
				String rawName = data.get("name").getAsString();
				if (rawName != null && !rawName.isBlank()) {
					name = rawName.trim();
				}
			}

			Set<Integer> provinces = new LinkedHashSet<>();
			if (data.has("provinces") && data.get("provinces").isJsonArray()) {
				for (JsonElement item : data.get("provinces").getAsJsonArray()) {
					if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber()) {
						continue;
					}
					provinces.add(item.getAsInt());
				}
			}

			MapRegion region = new MapRegion(id, name, provinces);
			regions.add(region);
			byId.put(id.toLowerCase(Locale.ROOT), region);
			for (int provinceId : provinces) {
				if (byProvince.containsKey(provinceId)) {
					MapRegion existing = byProvince.get(provinceId);
					logger().warning(
							"Province "
									+ provinceId
									+ " is in both '"
									+ existing.getId()
									+ "' and '"
									+ id
									+ "'; keeping '"
									+ existing.getId()
									+ "'");
					continue;
				}
				byProvince.put(provinceId, region);
			}
		}
	}

	public static List<MapRegion> getRegions() {
		return Collections.unmodifiableList(regions);
	}

	public static MapRegion getById(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		return byId.get(id.toLowerCase(Locale.ROOT));
	}

	public static MapRegion getByProvince(int provinceId) {
		return byProvince.get(provinceId);
	}
}
