package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.diplomacy.Attitude;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.objects.Faction;

class RelationManagerDiplomaticCostTest {

	@Test
	void relationCost_usesDiminishingReturnsAbove100Prestige() {
		Faction origin = mock(Faction.class);
		Faction target = mock(Faction.class);
		when(target.getPrestige()).thenReturn(200.0);
		RelationType type = mock(RelationType.class);
		when(type.getBaseCost()).thenReturn(1.0);
		when(type.isSettable()).thenReturn(true);
		when(type.isVassalage()).thenReturn(false);
		assertEquals(Math.sqrt(200.0), RelationManager.getDiplomaticCost(origin, target, type), 1e-9);
	}

	@Test
	void attitudeCost_usesDiminishingReturnsAbove100Prestige() {
		Faction origin = mock(Faction.class);
		Faction target = mock(Faction.class);
		when(target.getPrestige()).thenReturn(200.0);
		Attitude attitude = mock(Attitude.class);
		when(attitude.getBaseCost()).thenReturn(0.25);
		assertEquals(0.25 * Math.sqrt(200.0), RelationManager.getDiplomaticCost(origin, target, attitude), 1e-9);
	}

	@Test
	void relationCost_preservesLinearScalingThrough100Prestige() {
		Faction origin = mock(Faction.class);
		Faction target = mock(Faction.class);
		when(target.getPrestige()).thenReturn(100.0);
		RelationType type = mock(RelationType.class);
		when(type.getBaseCost()).thenReturn(3.5);
		when(type.isSettable()).thenReturn(true);
		when(type.isVassalage()).thenReturn(false);
		assertEquals(35.0, RelationManager.getDiplomaticCost(origin, target, type), 1e-9);
	}

	@Test
	void zeroAttitudeCost_isZero() {
		Faction origin = mock(Faction.class);
		Faction target = mock(Faction.class);
		when(target.getPrestige()).thenReturn(200.0);
		Attitude attitude = mock(Attitude.class);
		when(attitude.getBaseCost()).thenReturn(0.0);
		assertEquals(0.0, RelationManager.getDiplomaticCost(origin, target, attitude), 1e-9);
	}
}
