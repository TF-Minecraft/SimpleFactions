package me.Plugins.SimpleFactions.War.campaign.zoc;

import static me.Plugins.SimpleFactions.War.campaign.zoc.PortSeaZocIndex.OperationalPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.enums.Terrain;

class PortSeaZocIndexTest {
	private ProvinceManager pm;
	private MockedStatic<SimpleFactions> simpleFactions;
	private Faction defender;

	@BeforeEach
	void setUp() {
		Cache.warPortSeaZocRadius = 2;
		pm = new ProvinceManager();
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(pm);
		simpleFactions = mockStatic(SimpleFactions.class);
		simpleFactions.when(SimpleFactions::getInstance).thenReturn(plugin);

		defender = mock(Faction.class);
		when(defender.getId()).thenReturn("def");
	}

	@AfterEach
	void tearDown() {
		if (simpleFactions != null) {
			simpleFactions.close();
		}
	}

	@Test
	void portForSeaProvince_coversAdjacentSeaTiles() {
		Province coast = province(10, Terrain.PLAINS);
		Province sea = seaProvince(11);
		Province deepSea = seaProvince(12);
		link(coast, sea);
		link(sea, deepSea);
		pm.start(Map.of(10, coast, 11, sea, 12, deepSea));

		PortSeaZocIndex index = PortSeaZocIndex.fromPorts(List.of(
				new OperationalPort("port_a", defender, 10, 100L)));

		assertEquals("port_a", index.portForSeaProvince(11).orElseThrow().id());
		assertEquals("port_a", index.portForSeaProvince(12).orElseThrow().id());
	}

	@Test
	void portForSeaProvince_respectsRadius() {
		Cache.warPortSeaZocRadius = 1;
		Province coast = province(10, Terrain.PLAINS);
		Province sea = seaProvince(11);
		Province deepSea = seaProvince(12);
		link(coast, sea);
		link(sea, deepSea);
		pm.start(Map.of(10, coast, 11, sea, 12, deepSea));

		PortSeaZocIndex index = PortSeaZocIndex.fromPorts(List.of(
				new OperationalPort("port_a", defender, 10, 100L)));

		assertEquals("port_a", index.portForSeaProvince(11).orElseThrow().id());
		assertTrue(index.portForSeaProvince(12).isEmpty());
	}

	@Test
	void portForSeaProvince_oldestPortWinsOverlappingSea() {
		Province coastOld = province(10, Terrain.PLAINS);
		Province coastYoung = province(20, Terrain.PLAINS);
		Province sea = seaProvince(11);
		link(coastOld, sea);
		link(coastYoung, sea);
		pm.start(Map.of(10, coastOld, 11, sea, 20, coastYoung));

		OperationalPort older = new OperationalPort("port_old", defender, 10, 100L);
		OperationalPort younger = new OperationalPort("port_young", defender, 20, 200L);
		PortSeaZocIndex index = PortSeaZocIndex.fromPorts(List.of(younger, older));

		assertEquals("port_old", index.portForSeaProvince(11).orElseThrow().id());
	}

	@Test
	void portForSeaProvince_tieBreaksById() {
		Province coastA = province(10, Terrain.PLAINS);
		Province coastB = province(20, Terrain.PLAINS);
		Province sea = seaProvince(11);
		link(coastA, sea);
		link(coastB, sea);
		pm.start(Map.of(10, coastA, 11, sea, 20, coastB));

		OperationalPort portB = new OperationalPort("port_b", defender, 20, 100L);
		OperationalPort portA = new OperationalPort("port_a", defender, 10, 100L);
		PortSeaZocIndex index = PortSeaZocIndex.fromPorts(List.of(portB, portA));

		assertEquals("port_a", index.portForSeaProvince(11).orElseThrow().id());
	}

	@Test
	void portsCoveringSeaProvinces_returnsDistinctPorts() {
		Province coast = province(10, Terrain.PLAINS);
		Province seaA = seaProvince(11);
		Province seaB = seaProvince(12);
		link(coast, seaA);
		link(seaA, seaB);
		pm.start(Map.of(10, coast, 11, seaA, 12, seaB));

		PortSeaZocIndex index = PortSeaZocIndex.fromPorts(List.of(
				new OperationalPort("port_a", defender, 10, 100L)));

		assertEquals(1, index.portsCoveringSeaProvinces(List.of(11, 12)).size());
	}

	private Province province(int id, Terrain terrain) {
		return new Province(id, terrain.name(), 50, id * 10, id * 10);
	}

	private Province seaProvince(int id) {
		return province(id, Terrain.SEA);
	}

	private void link(Province a, Province b) {
		a.addNeighbour(b.getId());
		b.addNeighbour(a.getId());
	}
}
