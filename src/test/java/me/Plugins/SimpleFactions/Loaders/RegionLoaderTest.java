package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RegionLoaderTest {

	@AfterEach
	void tearDown() {
		RegionLoader.loadFrom(new com.google.gson.JsonObject());
	}

	@Test
	void missingFile_isEmpty() {
		RegionLoader.loadAll(new java.io.File("does-not-exist-regions.json"));
		assertEquals(0, RegionLoader.getRegions().size());
		assertNull(RegionLoader.getByProvince(1));
	}

	@Test
	void load_mapsProvincesAndName() throws IOException {
		Path file = Files.createTempFile("sf-regions-", ".json");
		Files.writeString(file, """
				{
				  "REGION_1": {
				    "name": "Highlands",
				    "provinces": [10, 20],
				    "rgb": "1,2,3"
				  }
				}
				""");
		try {
			RegionLoader.loadAll(file.toFile());
			assertEquals("Highlands", RegionLoader.getById("region_1").getName());
			assertSame(RegionLoader.getById("REGION_1"), RegionLoader.getByProvince(10));
			assertSame(RegionLoader.getById("REGION_1"), RegionLoader.getByProvince(20));
			assertNull(RegionLoader.getByProvince(99));
		} finally {
			Files.deleteIfExists(file);
		}
	}

	@Test
	void duplicateProvince_keepsFirst() {
		com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString("""
				{
				  "REGION_1": { "name": "A", "provinces": [5] },
				  "REGION_2": { "name": "B", "provinces": [5, 6] }
				}
				""").getAsJsonObject();
		RegionLoader.loadFrom(root);
		assertEquals("REGION_1", RegionLoader.getByProvince(5).getId());
		assertEquals("REGION_2", RegionLoader.getByProvince(6).getId());
	}
}
