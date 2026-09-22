package me.Plugins.SimpleFactions.Map.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;

class ZocRealmTest {
	private Faction attacker;
	private Faction defender;
	private MockedStatic<SimpleFactions> simpleFactions;
	private MockedStatic<TitleManager> titleManager;
	private ProvinceManager pm;

	@BeforeEach
	void setUp() {
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");

		pm = new ProvinceManager();
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(pm);
		simpleFactions = mockStatic(SimpleFactions.class);
		simpleFactions.when(SimpleFactions::getInstance).thenReturn(plugin);
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
	void resolveExportControllerFaction_noActiveWar_returnsOwner() {
		Installation fort = fort("fort_a", 20);

		Faction resolved = ZocRealm.resolveExportControllerFaction(fort, defender, List.of());

		assertEquals(defender, resolved);
	}

	@Test
	void resolveExportControllerFaction_flippedController_returnsCoalitionLeader() {
		Installation fort = fort("fort_a", 20);
		War war = war(1, List.of(5, 10, 20, 30));
		war.putFortController("fort_a", CampaignCoalition.AGGRESSOR);

		Faction resolved = ZocRealm.resolveExportControllerFaction(fort, defender, List.of(war));

		assertEquals(attacker, resolved);
	}

	@Test
	void computeZocProvincesForExport_usesControllerRealmNeighbors() {
		Province fortProvince = province(20);
		Province defenderNeighbor = province(21);
		Province attackerNeighbor = province(22);
		link(fortProvince, defenderNeighbor);
		link(fortProvince, attackerNeighbor);
		pm.start(Map.of(20, fortProvince, 21, defenderNeighbor, 22, attackerNeighbor));
		stubOwnership(21, defender);
		stubOwnership(22, attacker);

		Installation fort = fort("fort_a", 20);
		War war = war(1, List.of(5, 10, 20, 30));
		war.putFortController("fort_a", CampaignCoalition.AGGRESSOR);

		List<Integer> zoc = ZocRealm.computeZocProvincesForExport(fort, defender, List.of(war));

		assertTrue(zoc.contains(20));
		assertTrue(zoc.contains(22));
		assertTrue(zoc.stream().noneMatch(id -> id == 21));
	}

	@Test
	void selectPrimaryWarForFort_multipleWars_prefersAxisWar() {
		Installation fort = fort("fort_a", 20);
		War offAxis = war(2, List.of(5, 10, 30));
		offAxis.putFortController("fort_a", CampaignCoalition.DEFENDER);
		War onAxis = war(1, List.of(5, 10, 20, 30));
		onAxis.putFortController("fort_a", CampaignCoalition.AGGRESSOR);

		War selected = ZocRealm.selectPrimaryWarForFort(fort, List.of(offAxis, onAxis));

		assertEquals(onAxis, selected);
	}

	@Test
	void selectPrimaryWarForFort_multipleWarsNoAxis_returnsNull() {
		Installation fort = fort("fort_a", 20);
		War warA = war(1, List.of(5, 10, 30));
		warA.putFortController("fort_a", CampaignCoalition.DEFENDER);
		War warB = war(2, List.of(5, 15, 25));
		warB.putFortController("fort_a", CampaignCoalition.AGGRESSOR);

		assertNull(ZocRealm.selectPrimaryWarForFort(fort, List.of(warA, warB)));
		assertEquals(defender, ZocRealm.resolveExportControllerFaction(fort, defender, List.of(warA, warB)));
	}

	@Test
	void selectPrimaryWarForFort_scheduleReference_qualifiesSingleWar() {
		Installation fort = fort("fort_a", 20);
		War war = war(1, List.of(5, 10, 30));
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(18, CampaignBattleKind.SIEGE, false, "fort_a")));

		assertEquals(war, ZocRealm.selectPrimaryWarForFort(fort, List.of(war)));
	}

	private War war(int id, List<Integer> axis) {
		War war = new War(id, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setCampaignProvinces(axis);
		return war;
	}

	private Installation fort(String id, int provinceId) {
		return new Installation(id, "Fort", InstallationKind.FORT, provinceId, 0, 0, 100L);
	}

	private Province province(int id) {
		return new Province(id, Terrain.PLAINS.name(), 50, id * 10, id * 10);
	}

	private void link(Province a, Province b) {
		a.addNeighbour(b.getId());
		b.addNeighbour(a.getId());
	}

	private void stubOwnership(int provinceId, Faction owner) {
		titleManager.when(() -> TitleManager.getByProvince(provinceId)).thenReturn(owner);
	}
}
