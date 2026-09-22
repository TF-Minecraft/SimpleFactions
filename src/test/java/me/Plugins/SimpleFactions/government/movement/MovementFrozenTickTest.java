package me.Plugins.SimpleFactions.government.movement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Database.MovementData;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.government.Government;

class MovementFrozenTickTest {

	@Test
	void frozenTick_doesNotEndMovement() {
		Faction faction = mock(Faction.class);
		Government government = mock(Government.class);
		when(faction.getGovernment()).thenReturn(government);
		MovementData data = new MovementData();
		data.frozen = true;
		data.leader = "Alice";
		data.phase = "GATHERING";
		Movement movement = new Movement(faction, data);

		movement.tick();

		verify(government, never()).endMovement(movement);
	}

	@Test
	void unfrozenEmptyCauses_endsMovement() {
		Faction faction = mock(Faction.class);
		Government government = mock(Government.class);
		when(faction.getGovernment()).thenReturn(government);
		when(faction.getMembers()).thenReturn(java.util.List.of());
		when(faction.getVassalMembers()).thenReturn(java.util.List.of());
		when(faction.getTotalTradePower()).thenReturn(0.0);
		MovementData data = new MovementData();
		data.frozen = false;
		data.leader = "Alice";
		data.phase = "GATHERING";
		Movement movement = new Movement(faction, data);

		movement.tick();

		verify(government).endMovement(movement);
	}
}