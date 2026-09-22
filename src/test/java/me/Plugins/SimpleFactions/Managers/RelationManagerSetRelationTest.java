package me.Plugins.SimpleFactions.Managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Diplomacy.Attitude;
import me.Plugins.SimpleFactions.Diplomacy.DiplomacyHandler;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Diplomacy.Threshold;
import me.Plugins.SimpleFactions.Objects.Faction;

class RelationManagerSetRelationTest {

	@Test
	void unforced_belowThreshold_doesNotChangeType() {
		Fixture fx = fixture();
		when(fx.type.hasThreshold()).thenReturn(true);
		when(fx.type.getThreshold()).thenReturn(fx.threshold);
		when(fx.threshold.fulfilled(anyInt())).thenReturn(false);
		when(fx.threshold.getOpinion()).thenReturn(20);
		when(fx.threshold.getFormattedType()).thenReturn("higher than or equal to");
		when(fx.threshold.isMutual()).thenReturn(false);

		assertFalse(RelationManager.setRelation(null, fx.type, fx.target, fx.origin, false, false));
		verify(fx.origin, never()).setRelation(any(), any());
	}

	@Test
	void fiveArgWrapper_isUnforced() {
		Fixture fx = fixture();
		when(fx.type.hasThreshold()).thenReturn(true);
		when(fx.type.getThreshold()).thenReturn(fx.threshold);
		when(fx.threshold.fulfilled(anyInt())).thenReturn(false);
		when(fx.threshold.getOpinion()).thenReturn(20);
		when(fx.threshold.getFormattedType()).thenReturn("higher than or equal to");
		when(fx.threshold.isMutual()).thenReturn(false);

		RelationManager.setRelation(null, fx.type, fx.target, fx.origin, false);
		verify(fx.origin, never()).setRelation(any(), any());
	}

	@Test
	void forced_belowThreshold_changesType() {
		Fixture fx = fixture();
		when(fx.type.hasThreshold()).thenReturn(true);
		when(fx.type.getThreshold()).thenReturn(fx.threshold);
		when(fx.threshold.fulfilled(anyInt())).thenReturn(false);

		assertTrue(RelationManager.setRelationForced(fx.type, fx.target, fx.origin));
		assertOriginTypeSet(fx);
	}

