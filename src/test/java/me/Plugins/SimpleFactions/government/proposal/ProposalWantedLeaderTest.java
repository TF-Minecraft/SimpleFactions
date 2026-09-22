package me.Plugins.SimpleFactions.government.proposal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.movement.Action;
import me.Plugins.SimpleFactions.government.movement.PoliticalAction;

class ProposalWantedLeaderTest {

	@Test
	void checkTarget_allowsWhenCanBecomeLeader() {
		Proposal proposal = changeLeaderProposal(true);
		proposal.setTarget("Bob");
		assertTrue(proposal.checkTarget());
	}

	@Test
	void checkTarget_rejectsWhenCannotBecomeLeader() {
		Proposal proposal = changeLeaderProposal(false);
		proposal.setTarget("Alice");
		assertFalse(proposal.checkTarget());
	}

	@Test
	void checkTarget_warEndRequiresParticipatingWar() {
		Government government = mock(Government.class);
		Faction faction = mock(Faction.class);
		when(government.getFaction()).thenReturn(faction);
		Proposal proposal = new Proposal("Alice", government);
		proposal.setPoliticalActionProposal(new PoliticalAction(Action.WHITE_PEACE));
		proposal.setTarget("4");
		War war = mock(War.class);
		when(war.isActive()).thenReturn(true);
		when(war.isParticipating(faction)).thenReturn(true);
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(() -> WarManager.getById(4)).thenReturn(war);
			assertTrue(proposal.checkTarget());
			assertTrue(proposal.needsTarget());
		}
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(() -> WarManager.getById(4)).thenReturn(null);
			assertFalse(proposal.checkTarget());
		}
	}

	private static Proposal changeLeaderProposal(boolean canLead) {
		Government government = mock(Government.class);
		Faction faction = mock(Faction.class);
		when(government.getFaction()).thenReturn(faction);
		when(faction.canBecomeLeader("Bob")).thenReturn(canLead);
		when(faction.canBecomeLeader("Alice")).thenReturn(canLead);
		Proposal proposal = new Proposal("Alice", government);
		proposal.setPoliticalActionProposal(new PoliticalAction(Action.CHANGE_LEADER));
		return proposal;
	}
}
