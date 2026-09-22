package me.Plugins.SimpleFactions.War.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.settlement.Settlement;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

class ObjectiveProvincePickerTest {
	private ProvinceManager pm;
	private ObjectiveProvincePicker picker;

	@BeforeEach
	void setUp() {
		pm = new ProvinceManager();
		picker = new ObjectiveProvincePicker(pm);
	}

	@Test
	void pickObjective_usesCapitalWhenInSet() {
		Faction attacker = mockFaction("atk");
		Faction defender = mockFaction("def");
		when(defender.getCapital()).thenReturn(42);

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(10, 42));

			OptionalInt objective = picker.pickObjective(war, defender);
			assertTrue(objective.isPresent());
			assertEquals(42, objective.getAsInt());
		}
	}

	@Test
	void pickObjective_warUsesSameSetAsSubjugate() {
		Faction attacker = mockFaction("atk");
		Faction defender = mockFaction("def");
		when(defender.getCapital()).thenReturn(42);

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.WAR);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(10, 42));

			OptionalInt objective = picker.pickObjective(war, defender);
			assertTrue(objective.isPresent());
			assertEquals(42, objective.getAsInt());
		}
	}

	@Test
	void pickObjective_usurpUsesDefenderCapitalWhenPresent() {
		Faction attacker = mockFaction("atk");
		Faction defender = mockFaction("def");
		when(defender.getCapital()).thenReturn(42);

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.USURP);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(10, 42));

			OptionalInt objective = picker.pickObjective(war, defender);
			assertTrue(objective.isPresent());
			assertEquals(42, objective.getAsInt());
		}
	}

	@Test
	void pickObjective_capitalInSetBeatsLargerSettlement() {
		Faction attacker = mockFaction("atk");
		Faction defender = mockFaction("def");
		when(defender.getCapital()).thenReturn(10);

		Settlement largeSettlement = new Settlement("town", "Big Town", 20, 0, 0);
		SettlementHandler handler = mock(SettlementHandler.class);
		when(defender.getSettlementHandler()).thenReturn(handler);
		when(handler.getAll()).thenReturn(List.of(largeSettlement));
		when(handler.getPopulation(largeSettlement)).thenReturn(List.of(mock(Guild.class), mock(Guild.class), mock(Guild.class)));

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(10, 20));

			OptionalInt objective = picker.pickObjective(war, defender);
			assertEquals(10, objective.getAsInt());
		}
	}

	@Test
	void pickObjective_usesLargestSettlementByPopulation() {
		Faction attacker = mockFaction("atk");
		Faction defender = mockFaction("def");
		when(defender.getCapital()).thenReturn(-1);

		Settlement small = new Settlement("a", "A", 10, 0, 0);
		Settlement large = new Settlement("b", "B", 20, 0, 0);
		SettlementHandler handler = mock(SettlementHandler.class);
		when(defender.getSettlementHandler()).thenReturn(handler);
		when(handler.getAll()).thenReturn(List.of(small, large));
		when(handler.getPopulation(small)).thenReturn(List.of(mock(Guild.class)));
		when(handler.getPopulation(large)).thenReturn(List.of(mock(Guild.class), mock(Guild.class)));

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(10, 20));

			OptionalInt objective = picker.pickObjective(war, defender);
			assertEquals(20, objective.getAsInt());
		}
	}

	@Test
	void pickObjective_fallsBackToGeometricCenter() {
		Faction attacker = mockFaction("atk");
		Faction defender = mockFaction("def");
		when(defender.getCapital()).thenReturn(-1);

		SettlementHandler handler = mock(SettlementHandler.class);
		when(defender.getSettlementHandler()).thenReturn(handler);
		when(handler.getAll()).thenReturn(List.of());

		Province near = new Province(10, Terrain.PLAINS.name(), 50, 0, 0);
		Province far = new Province(20, Terrain.PLAINS.name(), 50, 100, 100);
		pm.start(Map.of(10, near, 20, far));

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(10, 20));

			OptionalInt objective = picker.pickObjective(war, defender);
			assertEquals(10, objective.getAsInt());
		}
	}

	@Test
	void pickObjective_pillageUsesSettlementCenter() {
		Faction attacker = mockFaction("atk");
		Faction defender = mockFaction("def");
		Settlement settlement = new Settlement("town", "Town", 11, 0, 0);
		SettlementHandler handler = mock(SettlementHandler.class);
		when(defender.getSettlementHandler()).thenReturn(handler);
		when(handler.getById("town")).thenReturn(settlement);

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.PILLAGE);
		war.setTargetSettlementId("town");

		FactionManager.factions.add(defender);
		try {
			OptionalInt objective = picker.pickObjective(war, defender);
			assertEquals(11, objective.getAsInt());
		} finally {
			FactionManager.factions.remove(defender);
		}
	}

	private static Faction mockFaction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		return faction;
	}
}