	@Test
	void unforced_mutualCheck_doesNotChangeTypeWhenLeaderOffline() {
		Fixture fx = fixture();
		when(fx.type.isMutual()).thenReturn(true);
		Player sender = mock(Player.class);
		when(fx.target.getLeader()).thenReturn("TargetLead");

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<RequestManager> requests = mockStatic(RequestManager.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("TargetLead")).thenReturn(null);
			assertFalse(RelationManager.setRelation(sender, fx.type, fx.target, fx.origin, true, false));
			verify(fx.origin, never()).setRelation(any(), any());
			requests.verify(() -> RequestManager.addRequest(any(), any(), any()), never());
		}
	}

	@Test
	void forced_mutual_changesTypeWithoutRequest() {
		Fixture fx = fixture();
		when(fx.type.isMutual()).thenReturn(true);

		try (MockedStatic<RequestManager> requests = mockStatic(RequestManager.class)) {
			assertTrue(RelationManager.setRelationForced(fx.type, fx.target, fx.origin));
			assertOriginTypeSet(fx);
			requests.verify(() -> RequestManager.addRequest(any(), any(), any()), never());
		}
	}

	@Test
	void forced_atLimit_doesNotChangeType() {
		Fixture fx = fixture();
		when(fx.type.hasLimit()).thenReturn(true);
		when(fx.type.getLimit()).thenReturn(1);
		when(fx.type.getId()).thenReturn("subject");
		Relation existing = mock(Relation.class);
		RelationType existingType = mock(RelationType.class);
		when(existing.getType()).thenReturn(existingType);
		when(existingType.getId()).thenReturn("subject");
		HashMap<String, Relation> relations = new HashMap<>();
		relations.put("other", existing);
		when(fx.origin.getRelations()).thenReturn(relations);

		assertFalse(RelationManager.setRelationForced(fx.type, fx.target, fx.origin));
		verify(fx.origin, never()).setRelation(any(), any());
	}

	@Test
	void forced_vassalAlreadyHasOtherOverlord_doesNotChangeType() {
		Fixture fx = vassalTypeFixture();
		Relation overlordRel = mock(Relation.class);
		RelationType overlordType = mock(RelationType.class);
		when(overlordType.isOverlord()).thenReturn(true);
		when(overlordRel.getType()).thenReturn(overlordType);
		HashMap<String, Relation> targetRels = new HashMap<>();
		targetRels.put("other-liege", overlordRel);
		when(fx.target.getRelations()).thenReturn(targetRels);

		assertFalse(RelationManager.setRelationForced(fx.type, fx.target, fx.origin));
		verify(fx.origin, never()).setRelation(any(), any());
	}

	@Test
	void forced_vassalLoop_doesNotChangeType() {
		Fixture fx = vassalTypeFixture();
		HashMap<String, Relation> originOverlords = overlordMap("target");
		HashMap<String, Relation> targetOverlords = overlordMap("emperor");
		when(fx.origin.getRelations()).thenReturn(originOverlords);
		when(fx.target.getRelations()).thenReturn(targetOverlords);

		Faction emperor = mock(Faction.class);
		when(emperor.getId()).thenReturn("emperor");
		when(emperor.getRelations()).thenReturn(new HashMap<>());

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getByString("target")).thenReturn(fx.target);
			factions.when(() -> FactionManager.getByString("emperor")).thenReturn(emperor);
			factions.when(FactionManager::getMap).thenReturn(null);
			assertFalse(RelationManager.setRelationForced(fx.type, fx.target, fx.origin));
			verify(fx.origin, never()).setRelation(any(), any());
		}
	}

	@Test
	void nullArgs_returnFalse() {
		Fixture fx = fixture();
		assertFalse(RelationManager.setRelationForced(null, fx.target, fx.origin));
		assertFalse(RelationManager.setRelationForced(fx.type, null, fx.origin));
		assertFalse(RelationManager.setRelationForced(fx.type, fx.target, null));
	}

	@Test
	void transferSubject_missingOverlord_doesNotSetRelation() {
		Fixture fx = fixture();
		RelationManager.transferSubject(fx.target, fx.origin);
		verify(fx.origin, never()).setRelation(any(), any());
	}

	@Test
	void vassal_refusesWhenOriginCannotHaveVassals() {
		Fixture fx = vassalTypeFixture();
		when(fx.origin.canHaveVassals()).thenReturn(false);

		assertFalse(RelationManager.setRelation(null, fx.type, fx.target, fx.origin, false, false));
		verify(fx.origin, never()).setRelation(any(), any());
	}

	@Test
	void forced_vassal_refusesWhenOriginCannotHaveVassals() {
		Fixture fx = vassalTypeFixture();
		when(fx.origin.canHaveVassals()).thenReturn(false);

		assertFalse(RelationManager.setRelationForced(fx.type, fx.target, fx.origin));
		verify(fx.origin, never()).setRelation(any(), any());
	}

	@Test
	void nonVassal_ignoresCanHaveVassalsFalse() {
		Fixture fx = fixture();
		when(fx.origin.canHaveVassals()).thenReturn(false);

		assertTrue(RelationManager.setRelationForced(fx.type, fx.target, fx.origin));
		assertOriginTypeSet(fx);
	}

	@Test
	void transferSubject_cannotHaveVassals_doesNotSetRelation() {
		Fixture fx = fixture();
		when(fx.origin.canHaveVassals()).thenReturn(false);

		RelationManager.transferSubject(fx.target, fx.origin);
		verify(fx.origin, never()).setRelation(any(), any());
	}

	@Test
	void setTradeRelationForced_writesBothSidesWithoutThreshold() {
		Fixture fx = fixture();
		DiplomacyHandler originDip = mock(DiplomacyHandler.class);
		DiplomacyHandler targetDip = mock(DiplomacyHandler.class);
		when(fx.origin.getDiplomacyHandler()).thenReturn(originDip);
		when(fx.target.getDiplomacyHandler()).thenReturn(targetDip);
		when(fx.type.hasThreshold()).thenReturn(true);
		when(fx.type.isMutual()).thenReturn(true);
		RelationType link = mock(RelationType.class);
		when(fx.type.getLink()).thenReturn(link);

		RelationManager.setTradeRelationForced(fx.type, fx.target, fx.origin);

		verify(originDip).setTradeRelation(fx.target, fx.type);
		verify(targetDip).setTradeRelation(fx.origin, link);
	}

	private static void assertOriginTypeSet(Fixture fx) {
		ArgumentCaptor<Relation> captor = ArgumentCaptor.forClass(Relation.class);
		verify(fx.origin).setRelation(eq(fx.target), captor.capture());
		assertEquals(fx.type, captor.getValue().getType());
	}

	private static HashMap<String, Relation> overlordMap(String overlordId) {
		Relation overlordRel = mock(Relation.class);
		RelationType overlordType = mock(RelationType.class);
		when(overlordType.isOverlord()).thenReturn(true);
		when(overlordRel.getType()).thenReturn(overlordType);
		HashMap<String, Relation> map = new HashMap<>();
		map.put(overlordId, overlordRel);
		return map;
	}

	private static Fixture vassalTypeFixture() {
		Fixture fx = fixture();
		when(fx.origin.canHaveVassals()).thenReturn(true);
		when(fx.type.isVassalage()).thenReturn(true);
		return fx;
	}

	private static Fixture fixture() {
		Fixture fx = new Fixture();
		fx.origin = mock(Faction.class);
		fx.target = mock(Faction.class);
		when(fx.origin.getId()).thenReturn("origin");
		when(fx.target.getId()).thenReturn("target");
		when(fx.origin.getName()).thenReturn("Origin");
		when(fx.target.getName()).thenReturn("Target");
		when(fx.origin.getRelations()).thenReturn(new HashMap<>());
		when(fx.target.getRelations()).thenReturn(new HashMap<>());

		fx.currentType = mock(RelationType.class);
		when(fx.currentType.shouldUpdateMap()).thenReturn(false);
		when(fx.currentType.willReset()).thenReturn(false);
		when(fx.currentType.getId()).thenReturn("neutral");

		Attitude attitude = mock(Attitude.class);
		fx.originRelation = new Relation(fx.currentType, attitude, 0);
		fx.targetRelation = new Relation(fx.currentType, attitude, 0);
		when(fx.origin.getRelation("target")).thenReturn(fx.originRelation);
		when(fx.target.getRelation("origin")).thenReturn(fx.targetRelation);

		fx.type = mock(RelationType.class);
		when(fx.type.isVassalage()).thenReturn(false);
		when(fx.type.hasThreshold()).thenReturn(false);
		when(fx.type.isMutual()).thenReturn(false);
		when(fx.type.hasLimit()).thenReturn(false);
		when(fx.type.shouldUpdateMap()).thenReturn(false);
		when(fx.type.willReset()).thenReturn(false);
		when(fx.type.getId()).thenReturn("ally");
		when(fx.type.getName()).thenReturn("Ally");
		when(fx.type.getLink()).thenReturn(fx.type);

		fx.threshold = mock(Threshold.class);
		return fx;
	}

	private static final class Fixture {
		Faction origin;
		Faction target;
		RelationType type;
		RelationType currentType;
		Relation originRelation;
		Relation targetRelation;
		Threshold threshold;
	}
}
