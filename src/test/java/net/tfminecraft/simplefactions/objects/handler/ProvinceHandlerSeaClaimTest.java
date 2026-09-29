package net.tfminecraft.simplefactions.objects.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;

class ProvinceHandlerSeaClaimTest {
	private ProvinceManager provinceManager;
	private MockedStatic<SimpleFactions> pluginStatic;

	@BeforeEach
	void setUp() {
		provinceManager = new ProvinceManager();
		pluginStatic = mockStatic(SimpleFactions.class);
		SimpleFactions plugin = mock(SimpleFactions.class);
		pluginStatic.when(SimpleFactions::getInstance).thenReturn(plugin);
		when(plugin.getProvinceManager()).thenReturn(provinceManager);
	}

	@AfterEach
	void tearDown() {
		pluginStatic.close();
	}

	@Test
	void capitalCanClaimAcrossOneSeaTile() {
		Province capital = land(1);
		Province sea = sea(2);
		Province target = land(3);
		link(capital, sea);
		link(sea, target);
		load(capital, sea, target);

		ProvinceHandler handler = handler(1, 1);

		assertTrue(handler.canClaim(3, false));
	}

	@Test
	void landConnectedCoastCanClaimAcrossOneSeaTile() {
		Province capital = land(1);
		Province coast = land(2);
		Province sea = sea(3);
		Province target = land(4);
		link(capital, coast);
		link(coast, sea);
		link(sea, target);
		load(capital, coast, sea, target);

		ProvinceHandler handler = handler(1, 1, 2);

		assertTrue(handler.canClaim(4, false));
		assertTrue(handler.getClaimDeniedReason(4, false).contains("Success"));
	}

	@Test
	void seaCrossingStopsAfterOneTile() {
		Province capital = land(1);
		Province coast = land(2);
		Province nearSea = sea(3);
		Province farSea = sea(4);
		Province target = land(5);
		link(capital, coast);
		link(coast, nearSea);
		link(nearSea, farSea);
		link(farSea, target);
		load(capital, coast, nearSea, farSea, target);

		ProvinceHandler handler = handler(1, 1, 2);

		assertFalse(handler.canClaim(5, false));
	}

	@Test
	void disconnectedHoldingCannotCrossSea() {
		Province capital = land(1);
		Province inland = land(2);
		Province holding = land(5);
		Province sea = sea(6);
		Province target = land(7);
		link(capital, inland);
		link(holding, sea);
		link(sea, target);
		load(capital, inland, holding, sea, target);

		ProvinceHandler handler = handler(1, 1, 2, 5);

		assertFalse(handler.canClaim(7, false));
	}

	@Test
	void waterBridgeStillWorksFromAnyOwnedProvince() {
		Province capital = land(1);
		Province coast = land(2);
		Province water = new Province(3, Terrain.WATER.name(), 0);
		Province target = land(4);
		link(capital, coast);
		link(coast, water);
		link(water, target);
		load(capital, coast, water, target);

		ProvinceHandler handler = handler(1, 1, 2);

		assertTrue(handler.canClaim(4, false));
	}

	@Test
	void noCapitalCannotCrossSea() {
		Province owned = land(1);
		Province sea = sea(2);
		Province target = land(3);
		link(owned, sea);
		link(sea, target);
		load(owned, sea, target);

		ProvinceHandler handler = handler(-1, 1);

		assertFalse(handler.canClaim(3, false));
	}

	private ProvinceHandler handler(int capital, int... owned) {
		Faction faction = mock(Faction.class);
		List<Integer> provinces = new java.util.ArrayList<>();
		for (int id : owned) {
			provinces.add(id);
		}
		return new ProvinceHandler(faction, capital, provinces);
	}

	private Province land(int id) {
		return new Province(id, Terrain.PLAINS.name(), 50);
	}

	private Province sea(int id) {
		return new Province(id, Terrain.SEA.name(), 0);
	}

	private void link(Province a, Province b) {
		a.addNeighbour(b.getId());
		b.addNeighbour(a.getId());
	}

	private void load(Province... provinces) {
		Map<Integer, Province> map = new HashMap<>();
		for (Province province : provinces) {
			map.put(province.getId(), province);
		}
		provinceManager.start(map);
	}
}
