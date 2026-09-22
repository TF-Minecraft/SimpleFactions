package me.Plugins.SimpleFactions.War.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;

class CallToArmsEligibilityTest {
	private final List<Faction> savedFactions = new ArrayList<>();

	private Faction king;
	private Faction dukeA;
	private Faction dukeB;
	private Faction dukeC;
	private Faction countB;
	private Faction france;
	private Faction frenchDuke;
	private War internalWar;

	@BeforeEach
	void setUp() {
		savedFactions.addAll(FactionManager.factions);
		FactionManager.factions.clear();

		king = mockIndependent("king");
		dukeA = mockSubjectWithAllies("dukeA", "king", "dukeC", "frenchDuke");
		dukeB = mockSubject("dukeB", "king");
		dukeC = mockSubject("dukeC", "king");
		countB = mockSubject("countB", "dukeB");
		france = mockIndependent("france");
		frenchDuke = mockSubject("frenchDuke", "france");
		FactionManager.factions.add(king);
		FactionManager.factions.add(dukeA);
		FactionManager.factions.add(dukeB);
		FactionManager.factions.add(dukeC);
		FactionManager.factions.add(countB);
		FactionManager.factions.add(france);
		FactionManager.factions.add(frenchDuke);

		internalWar = new War(1, dukeA, dukeB);
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
	}

	@Test
	void thirdSiblingAlly_allowed() {
		CallToArmsEligibility.Result result = CallToArmsEligibility.canCall(internalWar, dukeA, dukeC);
		assertTrue(result.allowed());
	}

	@Test
	void king_deniedAsOverlord() {
		CallToArmsEligibility.Result result = CallToArmsEligibility.canCall(internalWar, dukeA, king);
		assertFalse(result.allowed());
		assertEquals(CallToArmsEligibility.OVERLORD, result.message());
	}

	@Test
	void enemyCount_deniedBoundToEnemy() {
		CallToArmsEligibility.Result result = CallToArmsEligibility.canCall(internalWar, dukeA, countB);
		assertFalse(result.allowed());
		assertEquals(CallToArmsEligibility.BOUND_TO_ENEMY, result.message());
	}

	@Test
	void foreignVassal_allowedWhenLiegeNotMain() {
		CallToArmsEligibility.Result result = CallToArmsEligibility.canCall(internalWar, dukeA, frenchDuke);
		assertTrue(result.allowed());
	}

	@Test
	void foreignVassal_deniedWhenLiegeIsMain() {
		internalWar.getAttackers().addNewParticipant(france, internalWar.getAttackers().getMainParticipants().get(0));
		CallToArmsEligibility.Result result = CallToArmsEligibility.canCall(internalWar, dukeA, frenchDuke);
		assertFalse(result.allowed());
		assertEquals(CallToArmsEligibility.LIEGE_ALREADY_MAIN, result.message());
	}

	@Test
	void enemyMain_deniedParticipating() {
		CallToArmsEligibility.Result result = CallToArmsEligibility.canCall(internalWar, dukeA, dukeB);
		assertFalse(result.allowed());
		assertEquals(CallToArmsEligibility.ALREADY_IN_WAR, result.message());
	}

	@Test
	void nullsAndNonMain_denied() {
		assertFalse(CallToArmsEligibility.canCall(null, dukeA, dukeC).allowed());
		assertFalse(CallToArmsEligibility.canCall(internalWar, null, dukeC).allowed());
		assertFalse(CallToArmsEligibility.canCall(internalWar, dukeA, null).allowed());
		assertFalse(CallToArmsEligibility.canCall(internalWar, dukeC, dukeA).allowed());
		assertTrue(internalWar.canBeCalled(dukeA, dukeC));
		assertFalse(internalWar.canBeCalled(dukeC, dukeA));
	}

	private static Faction mockIndependent(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getRelations()).thenReturn(new HashMap<>());
		when(faction.getMembers()).thenReturn(new ArrayList<>());
		return faction;
	}

	private static Faction mockSubject(String id, String overlordId) {
		return mockSubjectWithAllies(id, overlordId);
	}

	private static Faction mockSubjectWithAllies(String id, String overlordId, String... allyIds) {
		Faction faction = mockIndependent(id);
		HashMap<String, Relation> relations = new HashMap<>();
		relations.put(overlordId, overlordRelation());
		for (String allyId : allyIds) {
			relations.put(allyId, allyRelation());
		}
		when(faction.getRelations()).thenReturn(relations);
		when(faction.getRelation(overlordId)).thenReturn(relations.get(overlordId));
		return faction;
	}

	private static Relation overlordRelation() {
		Relation relation = mock(Relation.class);
		RelationType type = mock(RelationType.class);
		when(type.isOverlord()).thenReturn(true);
		when(type.getId()).thenReturn("subject");
		when(relation.getType()).thenReturn(type);
		return relation;
	}

	private static Relation allyRelation() {
		Relation relation = mock(Relation.class);
		RelationType type = mock(RelationType.class);
		when(type.isOverlord()).thenReturn(false);
		when(type.getId()).thenReturn("ally");
		when(relation.getType()).thenReturn(type);
		return relation;
	}
}
