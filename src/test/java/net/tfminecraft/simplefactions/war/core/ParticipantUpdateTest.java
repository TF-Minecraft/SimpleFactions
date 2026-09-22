package net.tfminecraft.simplefactions.war.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.War;

class ParticipantUpdateTest {

	@Test
	void update_removesSubjectWithChangedOverlord() {
		Faction leader = mock(Faction.class);
		when(leader.getId()).thenReturn("leader");
		Faction staleSubject = mock(Faction.class);

		Participant participant = new Participant(
				leader,
				List.of(staleSubject),
				new HashMap<>(),
				new HashMap<>(),
				false);
		War war = mock(War.class);
		when(war.isMainParticipant(staleSubject)).thenReturn(false);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			relations.when(() -> RelationManager.getOverlord(staleSubject)).thenReturn("other_lord");
			relations.when(() -> RelationManager.getSubjects(leader)).thenReturn(List.of());

			participant.update(war);
		}

		assertFalse(participant.getSubjects().contains(staleSubject));
	}

	@Test
	void update_addsNewSubjectFromRelations() {
		Faction leader = mock(Faction.class);
		when(leader.getId()).thenReturn("leader");
		when(leader.getRelations()).thenReturn(new HashMap<>());

		Faction newSubject = mock(Faction.class);
		Participant participant = new Participant(
				leader,
				new ArrayList<>(),
				new HashMap<>(),
				new HashMap<>(),
				false);
		War war = mock(War.class);
		when(war.isMainParticipant(newSubject)).thenReturn(false);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			relations.when(() -> RelationManager.getSubjects(leader)).thenReturn(List.of(newSubject));

			participant.update(war);
		}

		assertTrue(participant.getSubjects().contains(newSubject));
	}

	@Test
	void update_keepsBackerWithoutAllyRelationAndDropsFakeAlly() {
		Faction leader = mock(Faction.class);
		when(leader.getId()).thenReturn("leader");
		when(leader.getRelations()).thenReturn(new HashMap<>());

		Faction fakeAlly = mock(Faction.class);
		when(fakeAlly.getId()).thenReturn("fake_ally");
		RelationType notAlly = mock(RelationType.class);
		when(notAlly.getId()).thenReturn("neutral");
		Relation relation = mock(Relation.class);
		when(relation.getType()).thenReturn(notAlly);
		when(fakeAlly.getRelation("leader")).thenReturn(relation);

		Faction backer = mock(Faction.class);
		when(backer.getId()).thenReturn("brume");

		HashMap<Faction, Boolean> allies = new HashMap<>();
		allies.put(fakeAlly, true);

		Participant participant = new Participant(
				leader,
				new ArrayList<>(),
				allies,
				List.of(backer),
				new HashMap<>(),
				true);
		War war = mock(War.class);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			relations.when(() -> RelationManager.getSubjects(leader)).thenReturn(List.of());
			factions.when(() -> FactionManager.getByString(anyString())).thenReturn(null);

			participant.update(war);
		}

		assertFalse(participant.getAllies().containsKey(fakeAlly));
		assertTrue(participant.getBackers().contains(backer));
		assertTrue(participant.isJoinedSecondary(backer));
		assertFalse(participant.isJoinedSecondary(fakeAlly));
	}
}
