package me.Plugins.SimpleFactions.War.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

class BackerParticipationTest {

	@Test
	void sideAndWarTreatBackerAsParticipating() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		Faction backer = mock(Faction.class);
		when(attacker.getId()).thenReturn("rebels");
		when(defender.getId()).thenReturn("host");
		when(backer.getId()).thenReturn("brume");

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			relations.when(() -> RelationManager.getSubjects(attacker)).thenReturn(List.of());
			relations.when(() -> RelationManager.getSubjects(defender)).thenReturn(List.of());
			relations.when(() -> RelationManager.getAllies(attacker)).thenReturn(List.of());
			relations.when(() -> RelationManager.getAllies(defender)).thenReturn(List.of());

			Side attackers = new Side(attacker);
			Side defenders = new Side(defender);
			attackers.getMainParticipants().get(0).addBacker(backer);

			assertTrue(attackers.isParticipating(backer));

			War war = new War(
					1,
					attackers,
					defenders,
					WarGoalType.OVERTHROW,
					WarType.OVERTHROW,
					null,
					null,
					null);
			assertEquals(attackers, war.getSide(backer));
			assertTrue(BattleSideMembers.collectParticipatingFactions(attackers).contains(backer));
		}
	}

	@Test
	void getAllParticipatingFactionsIncludesBackers() {
		Faction leader = mock(Faction.class);
		when(leader.getId()).thenReturn("rebels");
		Faction backer = mock(Faction.class);
		when(backer.getId()).thenReturn("brume");

		Participant participant = new Participant(
				leader,
				List.of(),
				new HashMap<>(),
				List.of(backer),
				new HashMap<>(),
				true);

		assertTrue(participant.getAllParticipatingFactions().contains(backer));
		assertTrue(participant.getJoinedSecondaries().contains(backer));
	}
}
