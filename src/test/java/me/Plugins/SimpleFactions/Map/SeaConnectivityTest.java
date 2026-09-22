package me.Plugins.SimpleFactions.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.Terrain;

class SeaConnectivityTest {
	private ProvinceManager pm;

	@BeforeEach
	void setUp() {
		pm = new ProvinceManager();
	}

	@Test
	void hasSeaConnection_trueOnSharedOcean() {
		Province aLand = province(1, Terrain.PLAINS);
		Province sea1 = province(2, Terrain.SEA);
		Province sea2 = province(3, Terrain.SEA);
		Province bLand = province(4, Terrain.PLAINS);
		link(aLand, sea1);
		link(sea1, sea2);
		link(sea2, bLand);
		load(aLand, sea1, sea2, bLand);

		assertTrue(SeaConnectivity.hasSeaConnection(pm, faction(List.of(1)), faction(List.of(4))));
	}

	@Test
	void hasSeaConnection_falseWhenLandlocked() {
		Province inland = province(1, Terrain.PLAINS);
		Province other = province(2, Terrain.PLAINS);
		Province sea = province(3, Terrain.SEA);
		link(other, sea);
		load(inland, other, sea);

		assertFalse(SeaConnectivity.hasSeaConnection(pm, faction(List.of(1)), faction(List.of(2))));
	}

	@Test
	void hasSeaConnection_falseWhenOceansDisconnected() {
		Province aLand = province(1, Terrain.PLAINS);
		Province oceanA = province(2, Terrain.SEA);
		Province aLand2 = province(5, Terrain.PLAINS);
		Province bLand = province(3, Terrain.PLAINS);
		Province oceanB = province(4, Terrain.SEA);
		link(aLand, oceanA);
		link(oceanA, aLand2);
		link(bLand, oceanB);
		load(aLand, oceanA, aLand2, bLand, oceanB);

		assertFalse(SeaConnectivity.hasSeaConnection(pm, faction(List.of(1)), faction(List.of(3))));
	}

	@Test
	void hasSeaConnection_waterDoesNotBridgeOceans() {
		Province aLand = province(1, Terrain.PLAINS);
		Province oceanA = province(2, Terrain.SEA);
		Province river = province(3, Terrain.WATER);
		Province oceanB = province(4, Terrain.SEA);
		Province bLand = province(5, Terrain.PLAINS);
		link(aLand, oceanA);
		link(oceanA, river);
		link(river, oceanB);
		link(oceanB, bLand);
		load(aLand, oceanA, river, oceanB, bLand);

		assertFalse(SeaConnectivity.hasSeaConnection(pm, faction(List.of(1)), faction(List.of(5))));
	}

	private static Faction faction(List<Integer> provinces) {
		Faction faction = mock(Faction.class);
		when(faction.getProvinces()).thenReturn(provinces);
		return faction;
	}

	private static Province province(int id, Terrain terrain) {
		return new Province(id, terrain.name(), 50);
	}

	private static void link(Province a, Province b) {
		a.addNeighbour(b.getId());
		b.addNeighbour(a.getId());
	}

	private void load(Province... provinces) {
		Map<Integer, Province> map = new HashMap<>();
		for (Province province : provinces) {
			map.put(province.getId(), province);
		}
		pm.start(map);
	}
}
