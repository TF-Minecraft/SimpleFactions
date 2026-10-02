package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.diplomacy.DiplomacyHandler;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.objects.Faction;

class RelationManagerCapacityGateTest {

	@Test
	void decreaseIsAllowedWhenAlreadyOverCapacity() {
		assertFalse(RelationManager.lacksCapacityForChange(-5, 0, 4));
		assertFalse(RelationManager.lacksCapacityForChange(-1, 1, 3));
		assertFalse(RelationManager.lacksCapacityForChange(0, 0, 0));
	}

	@Test
	void increaseIsBlockedUnlessFreeCapacityCoversIt() {
		assertTrue(RelationManager.lacksCapacityForChange(-5, 2, 0));
		assertTrue(RelationManager.lacksCapacityForChange(1, 4, 2));
		assertFalse(RelationManager.lacksCapacityForChange(2, 4, 2));
		assertFalse(RelationManager.lacksCapacityForChange(3, 4, 2));
	}

	@Test
	void endingAllianceWhileOverCapacityDoesNotLackCapacity() {
		Pair pair = alliance();
		when(pair.originHandler.getAvailableCapacity()).thenReturn(-6.0);
		when(pair.targetHandler.getAvailableCapacity()).thenReturn(-2.0);

		assertFalse(RelationManager.actorLacksCapacity(pair.origin, pair.target, pair.none, pair.ally));
		assertFalse(RelationManager.partnerLacksRelationCapacity(pair.origin, pair.target, pair.none));
		assertEquals(pair.none, RelationManager.partnerRelationAfter(pair.target, pair.origin, pair.none));
	}

	@Test
	void newAllianceWhileOverCapacityStillLacksCapacity() {
		Pair pair = alliance();
		when(pair.origin.getRelation("target")).thenReturn(new Relation(pair.none, null));
		when(pair.target.getRelation("origin")).thenReturn(new Relation(pair.none, null));
		when(pair.originHandler.getAvailableCapacity()).thenReturn(-1.0);
		when(pair.targetHandler.getAvailableCapacity()).thenReturn(100.0);

		assertTrue(RelationManager.actorLacksCapacity(pair.origin, pair.target, pair.ally, pair.none));
		assertFalse(RelationManager.partnerLacksRelationCapacity(pair.origin, pair.target, pair.ally));
	}

	@Test
	void releasingSubjectWhileOverCapacityDoesNotLackCapacity() {
		Pair pair = alliance();
		RelationType subject = relation("subject", 1.0, true, true);
		RelationType overlord = relation("overlord", 5.0, false, false);
		when(overlord.isSettable()).thenReturn(false);
		when(subject.isVassalage()).thenReturn(true);
		when(overlord.isOverlord()).thenReturn(true);
		when(pair.origin.getRelation("target")).thenReturn(new Relation(subject, null));
		when(pair.target.getRelation("origin")).thenReturn(new Relation(overlord, null));
		when(pair.originHandler.getAvailableCapacity()).thenReturn(-8.0);
		when(pair.targetHandler.getAvailableCapacity()).thenReturn(-8.0);

		assertFalse(RelationManager.actorLacksCapacity(pair.origin, pair.target, pair.none, subject));
		assertFalse(RelationManager.partnerLacksRelationCapacity(pair.origin, pair.target, pair.none));
	}

	private static Pair alliance() {
		Faction origin = mock(Faction.class);
		Faction target = mock(Faction.class);
		DiplomacyHandler originHandler = mock(DiplomacyHandler.class);
		DiplomacyHandler targetHandler = mock(DiplomacyHandler.class);
		when(origin.getId()).thenReturn("origin");
		when(target.getId()).thenReturn("target");
		when(origin.getPrestige()).thenReturn(100.0);
		when(target.getPrestige()).thenReturn(100.0);
		when(origin.getDiplomacyHandler()).thenReturn(originHandler);
		when(target.getDiplomacyHandler()).thenReturn(targetHandler);

		RelationType ally = relation("ally", 1.5, true, true);
		RelationType none = relation("none", 0.0, true, false);
		when(none.getLink()).thenReturn(none);
		when(ally.getLink()).thenReturn(ally);
		when(origin.getRelation("target")).thenReturn(new Relation(ally, null));
		when(target.getRelation("origin")).thenReturn(new Relation(ally, null));

		Pair pair = new Pair();
		pair.origin = origin;
		pair.target = target;
		pair.originHandler = originHandler;
		pair.targetHandler = targetHandler;
		pair.ally = ally;
		pair.none = none;
		return pair;
	}

	private static RelationType relation(String id, double cost, boolean settable, boolean willReset) {
		RelationType type = mock(RelationType.class);
		when(type.getId()).thenReturn(id);
		when(type.getBaseCost()).thenReturn(cost);
		when(type.isSettable()).thenReturn(settable);
		when(type.isVassalage()).thenReturn(false);
		when(type.getBlocScaling()).thenReturn(0.0);
		when(type.willReset()).thenReturn(willReset);
		when(type.isMutual()).thenReturn(willReset);
		when(type.hasLink()).thenReturn(false);
		when(type.isOverlord()).thenReturn(false);
		return type;
	}

	private static final class Pair {
		private Faction origin;
		private Faction target;
		private DiplomacyHandler originHandler;
		private DiplomacyHandler targetHandler;
		private RelationType ally;
		private RelationType none;
	}
}
