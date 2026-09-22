package me.Plugins.SimpleFactions.Managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

class WarManagerCampaignTest {
	private ProvinceManager pm;
	private Faction attacker;
	private Faction defender;
	private MockedStatic<SimpleFactions> simpleFactions;

	@BeforeEach
	void setUp() {
		Cache.tradeCarry.clear();
		Cache.tradeCarry.put(Terrain.PLAINS, 0.85);
		Cache.warPathfinderNeutralPenalty = 8.0;
		Cache.warPathfinderSeaPassEnabled = true;
		Cache.warPathfinderWaterCost = 0.0;
		Cache.warInitiativeFactor = 1.5;
		Cache.warGoalMaxBattles = new EnumMap<>(WarGoalType.class);
		for (WarGoalType goal : WarGoalType.values()) {
			Cache.warGoalMaxBattles.put(goal, 4);
		}

		pm = new ProvinceManager();
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(pm);
		simpleFactions = mockStatic(SimpleFactions.class);
		simpleFactions.when(SimpleFactions::getInstance).thenReturn(plugin);

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getCapital()).thenReturn(5);
		when(defender.getCapital()).thenReturn(30);

		SettlementHandler handler = mock(SettlementHandler.class);
		when(defender.getSettlementHandler()).thenReturn(handler);
		when(handler.getAll()).thenReturn(List.of());
	}

	@AfterEach
	void tearDown() {
		if (simpleFactions != null) {
			simpleFactions.close();
		}
	}

	@Test
	void regenerateCampaign_populatesAndPersistsFields() {
		buildBorderGraph();

		War war = new War(5, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager);

			assertTrue(WarManager.regenerateCampaign(war, pm));
			assertEquals(30, war.getObjectiveProvinceId());
			assertEquals(20, war.getCampaignStartProvinceId());
			assertEquals(List.of(5, 10, 20, 30), war.getCampaignProvinces());
			assertEquals(2, war.getCursorIndex());
			int expectedAttackerFuel = (int) Math.ceil(
					war.getCampaignBattleSchedule().size() * Cache.warInitiativeFactor);
			int expectedDefenderFuel = (int) Math.ceil(
					war.getCampaignCounterSchedule().size() * Cache.warInitiativeFactor);
			assertEquals(expectedAttackerFuel, war.getInitiativeAttacker());
			assertEquals(expectedDefenderFuel, war.getInitiativeDefender());
		}
	}

	@Test
	void regenerateCampaign_failsWhenNoRoute() {
		Province isolated = province(10, Terrain.PLAINS);
		Province objective = province(30, Terrain.PLAINS);
		pm.start(Map.of(10, isolated, 30, objective));

		War war = new War(6, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(30));
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);

			assertFalse(WarManager.regenerateCampaign(war, pm));
		}
	}

	@Test
	void regenerateCampaign_rejectsRaidWarType() {
		War war = new War(7, attacker, defender);
		war.setWarType(WarType.RAID);

		assertFalse(WarManager.regenerateCampaign(war, pm));
	}

	private void buildBorderGraph() {
		Province atkCapital = province(5, Terrain.PLAINS);
		Province border = province(10, Terrain.PLAINS);
		Province mid = province(20, Terrain.PLAINS);
		Province objective = province(30, Terrain.PLAINS);
		link(atkCapital, border);
		link(border, mid);
		link(mid, objective);
		pm.start(Map.of(5, atkCapital, 10, border, 20, mid, 30, objective));
	}

	private void stubOwnership(MockedStatic<TitleManager> titleManager) {
		titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(20, 30));
		titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
		titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
		titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
		titleManager.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);
	}

	private Province province(int id, Terrain terrain) {
		return new Province(id, terrain.name(), 50, id * 10, id * 10);
	}

	private void link(Province a, Province b) {
		a.addNeighbour(b.getId());
		b.addNeighbour(a.getId());
	}
}
