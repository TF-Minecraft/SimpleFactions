package me.Plugins.SimpleFactions.War.campaign.zoc;

import static me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex.OperationalFort;

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

import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.enums.Terrain;

class FortZocIndexTest {
	private ProvinceManager pm;
	private MockedStatic<SimpleFactions> simpleFactions;
	private MockedStatic<TitleManager> titleManager;
	private Faction defender;

	@BeforeEach
	void setUp() {
		pm = new ProvinceManager();
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(pm);
		simpleFactions = mockStatic(SimpleFactions.class);
		simpleFactions.when(SimpleFactions::getInstance).thenReturn(plugin);

		defender = mock(Faction.class);
		when(defender.getId()).thenReturn("def");

		titleManager = mockStatic(TitleManager.class);
	}

	@AfterEach
	void tearDown() {
		if (titleManager != null) {
			titleManager.close();
		}
		if (simpleFactions != null) {
			simpleFactions.close();
		}
	}

	@Test
	void fortForProvince_oldestFortWinsOverlappingZoc() {
		Province p20 = province(20);
		Province p21 = province(21);
		Province p22 = province(22);
		link(p20, p21);
		link(p21, p22);
		pm.start(Map.of(20, p20, 21, p21, 22, p22));
		stubDefenderOwnership(20, 21, 22);

		OperationalFort older = new OperationalFort("fort_old", defender, 20, 100L);
		OperationalFort younger = new OperationalFort("fort_young", defender, 22, 200L);
		FortZocIndex index = FortZocIndex.fromForts(List.of(younger, older));

		assertEquals("fort_old", index.fortForProvince(21).orElseThrow().id());
		assertEquals("fort_old", index.fortForProvince(20).orElseThrow().id());
		assertEquals(
				List.of("fort_old", "fort_young"),
				index.fortsCovering(21).stream().map(OperationalFort::id).toList());
	}

	@Test
	void fortForProvince_tieBreaksById() {
		Province p20 = province(20);
		Province p21 = province(21);
		Province p22 = province(22);
		link(p20, p21);
		link(p21, p22);
		pm.start(Map.of(20, p20, 21, p21, 22, p22));
		stubDefenderOwnership(20, 21, 22);

		OperationalFort fortB = new OperationalFort("fort_b", defender, 22, 100L);
		OperationalFort fortA = new OperationalFort("fort_a", defender, 20, 100L);
		FortZocIndex index = FortZocIndex.fromForts(List.of(fortB, fortA));

		assertEquals("fort_a", index.fortForProvince(21).orElseThrow().id());
	}

	@Test
	void fortForProvince_emptyWhenOutsideZoc() {
		Province p20 = province(20);
		pm.start(Map.of(20, p20));
		stubDefenderOwnership(20);

		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_a", defender, 20, 100L)));

		assertTrue(index.fortForProvince(99).isEmpty());
	}

	private void stubDefenderOwnership(int... provinceIds) {
		for (int provinceId : provinceIds) {
			titleManager.when(() -> TitleManager.getByProvince(provinceId)).thenReturn(defender);
		}
	}

	private Province province(int id) {
		return new Province(id, Terrain.PLAINS.name(), 50, id * 10, id * 10);
	}

	private void link(Province a, Province b) {
		a.addNeighbour(b.getId());
		b.addNeighbour(a.getId());
	}
}
