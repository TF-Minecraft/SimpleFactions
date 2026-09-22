package me.Plugins.SimpleFactions.War.declare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import me.Plugins.SimpleFactions.War.core.War;

class InterVassalQueriesTest {
	private final List<Faction> savedFactions = new ArrayList<>();

	private Faction king;
	private Faction dukeA;
	private Faction dukeB;
	private Faction countA;
	private Faction dukeC;

	@BeforeEach
	void setUp() {
		savedFactions.addAll(FactionManager.factions);
		FactionManager.factions.clear();

		king = mockIndependent("king");
		dukeA = mockSubject("dukeA", "king");
		dukeB = mockSubject("dukeB", "king");
		countA = mockSubject("countA", "dukeA");
		dukeC = mockSubject("dukeC", "king");
		FactionManager.factions.add(king);
		FactionManager.factions.add(dukeA);
		FactionManager.factions.add(dukeB);
		FactionManager.factions.add(countA);
		FactionManager.factions.add(dukeC);
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
	}

	@Test
	void topLiegeId_independentIsNull() {
		assertNull(InterVassalQueries.topLiegeId(king));
		assertNull(InterVassalQueries.topLiegeId(null));
	}

	@Test
	void topLiegeId_walksToKing() {
		assertEquals("king", InterVassalQueries.topLiegeId(dukeA));
		assertEquals("king", InterVassalQueries.topLiegeId(countA));
	}

	@Test
	void siblingsShareTopLiegeAndAreInternalPeers() {
		assertTrue(InterVassalQueries.sharesTopLiege(dukeA, dukeB));
		assertTrue(InterVassalQueries.isInternalPeer(dukeA, dukeB));
		assertFalse(InterVassalQueries.isNestedUnder(dukeA, dukeB));
	}

	@Test
	void cousinCountVersusOtherDukeIsInternalPeer() {
		assertTrue(InterVassalQueries.sharesTopLiege(countA, dukeB));
		assertTrue(InterVassalQueries.isInternalPeer(countA, dukeB));
		assertFalse(InterVassalQueries.isNestedUnder(countA, dukeB));
	}

	@Test
	void vassalVersusLiegeIsNotInternalPeer() {
		assertFalse(InterVassalQueries.sharesTopLiege(dukeA, king));
		assertFalse(InterVassalQueries.isInternalPeer(dukeA, king));
		assertTrue(InterVassalQueries.isNestedUnder(dukeA, king));
		assertTrue(InterVassalQueries.isNestedUnder(countA, dukeA));
		assertFalse(InterVassalQueries.isInternalPeer(countA, dukeA));
	}

	@Test
	void independentsDoNotShareTopLiege() {
		Faction other = mockIndependent("other");
		FactionManager.factions.add(other);
		assertFalse(InterVassalQueries.sharesTopLiege(king, other));
		assertFalse(InterVassalQueries.isInternalPeer(king, other));
	}

	@Test
	void nullArgsAreFalse() {
		assertFalse(InterVassalQueries.sharesTopLiege(null, dukeA));
		assertFalse(InterVassalQueries.isInternalPeer(dukeA, null));
		assertFalse(InterVassalQueries.isNestedUnder(null, king));
		assertFalse(InterVassalQueries.isOverlordOfMain(king, null));
		assertFalse(InterVassalQueries.isOverlordOfMain(null, new War(1, dukeA, dukeB)));
	}

	@Test
	void isOverlordOfMain_kingTrueUninvolvedSiblingFalse() {
		War war = new War(1, dukeA, dukeB);
		assertTrue(InterVassalQueries.isOverlordOfMain(king, war));
		assertFalse(InterVassalQueries.isOverlordOfMain(dukeC, war));
		assertFalse(InterVassalQueries.isOverlordOfMain(countA, war));
	}

	private static Faction mockIndependent(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getRelations()).thenReturn(new HashMap<>());
		when(faction.getMembers()).thenReturn(new ArrayList<>());
		return faction;
	}

	private static Faction mockSubject(String id, String overlordId) {
		Faction faction = mockIndependent(id);
		Relation relation = mock(Relation.class);
		RelationType type = mock(RelationType.class);
		when(type.isOverlord()).thenReturn(true);
		when(type.getId()).thenReturn("subject");
		when(relation.getType()).thenReturn(type);
		HashMap<String, Relation> relations = new HashMap<>();
		relations.put(overlordId, relation);
		when(faction.getRelations()).thenReturn(relations);
		when(faction.getRelation(overlordId)).thenReturn(relation);
		return faction;
	}
}
