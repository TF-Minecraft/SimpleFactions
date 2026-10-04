package net.tfminecraft.simplefactions.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;

class CompilerProvinceExportTest {
	@Test
	void provincesOmitInfrastructureAndEffectiveTerrain() {
		for (String terrain : new String[] {"bog", "plains", "sea", "water"}) {
			JsonObject json = Compiler.provinceToJson(new Province(1, terrain, 0), null);
			assertFalse(json.has("infrastructure"));
			assertFalse(json.has("infrastructure_fill"));
			assertFalse(json.has("effective_terrain"));
			assertFalse(json.has("terrain"));
			assertFalse(json.has("terrain_value"));
		}
	}

	@Test
	void tradeObjectAndExistingFieldOrderArePreserved() {
		Province province = new Province(1, "bog", 0);
		Guild guild = mock(Guild.class);
		province.setData("guild-id", new ProvinceDataEntry(guild, 1.234, 0.567));

		JsonObject json = Compiler.provinceToJson(province, "occupier");

		assertEquals("{\"trade\":1.23,\"production\":0.57}", json.getAsJsonObject("trade")
				.getAsJsonObject("guild-id").toString());
		assertEquals("id", json.keySet().toArray()[0]);
		assertEquals("prosperity", json.keySet().toArray()[1]);
		assertEquals("occupied_by", json.keySet().toArray()[2]);
		assertEquals("trade", json.keySet().toArray()[3]);
		assertEquals(4, json.keySet().size());
	}
}
