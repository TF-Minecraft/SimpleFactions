package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.tiers.Title;

class TitleLoaderSaveTest {
	@TempDir
	Path folder;

	@Test
	void saveKeepsOtherEntriesTheirOrderAndUnknownFields() throws Exception {
		Files.writeString(folder.resolve("county.json"),
				"{\"COUNTY_1\": {\"name\": \"Urseilos\", \"provinces\": [3, 1], \"rgb\": \"1,1,1\", \"note\": \"keep me\"},"
						+ " \"COUNTY_2\": {\"name\": \"Ardentos\", \"provinces\": [4], \"rgb\": \"2,2,2\"}}",
				StandardCharsets.UTF_8);
		Title title = title("{\"name\":\"Urseilos\",\"provinces\":[3,1],\"rgb\":\"1,1,1\"}");
		title.setName("New Urseilos");
		title.getProvinces().add(9);

		assertTrue(TitleLoader.saveTitle(title, folder.toFile()));

		JsonObject root = JsonParser.parseString(Files.readString(folder.resolve("county.json"))).getAsJsonObject();
		assertEquals(List.of("COUNTY_1", "COUNTY_2"), List.copyOf(root.keySet()));
		JsonObject saved = root.getAsJsonObject("COUNTY_1");
		assertEquals("New Urseilos", saved.get("name").getAsString());
		assertEquals("[3,1,9]", saved.get("provinces").toString());
		assertEquals("keep me", saved.get("note").getAsString());
		assertFalse(saved.has("title-complete"));
		assertFalse(saved.has("titles"));
		assertEquals("Ardentos", root.getAsJsonObject("COUNTY_2").get("name").getAsString());
		assertFalse(Files.exists(folder.resolve("county.json.tmp")));
	}

	@Test
	void saveRefusesToOverwriteAnUnreadableFile() throws Exception {
		Files.writeString(folder.resolve("county.json"), "{ not json", StandardCharsets.UTF_8);

		assertFalse(TitleLoader.saveTitle(title("{\"name\":\"Urseilos\",\"provinces\":[1],\"rgb\":\"1,1,1\"}"), folder.toFile()));
		assertEquals("{ not json", Files.readString(folder.resolve("county.json")));
	}

	private static Title title(String json) {
		YamlConfiguration config = new YamlConfiguration();
		config.set("name", "county");
		config.set("tier", 2);
		return new Title(new Tier("county", config), "COUNTY_1", JsonParser.parseString(json).getAsJsonObject());
	}
}
