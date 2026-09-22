package me.Plugins.SimpleFactions.War.civilwar.wartime;


import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarLandSplitService;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarSeaPortGate;
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
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarLandSplitService.LandSplitPlan;

class CivilWarSeaPortGateTest {
	private ProvinceManager pm;

	@BeforeEach
	void setUp() {
		pm = new ProvinceManager();
	}

	@Test
	void landConnected_doesNotRequirePort() {
		Province a = province(1, Terrain.PLAINS);
		Province b = province(2, Terrain.PLAINS);
		link(a, b);
		load(a, b);

		Faction host = mock(Faction.class);
		when(host.getCapital()).thenReturn(1);
		LandSplitPlan plan = new LandSplitPlan(List.of(2), List.of(1));

		assertTrue(CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(pm, host, plan));
	}

	@Test
	void seaBetweenWithoutPort_refuses() {
		Province hostLand = province(1, Terrain.PLAINS);
		Province sea = province(2, Terrain.SEA);
		Province rebelLand = province(3, Terrain.PLAINS);
		link(hostLand, sea);
		link(sea, rebelLand);
		load(hostLand, sea, rebelLand);

		Faction host = mock(Faction.class);
		when(host.getCapital()).thenReturn(1);
		InstallationHandler handler = new InstallationHandler(host);
		when(host.getInstallationHandler()).thenReturn(handler);
		LandSplitPlan plan = new LandSplitPlan(List.of(3), List.of(1));

		assertFalse(CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(pm, host, plan));
	}

	@Test
	void seaBetweenWithPortOnRebelTile_allows() {
		Province hostLand = province(1, Terrain.PLAINS);
		Province sea = province(2, Terrain.SEA);
		Province rebelLand = province(3, Terrain.PLAINS);
		link(hostLand, sea);
		link(sea, rebelLand);
		load(hostLand, sea, rebelLand);

		Faction host = mock(Faction.class);
		when(host.getCapital()).thenReturn(1);
		InstallationHandler handler = new InstallationHandler(host);
		when(host.getInstallationHandler()).thenReturn(handler);
		handler.acceptTransferred(new Installation("port-1", "Harbour", InstallationKind.PORT, 3, 0, 0, 1L));
		LandSplitPlan plan = new LandSplitPlan(List.of(3), List.of(1));

		assertTrue(CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(pm, host, plan));
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
