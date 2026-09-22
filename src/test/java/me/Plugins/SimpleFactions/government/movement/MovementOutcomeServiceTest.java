package me.Plugins.SimpleFactions.government.movement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.StabilityModifier;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;
import me.Plugins.SimpleFactions.government.proposal.Proposal;

class MovementOutcomeServiceTest {

	@Test
	void appliesLeaderChangeBeforeLaw() {
		Proposal lawProposal = mock(Proposal.class);
		Proposal leaderProposal = mock(Proposal.class);
		Cause law = cause(Action.LAW_CHANGE, lawProposal);
		Cause leader = cause(Action.CHANGE_LEADER, leaderProposal);
		Fixture fx = fixture(List.of(law, leader), 40);

		MovementOutcomeService.apply(fx.movement, MovementOutcomeSource.ACCEPTED);

		InOrder order = inOrder(fx.government, leaderProposal, lawProposal);
		order.verify(fx.government).addStabilityModifier(any(StabilityModifier.class));
		order.verify(leaderProposal).apply(leader);
		order.verify(lawProposal).apply(law);
		order.verify(fx.government).endMovement(fx.movement);
	}

	@Test
	void accepted_addsCavedToMovementThenAppliesThenEnds() {
		Proposal proposal = mock(Proposal.class);
		Cause cause = cause(Action.LAW_CHANGE, proposal);
		Fixture fx = fixture(List.of(cause), 55);

		MovementOutcomeService.apply(fx.movement, MovementOutcomeSource.ACCEPTED);

		ArgumentCaptor<StabilityModifier> captor = ArgumentCaptor.forClass(StabilityModifier.class);
		verify(fx.government).addStabilityModifier(captor.capture());
		assertEquals("Caved to Movement", captor.getValue().getName());
		assertEquals(55.0, captor.getValue().getModifier());
		assertEquals(1.0, captor.getValue().getDecay());
		verify(proposal).apply(cause);
		verify(fx.government).endMovement(fx.movement);
	}

	@Test
	void war_withLeaderChange_addsCoupNotCivilWar() {
		Proposal leaderProposal = mock(Proposal.class);
		Proposal lawProposal = mock(Proposal.class);
		Cause leader = cause(Action.CHANGE_LEADER, leaderProposal);
		Cause law = cause(Action.LAW_CHANGE, lawProposal);
		Fixture fx = fixture(List.of(law, leader), 40);

		MovementOutcomeService.apply(fx.movement, MovementOutcomeSource.WAR);

		ArgumentCaptor<StabilityModifier> captor = ArgumentCaptor.forClass(StabilityModifier.class);
		verify(fx.government).addStabilityModifier(captor.capture());
		assertEquals("Coup", captor.getValue().getName());
		assertEquals(-75.0, captor.getValue().getModifier());
		assertEquals(1.0, captor.getValue().getDecay());
		verify(leaderProposal).apply(leader);
		verify(lawProposal).apply(law);
	}

	@Test
	void war_withOnlyLaw_addsCivilWar() {
		Proposal proposal = mock(Proposal.class);
		when(proposal.isLawProposal()).thenReturn(true);
		Cause cause = cause(Action.LAW_CHANGE, proposal);
		Fixture fx = fixture(List.of(cause), 40);

		MovementOutcomeService.apply(fx.movement, MovementOutcomeSource.WAR);

		ArgumentCaptor<StabilityModifier> captor = ArgumentCaptor.forClass(StabilityModifier.class);
		verify(fx.government).addStabilityModifier(captor.capture());
		assertEquals("Civil War", captor.getValue().getName());
		assertEquals(-75.0, captor.getValue().getModifier());
		verify(proposal).apply(cause);
		verify(fx.government).endMovement(fx.movement);
	}

	@Test
	void war_withOnlyTax_addsCivilWar() {
		Proposal proposal = mock(Proposal.class);
		when(proposal.isTaxProposal()).thenReturn(true);
		Cause cause = cause(Action.TAX_CHANGE, proposal);
		Fixture fx = fixture(List.of(cause), 40);

		MovementOutcomeService.apply(fx.movement, MovementOutcomeSource.WAR);

		ArgumentCaptor<StabilityModifier> captor = ArgumentCaptor.forClass(StabilityModifier.class);
		verify(fx.government).addStabilityModifier(captor.capture());
		assertEquals("Civil War", captor.getValue().getName());
		assertEquals(-75.0, captor.getValue().getModifier());
	}

	@Test
	void applyPassesCauseNotNull() {
		Proposal proposal = mock(Proposal.class);
		Cause cause = cause(Action.INDEPENDENCE, proposal);
		Fixture fx = fixture(List.of(cause), 40);

		MovementOutcomeService.apply(fx.movement, MovementOutcomeSource.WAR);

		verify(proposal).apply(cause);
		verify(fx.government, never()).addStabilityModifier(any());
		verify(fx.government).endMovement(fx.movement);
	}

	@Test
	void nullMovement_doesNothing() {
		assertDoesNotThrow(() -> MovementOutcomeService.apply(null, MovementOutcomeSource.ACCEPTED));
	}

	@Test
	void orderedCauses_putsLeaderFirst() {
		Cause law = cause(Action.LAW_CHANGE, mock(Proposal.class));
		Cause leader = cause(Action.CHANGE_LEADER, mock(Proposal.class));
		Movement movement = mock(Movement.class);
		when(movement.getCauses()).thenReturn(List.of(law, leader));
		assertEquals(List.of(leader, law), MovementOutcomeService.orderedCauses(movement));
	}

	@Test
	void stabilityFor_acceptedUsesMovementMagnitude() {
		Movement movement = mock(Movement.class);
		when(movement.getStabilityEffect()).thenReturn(62.5);
		StabilityModifier modifier =
				MovementOutcomeService.stabilityFor(movement, MovementOutcomeSource.ACCEPTED);
		assertEquals("Caved to Movement", modifier.getName());
		assertEquals(62.5, modifier.getModifier());
	}

	@Test
	void stabilityFor_warIndependenceOnly_isNull() {
		Cause cause = cause(Action.INDEPENDENCE, mock(Proposal.class));
		Movement movement = mock(Movement.class);
		when(movement.getCauses()).thenReturn(List.of(cause));
		assertNull(MovementOutcomeService.stabilityFor(movement, MovementOutcomeSource.WAR));
	}

	private static Fixture fixture(List<Cause> causes, double stabilityEffect) {
		Fixture fx = new Fixture();
		fx.movement = mock(Movement.class);
		fx.faction = mock(Faction.class);
		fx.government = mock(Government.class);
		when(fx.movement.getFaction()).thenReturn(fx.faction);
		when(fx.movement.getCauses()).thenReturn(causes);
		when(fx.movement.getStabilityEffect()).thenReturn(stabilityEffect);
		when(fx.faction.getGovernment()).thenReturn(fx.government);
		return fx;
	}

	private static Cause cause(Action action, Proposal proposal) {
		Cause cause = mock(Cause.class);
		when(cause.getAction()).thenReturn(action);
		when(cause.getProposal()).thenReturn(proposal);
		return cause;
	}

	private static final class Fixture {
		Movement movement;
		Faction faction;
		Government government;
	}
}
