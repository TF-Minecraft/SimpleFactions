package net.tfminecraft.simplefactions.war.resolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.government.movement.Action;

class CouncilPeaceQueriesTest {

	@Test
	void parseWarId_andIsWarEndAction() {
		assertEquals(12, CouncilPeaceQueries.parseWarId("12"));
		assertNull(CouncilPeaceQueries.parseWarId("nope"));
		assertNull(CouncilPeaceQueries.parseWarId(null));
		assertTrue(CouncilPeaceQueries.isWarEndAction(Action.WHITE_PEACE));
		assertTrue(CouncilPeaceQueries.isWarEndAction(Action.SURRENDER));
		assertFalse(CouncilPeaceQueries.isWarEndAction(Action.CHANGE_LEADER));
	}

	@Test
	void warsFor_onlyParticipatingActive() {
		Faction us = mock(Faction.class);
		War ours = mock(War.class);
		War other = mock(War.class);
		when(ours.isParticipating(us)).thenReturn(true);
		when(other.isParticipating(us)).thenReturn(false);
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(WarManager::getActive).thenReturn(List.of(ours, other));
			assertEquals(List.of(ours), CouncilPeaceQueries.warsFor(us));
			assertTrue(CouncilPeaceQueries.isParticipatingInAny(us));
		}
	}

	@Test
	void isValidTarget_requiresActiveParticipant() {
		Faction us = mock(Faction.class);
		War war = mock(War.class);
		when(war.isActive()).thenReturn(true);
		when(war.isParticipating(us)).thenReturn(true);
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(() -> WarManager.getById(4)).thenReturn(war);
			assertTrue(CouncilPeaceQueries.isValidTarget(us, "4"));
			assertFalse(CouncilPeaceQueries.isValidTarget(us, "9"));
		}
	}

	@Test
	void sideMain_usesCoalitionLeader() {
		Faction vassal = mock(Faction.class);
		Faction main = mock(Faction.class);
		War war = mock(War.class);
		Side attackers = mock(Side.class);
		when(war.getSide(vassal)).thenReturn(attackers);
		when(attackers.getLeader()).thenReturn(main);
		assertEquals(main, CouncilPeaceQueries.sideMain(war, vassal));
	}
}
