package net.tfminecraft.simplefactions.diplomacy.coalition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.map.export.CoalitionMapExport;

class CoalitionMapExportTest extends CoalitionTestBase {
	@TempDir
	Path folder;

	private JsonObject only() {
		return CoalitionMapExport.payload().getAsJsonArray("coalitions").get(0).getAsJsonObject();
	}

	@Test
	void eachCoalitionIsSentWithFactionIdsForTheWebsiteToResolve() {
		Coalition league = league();
		JsonObject row = only();
		assertEquals(league.getId(), row.get("id").getAsString());
		assertEquals("Grand League", row.get("name").getAsString());
		// No colour of its own yet, so the leader's.
		assertEquals(thalendor.getRGB(), row.get("rgb").getAsString());
		assertEquals("thalendor", row.get("leader").getAsString());
		assertEquals("holy", row.get("target").getAsString());
		assertFalse(row.get("offensive").getAsBoolean());
		assertEquals(List.of("thalendor", "bog", "fig"),
				row.getAsJsonArray("members").asList().stream().map(e -> e.getAsString()).toList());
		assertEquals(thalendor.getBannerPatterns(),
				row.getAsJsonArray("banner").asList().stream().map(e -> e.getAsString()).toList());

		league.setBannerPatterns(List.of("RED.BASE"));
		league.setOffensive(true);
		assertTrue(CoalitionService.setColour(thalendor, "120, 40,40").ok());
		row = only();
		assertEquals("120,40,40", row.get("rgb").getAsString());
		assertEquals("RED.BASE", row.getAsJsonArray("banner").get(0).getAsString());
		assertTrue(row.get("offensive").getAsBoolean());

		// A lost target is not hatched, and a coalition without a known leader has no colour.
		league.setTargetLostAt(CoalitionRegistry.now());
		league.setRgb(null);
		league.setBannerPatterns(null);
		league.setLeaderId("gone");
		row = only();
		assertTrue(row.get("target").isJsonNull());
		assertTrue(row.get("rgb").isJsonNull());
		assertEquals(0, row.getAsJsonArray("banner").size());

		CoalitionSettings.enabled = false;
		assertEquals(0, CoalitionMapExport.payload().getAsJsonArray("coalitions").size());
	}

	@Test
	void theExportIsWrittenAndReturnedForChangeDetection() throws Exception {
		league();
		Path out = folder.resolve("MapAPI/coalitions.json");
		String json = CoalitionMapExport.write(out.toFile());
		assertEquals(json, Files.readString(out));
		assertEquals(1, JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("coalitions").size());
		assertEquals(json, CoalitionMapExport.write(Path.of("coalitions-test.json").toFile()));
		Files.delete(Path.of("coalitions-test.json"));
	}

	@Test
	void onlyTheLeaderPicksAValidColour() {
		Coalition league = league();
		assertEquals(CoalitionService.NOT_IN_COALITION, CoalitionService.setColour(holy, "1,2,3").message());
		assertEquals(CoalitionService.NOT_LEADER, CoalitionService.setColour(bog, "1,2,3").message());
		assertEquals("§cInvalid format. Use: R,G,B (e.g. 255,0,0)", CoalitionService.setColour(thalendor, "1,2").message());
		assertEquals("§cRGB values must be numbers (e.g. 128,128,128)", CoalitionService.setColour(thalendor, "a,b,c").message());
		assertEquals("§cEach RGB value must be between 0 and 255", CoalitionService.setColour(thalendor, "1,2,300").message());
		assertEquals("§c0,0,0 is reserved for unclaimed land; pick another colour", CoalitionService.setColour(thalendor, "0,0,0").message());
		assertTrue(CoalitionService.setColour(thalendor, "10,20,30").ok());
		assertEquals("10,20,30", CoalitionService.colour(league));
		assertTrue(CoalitionService.setColour(thalendor, "RESET").ok());
		assertEquals(thalendor.getRGB(), CoalitionService.colour(league));
	}
}
