package net.tfminecraft.simplefactions.war.resolution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.proposal.Proposal;

class CouncilPeaceServiceTest {

	@Test
	void surrender_usesSideMainNotVassal() {
		Faction vassal = mock(Faction.class);
		Faction main = mock(Faction.class);
		when(main.getId()).thenReturn("atk");
		War war = mock(War.class);
		Side attackers = mock(Side.class);
		when(war.isActive()).thenReturn(true);
		when(war.isParticipating(vassal)).thenReturn(true);
		when(war.getSide(vassal)).thenReturn(attackers);
		when(attackers.getLeader()).thenReturn(main);
		Proposal proposal = proposal(Action.SURRENDER, "7");
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<WarResolutionService> resolution = mockStatic(WarResolutionService.class)) {
			wars.when(() -> WarManager.getById(7)).thenReturn(war);
			CouncilPeaceService.apply(vassal, proposal);
			resolution.verify(() -> WarResolutionService.surrender(war, main));
		}
	}

	@Test
	void whitePeace_setsForcedAndDoesNotEndUnlessBoth() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		War war = new War(7, attacker, defender);
		Proposal proposal = proposal(Action.WHITE_PEACE, "7");
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<WarResolutionService> resolution = mockStatic(WarResolutionService.class)) {
			wars.when(() -> WarManager.getById(7)).thenReturn(war);
			CouncilPeaceService.apply(attacker, proposal);
			assertTrue(war.isForcedWhitePeaceByAttacker());
			assertTrue(war.isWhitePeaceProposedByAttacker());
			assertFalse(war.isForcedWhitePeaceByDefender());
			resolution.verify(() -> WarResolutionService.endWhitePeace(any()), never());
		}
	}

	@Test
	void whitePeace_bothSidesEndWar() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		War war = new War(7, attacker, defender);
		war.setWhitePeaceProposedByDefender(true);
		Proposal proposal = proposal(Action.WHITE_PEACE, "7");
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<WarResolutionService> resolution = mockStatic(WarResolutionService.class)) {
			wars.when(() -> WarManager.getById(7)).thenReturn(war);
			CouncilPeaceService.apply(attacker, proposal);
			resolution.verify(() -> WarResolutionService.endWhitePeace(war));
		}
	}

	@Test
	void deadWarId_isNoOp() {
		Faction actor = mock(Faction.class);
		Proposal proposal = proposal(Action.SURRENDER, "99");
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<WarResolutionService> resolution = mockStatic(WarResolutionService.class)) {
			wars.when(() -> WarManager.getById(99)).thenReturn(null);
			CouncilPeaceService.apply(actor, proposal);
			resolution.verify(() -> WarResolutionService.surrender(any(), any()), never());
		}
	}

	private static Proposal proposal(Action action, String warId) {
		Government government = mock(Government.class);
		Proposal proposal = new Proposal("Alice", government);
		proposal.setPoliticalActionProposal(new PoliticalAction(action));
		proposal.setTarget(warId);
		return proposal;
	}
}
