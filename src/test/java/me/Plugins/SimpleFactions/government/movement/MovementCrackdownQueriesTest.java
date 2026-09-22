package me.Plugins.SimpleFactions.government.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.LawHandler;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;

class MovementCrackdownQueriesTest {

	@ParameterizedTest
	@CsvSource({
			"none, GATHERING, false",
			"none, PRESSURING, false",
			"none, AGITATED, true",
			"none, REBELLIOUS, true",
			"right_to_assembly, GATHERING, false",
			"right_to_assembly, PRESSURING, false",
			"right_to_assembly, AGITATED, false",
			"right_to_assembly, REBELLIOUS, true",
			"state_crackdowns, GATHERING, true",
			"state_crackdowns, PRESSURING, true",
			"state_crackdowns, AGITATED, true",
			"state_crackdowns, REBELLIOUS, true",
			"unknown, GATHERING, false",
			"unknown, AGITATED, true",
	})
	void phaseAllowed_matchesLawTable(String lawId, Phase phase, boolean expected) {
		assertEquals(expected, MovementCrackdownQueries.phaseAllowed(lawId, phase));
	}

	@Test
	void phaseAllowed_nullPhaseIsFalse() {
		assertFalse(MovementCrackdownQueries.phaseAllowed("state_crackdowns", null));
	}

	@Test
	void assemblyLawId_defaultsToNone() {
		assertEquals("none", MovementCrackdownQueries.assemblyLawId(null));

		Faction host = mock(Faction.class);
		when(host.getLawHandler()).thenReturn(null);
		assertEquals("none", MovementCrackdownQueries.assemblyLawId(host));

		LawHandler handler = mock(LawHandler.class);
		when(host.getLawHandler()).thenReturn(handler);
		when(handler.getGroup("assembly")).thenReturn(null);
		assertEquals("none", MovementCrackdownQueries.assemblyLawId(host));
	}

	@Test
	void canCrush_falseWhenNullOrFrozen() {
		assertFalse(MovementCrackdownQueries.canCrush(null, mock(Movement.class)));
		assertFalse(MovementCrackdownQueries.canCrush(mock(Faction.class), null));

		Faction host = factionWithLaw("state_crackdowns");
		Movement frozen = mock(Movement.class);
		when(frozen.getPhase()).thenReturn(Phase.GATHERING);
		when(frozen.isFrozen()).thenReturn(true);
		assertFalse(MovementCrackdownQueries.canCrush(host, frozen));

		Movement missingPhase = mock(Movement.class);
		when(missingPhase.getPhase()).thenReturn(null);
		when(missingPhase.isFrozen()).thenReturn(false);
		assertFalse(MovementCrackdownQueries.canCrush(host, missingPhase));
	}

	@Test
	void denyReason_frozenAssemblyAndAgitation() {
		Faction assembly = factionWithLaw("right_to_assembly");
		Movement frozen = movement(Phase.REBELLIOUS, true);
		assertEquals("The movement cannot be disbanded while frozen.", MovementCrackdownQueries.denyReason(assembly, frozen));

		assertEquals(
				"Your assembly law only allows this when the movement is rebellious.",
				MovementCrackdownQueries.denyReason(assembly, movement(Phase.AGITATED, false)));

		Faction none = factionWithLaw("none");
		assertEquals(
				"The movement is not agitated enough.",
				MovementCrackdownQueries.denyReason(none, movement(Phase.GATHERING, false)));
		assertNull(MovementCrackdownQueries.denyReason(none, movement(Phase.AGITATED, false)));
	}

	@Test
	void canCrush_usesHostAssemblyLawAndPhase() {
		Faction none = factionWithLaw("none");
		assertTrue(MovementCrackdownQueries.canCrush(none, movement(Phase.AGITATED, false)));
		assertFalse(MovementCrackdownQueries.canCrush(none, movement(Phase.GATHERING, false)));

		Faction assembly = factionWithLaw("right_to_assembly");
		assertTrue(MovementCrackdownQueries.canCrush(assembly, movement(Phase.REBELLIOUS, false)));
		assertFalse(MovementCrackdownQueries.canCrush(assembly, movement(Phase.AGITATED, false)));
	}

	private static Movement movement(Phase phase, boolean frozen) {
		Movement movement = mock(Movement.class);
		when(movement.getPhase()).thenReturn(phase);
		when(movement.isFrozen()).thenReturn(frozen);
		return movement;
	}

	private static Faction factionWithLaw(String lawId) {
		Faction host = mock(Faction.class);
		LawHandler handler = mock(LawHandler.class);
		LawGroup group = mock(LawGroup.class);
		Law law = mock(Law.class);
		when(host.getLawHandler()).thenReturn(handler);
		when(handler.getGroup("assembly")).thenReturn(group);
		when(group.getCurrent()).thenReturn(law);
		when(law.getId()).thenReturn(lawId);
		return host;
	}
}
