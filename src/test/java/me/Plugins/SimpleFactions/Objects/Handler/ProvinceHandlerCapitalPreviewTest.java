package me.Plugins.SimpleFactions.Objects.Handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.settlement.handler.CapitalResult;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

class ProvinceHandlerCapitalPreviewTest {
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
	void preview_returnsEmptyWhenCapitalUnchanged() {
		Faction faction = mock(Faction.class);
		ProvinceHandler handler = new ProvinceHandler(faction, 1, List.of(1, 2, 3));
		when(faction.getGuildHandler()).thenReturn(new GuildHandler(faction));

		assertEquals(List.of(), handler.previewProvincesLostIfCapitalMoved(1));
	}

	@Test
	void preview_returnsDisconnectedProvincesWhenCapitalMoves() {
		Province one = province(1, Terrain.PLAINS);
		Province two = province(2, Terrain.PLAINS);
		Province four = province(4, Terrain.PLAINS);
		Province five = province(5, Terrain.PLAINS);
		link(one, two);
		link(four, five);
		load(one, two, four, five);

		Faction faction = mock(Faction.class);
		when(faction.getGuildHandler()).thenReturn(new GuildHandler(faction));
		ProvinceHandler handler = new ProvinceHandler(faction, 1, List.of(1, 2, 4, 5));

		List<Integer> lost = handler.previewProvincesLostIfCapitalMoved(5);

		assertEquals(List.of(1, 2), lost);
	}

	@Test
	void preview_returnsEmptyWhenMoveKeepsRealmConnected() {
		Province one = province(1, Terrain.PLAINS);
		Province two = province(2, Terrain.PLAINS);
		Province three = province(3, Terrain.PLAINS);
		link(one, two);
		link(two, three);
		load(one, two, three);

		Faction faction = mock(Faction.class);
		when(faction.getGuildHandler()).thenReturn(new GuildHandler(faction));
		ProvinceHandler handler = new ProvinceHandler(faction, 1, List.of(1, 2, 3));

		assertEquals(List.of(), handler.previewProvincesLostIfCapitalMoved(3));
	}

	@Test
	void validateFactionCapital_allowsCrossCityMove() {
		Province one = province(100, Terrain.PLAINS);
		Province two = province(200, Terrain.PLAINS);
		load(one, two);

		Faction faction = mock(Faction.class);
		SettlementHandler settlements = new SettlementHandler(faction);

		when(faction.ownsProvince(100)).thenReturn(true);
		when(faction.ownsProvince(200)).thenReturn(true);
		when(faction.getCapital()).thenReturn(100);

		settlements.found("Old City", 100, 0, 0);
		settlements.found("New City", 200, 0, 0);

		CapitalResult result = settlements.validateFactionCapital(null, 200, null);

		assertTrue(result.isSuccess());
	}

	@Test
	void validateFactionCapital_requiresNameWhenNoSettlement() {
		Province one = province(100, Terrain.PLAINS);
		Province three = province(300, Terrain.PLAINS);
		load(one, three);

		Faction faction = mock(Faction.class);
		SettlementHandler settlements = new SettlementHandler(faction);

		when(faction.ownsProvince(100)).thenReturn(true);
		when(faction.ownsProvince(300)).thenReturn(true);
		when(faction.getCapital()).thenReturn(100);
		settlements.found("Old City", 100, 0, 0);

		CapitalResult withoutName = settlements.validateFactionCapital(null, 300, null);
		CapitalResult withName = settlements.validateFactionCapital(null, 300, "Frontier");

		assertFalse(withoutName.isSuccess());
		assertTrue(withName.isSuccess());
	}

	private Province province(int id, Terrain terrain) {
		return new Province(id, terrain.name(), 50);
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
