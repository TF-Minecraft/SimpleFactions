package net.tfminecraft.simplefactions.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;

class CompilerProvinceExportTest {
	private final Map<Terrain, Double> previousCarry = new HashMap<>(Cache.tradeCarry);
	private final double previousFull = Cache.infrastructureFull;
	private final double previousTarget = Cache.infrastructureTarget;

	@BeforeEach
	void setUp() {
		Cache.infrastructureFull = 20;
		Cache.infrastructureTarget = 0.75;
		Cache.tradeCarry.clear();
		Cache.tradeCarry.put(Terrain.BOG, 0.40);
		Cache.tradeCarry.put(Terrain.PLAINS, 0.75);
		Cache.tradeCarry.put(Terrain.SEA, 0.60);
		Cache.tradeCarry.put(Terrain.WATER, 0.75);
	}

	@AfterEach
	void restore() {
		Cache.infrastructureFull = previousFull;
		Cache.infrastructureTarget = previousTarget;
		Cache.tradeCarry.clear();
		Cache.tradeCarry.putAll(previousCarry);
	}

	@Test
	void bogWithInfrastructureExportsFillAndEffectiveTerrain() {
		Province province = new Province(1, "bog", 0);
		province.setInfrastructure(12);

		JsonObject json = Compiler.provinceToJson(province, null);

		assertEquals("bog", json.get("terrain").getAsString());
		assertEquals(0.40, json.get("terrain_value").getAsDouble());
		assertEquals(12, json.get("infrastructure").getAsDouble());
		assertEquals(0.60, json.get("infrastructure_fill").getAsDouble());
		assertEquals(0.61, json.get("effective_terrain").getAsDouble());
	}

	@Test
	void terrainValueIsNotRounded() {
		Cache.tradeCarry.put(Terrain.BOG, 0.445);

		JsonObject json = Compiler.provinceToJson(new Province(1, "bog", 0), null);

		assertEquals(0.445, json.get("terrain_value").getAsDouble());
	}

	@Test
	void invalidInfrastructureFullOmitsInfrastructureFields() {
		Cache.infrastructureFull = -5;
		Province province = new Province(1, "bog", 0);
		province.setInfrastructure(12);

		JsonObject json = Compiler.provinceToJson(province, null);

		assertFalse(json.has("infrastructure"));
		assertFalse(json.has("infrastructure_fill"));
	}

	@Test
	void bogWithoutInfrastructureOmitsInfrastructureFields() {
		JsonObject json = Compiler.provinceToJson(new Province(1, "bog", 0), null);

		assertEquals(0.40, json.get("effective_terrain").getAsDouble());
		assertFalse(json.has("infrastructure"));
		assertFalse(json.has("infrastructure_fill"));
	}

	@Test
	void infrastructureDoesNotRaisePlainsAboveItsTerrainValue() {
		Province province = new Province(1, "plains", 0);
		province.setInfrastructure(20);

		assertEquals(0.75, Compiler.provinceToJson(province, null).get("effective_terrain").getAsDouble());
	}

	@Test
	void seaAndWaterOmitAllNewTerrainFields() {
		for (String terrain : new String[] {"sea", "water"}) {
			JsonObject json = Compiler.provinceToJson(new Province(1, terrain, 0), null);
			assertFalse(json.has("terrain"));
			assertFalse(json.has("terrain_value"));
			assertFalse(json.has("infrastructure"));
			assertFalse(json.has("infrastructure_fill"));
			assertFalse(json.has("effective_terrain"));
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
		assertTrue(json.has("effective_terrain"));
	}
}
