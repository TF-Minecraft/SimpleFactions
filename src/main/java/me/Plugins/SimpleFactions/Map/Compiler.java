package me.Plugins.SimpleFactions.Map;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Map.export.OccupationMapExport;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Map.Provinces.ProvinceDataEntry;
import me.Plugins.SimpleFactions.SimpleFactions;

public class Compiler {
	public void exportQueue(HashMap<String, List<String>> queues) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File file = new File("plugins/SimpleFactions/MapAPI/queue.json");

        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(queues, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
	public void exportAllFactionsToNationJson() {
		File folder = new File("plugins/SimpleFactions/Data");
		File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));

		JsonObject root = new JsonObject();
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		if (files != null && files.length > 0) {
			for (File file : files) {
				try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
					JsonObject factionData = JsonParser.parseReader(reader).getAsJsonObject();
					String id = factionData.get("id").getAsString();
					root.add(id, factionData);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		File outputFile = new File("plugins/SimpleFactions/MapAPI/nation.json");
		outputFile.getParentFile().mkdirs();

		try (FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
			gson.toJson(root, writer);
			System.out.println("Successfully exported nations to: " + outputFile.getPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static double r2(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	public void exportProvincesToJson(File out) throws Exception {
		JsonArray arr = new JsonArray();

		ProvinceManager pm = SimpleFactions.getInstance().getProvinceManager();
		Map<Integer, String> occupiedBy = OccupationMapExport.occupierByProvince(
				WarManager.getActive(),
				TitleManager::getByProvince);

		for (Province p : pm.getProvinces()) {
			JsonObject o = new JsonObject();

			o.addProperty("id", p.getId());
			o.addProperty("prosperity", p.getProsperity());
			String occupierId = occupiedBy.get(p.getId());
			if (occupierId != null) {
				o.addProperty("occupied_by", occupierId);
			}

			// trade data
			JsonObject trade = new JsonObject();
			double totalTrade = 0;

			for (Map.Entry<String, ProvinceDataEntry> e : p.getAllData().entrySet()) {
				ProvinceDataEntry d = e.getValue();
				if (d.getTrade() < 0.1) continue;

				JsonObject g = new JsonObject();
				g.addProperty("trade", r2(d.getTrade()));
				g.addProperty("production", r2(d.getProduction()));

				trade.add(e.getKey(), g);
				totalTrade += d.getTrade();
			}

			o.add("trade", trade);

			arr.add(o);
		}

		try (FileWriter w = new FileWriter(out)) {
			new GsonBuilder().setPrettyPrinting().create().toJson(arr, w);
		}
	}

	public void exportGuildsToJson(File out) throws Exception {
		JsonArray arr = new JsonArray();

		for (Guild g : FactionManager.getAllGuilds()) {
			JsonObject o = new JsonObject();

			o.addProperty("id", g.getId());
			o.addProperty("name", g.getName());
			o.addProperty("type", g.getType().getName());
			o.addProperty("size", g.getSize());
			o.addProperty("rgb", g.getRGB());
			o.addProperty("trade_power", g.getTradeBreakdown().getTradePower());

			JsonArray patterns = new JsonArray();
			for (String p : g.getBannerPatterns()) {
				patterns.add(p);
			}
			o.add("banner", patterns);

			arr.add(o);
		}

		try (FileWriter w = new FileWriter(out)) {
			new GsonBuilder().setPrettyPrinting().create().toJson(arr, w);
		}
	}
}
