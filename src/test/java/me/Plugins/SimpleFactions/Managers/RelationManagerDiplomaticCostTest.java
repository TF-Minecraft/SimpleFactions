package me.Plugins.SimpleFactions.Managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Diplomacy.Attitude;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Objects.Faction;

class RelationManagerDiplomaticCostTest {

	@Test
	void relationCost_scalesWithTargetPrestige() {
		Faction origin = mock(Faction.class);
		Faction target = mock(Faction.class);
		when(target.getPrestige()).thenReturn(200.0);
		RelationType type = mock(RelationType.class);
		when(type.getBaseCost()).thenReturn(1.0);
		when(type.isSettable()).thenReturn(true);
		when(type.isVassalage()).thenReturn(false);
		assertEquals(20.0, RelationManager.getDiplomaticCost(origin, target, type), 1e-9);
	}

	@Test
	void attitudeCost_scalesWithTargetPrestige() {
		Faction origin = mock(Faction.class);
		Faction target = mock(Faction.class);
		when(target.getPrestige()).thenReturn(200.0);
		Attitude attitude = mock(Attitude.class);
		when(attitude.getBaseCost()).thenReturn(0.25);
		assertEquals(5.0, RelationManager.getDiplomaticCost(origin, target, attitude), 1e-9);
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
