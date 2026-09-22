package me.Plugins.SimpleFactions.War.commitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.enums.FactionModifiers;

class LevySnapshotCalculatorTest {
	@Test
	void findNearestFighterHolder_returnsNearestNotTopOverlord() {
		Faction v3 = faction("v3", 5);
		Faction v2 = faction("v2", 5);
		Faction v = faction("v", 5);
		Faction m = faction("m", 5);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			relations.when(() -> RelationManager.getOverlord(v3)).thenReturn("v2");
			relations.when(() -> RelationManager.getOverlord(v2)).thenReturn("v");
			relations.when(() -> RelationManager.getOverlord(v)).thenReturn("m");
			relations.when(() -> RelationManager.getOverlord(m)).thenReturn(null);
			factions.when(() -> FactionManager.getByString("v2")).thenReturn(v2);
			factions.when(() -> FactionManager.getByString("v")).thenReturn(v);
			factions.when(() -> FactionManager.getByString("m")).thenReturn(m);

			Set<String> fighters = Set.of("m", "v");
			assertEquals(v, LevySnapshotCalculator.findNearestFighterHolder(v3, fighters));
			assertEquals(v, LevySnapshotCalculator.findNearestFighterHolder(v2, fighters));
		}
	}

	@Test
	void findNearestFighterHolder_returnsNullWhenNoFighterOnChain() {
		Faction v2 = faction("v2", 5);
		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			relations.when(() -> RelationManager.getOverlord(v2)).thenReturn(null);
			assertNull(LevySnapshotCalculator.findNearestFighterHolder(v2, Set.of("m")));
		}
	}

	@Test
	void levyContribution_appliesIntermediateModifier() {
		Faction v3 = faction("v3", 10);
		Faction v2 = faction("v2", 10);
		Faction v = faction("v", 10);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			relations.when(() -> RelationManager.getOverlord(v3)).thenReturn("v2");
			relations.when(() -> RelationManager.getOverlord(v2)).thenReturn("v");
			factions.when(() -> FactionManager.getByString("v2")).thenReturn(v2);
			factions.when(() -> FactionManager.getByString("v")).thenReturn(v);

			assertEquals(5, LevySnapshotCalculator.levyContribution(v2, v));
			assertEquals(3, LevySnapshotCalculator.levyContribution(v3, v));
		}
	}

	@Test
	void collectLevyRows_assignsNearestFighterHolder() {
		Faction m = faction("m", 0);
		Faction v = fighter("v", 10);
		Faction v2 = faction("v2", 8);
		Faction v3 = faction("v3", 4);
		Faction d = fighter("d", 0);

		War war = new War(1, m, d);
		Participant attacker = war.getAttackers().getMainParticipants().get(0);
		attacker.getSubjects().add(v);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			stubSubjects(relations, Map.of(
					"m", List.of(v),
					"v", List.of(v2),
					"v2", List.of(v3),
					"v3", List.of()));
			Map<String, String> overlordMap = new HashMap<>();
			overlordMap.put("m", null);
			overlordMap.put("v", "m");
			overlordMap.put("v2", "v");
			overlordMap.put("v3", "v2");
			stubOverlords(relations, factions, overlordMap, Map.of(
					"m", m,
					"v", v,
					"v2", v2,
					"v3", v3));
			relations.when(() -> RelationManager.sameRealm(any(Faction.class), any(Faction.class))).thenReturn(true);

			Map<String, LevySnapshotCalculator.LevyRow> rows =
					LevySnapshotCalculator.collectLevyRows(war.getAttackers());

			assertEquals(2, rows.size());
			assertEquals(4, rows.get(LevySnapshotCalculator.levyKey("v", "v2")).count());
			assertEquals(1, rows.get(LevySnapshotCalculator.levyKey("v", "v3")).count());
			assertFalse(rows.containsKey(LevySnapshotCalculator.levyKey("m", "v2")));
		}
	}

	private static Faction fighter(String id, int slots) {
		return faction(id, slots);
	}

	private static Faction faction(String id, int slots) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getMembers()).thenReturn(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"));
		FactionModifier levy = mock(FactionModifier.class);
		when(levy.getAmount()).thenReturn(50.0);
		when(faction.getModifier(FactionModifiers.LEVY)).thenReturn(levy);

		Military military = mock(Military.class);
		Regiment regiment = mock(Regiment.class);
		when(regiment.isLevy()).thenReturn(false);
		when(regiment.getId()).thenReturn("infantry");
		when(regiment.getCurrentSlots()).thenReturn(slots);
		when(military.getRegiments()).thenReturn(List.of(regiment));
		when(faction.getMilitary()).thenReturn(military);
		return faction;
	}

	private static void stubSubjects(MockedStatic<RelationManager> relations, Map<String, List<Faction>> subjects) {
		for (Map.Entry<String, List<Faction>> entry : subjects.entrySet()) {
			Faction key = mock(Faction.class);
			when(key.getId()).thenReturn(entry.getKey());
			relations.when(() -> RelationManager.getSubjects(key)).thenReturn(entry.getValue());
		}
		relations.when(() -> RelationManager.getSubjects(org.mockito.ArgumentMatchers.any(Faction.class)))
				.thenAnswer(invocation -> {
					Faction faction = invocation.getArgument(0);
					return subjects.getOrDefault(faction.getId(), List.of());
				});
	}

	private static void stubOverlords(
			MockedStatic<RelationManager> relations,
			MockedStatic<FactionManager> factions,
			Map<String, String> overlords,
			Map<String, Faction> factionById) {
		relations.when(() -> RelationManager.getOverlord(org.mockito.ArgumentMatchers.any(Faction.class)))
				.thenAnswer(invocation -> {
					Faction faction = invocation.getArgument(0);
					return overlords.get(faction.getId());
				});
		factions.when(() -> FactionManager.getByString(org.mockito.ArgumentMatchers.anyString()))
				.thenAnswer(invocation -> {
					String id = invocation.getArgument(0);
					return factionById.get(id);
				});
	}
}
