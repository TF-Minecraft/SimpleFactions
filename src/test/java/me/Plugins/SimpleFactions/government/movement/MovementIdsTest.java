package me.Plugins.SimpleFactions.government.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Database.MovementData;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.government.Government;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MovementIdsTest {

	@Test
	void slug_lowercasesFounder() {
		assertEquals("alice_movement", MovementIds.slug("Alice"));
	}

	@Test
	void allocate_usesBaseWhenFree() {
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getMovementById("alice_movement")).thenReturn(null);
			assertEquals("alice_movement", MovementIds.allocate("Alice"));
		}
	}

	@Test
	void allocate_suffixesWhenTaken() {
		Movement existing = mock(Movement.class);
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getMovementById("alice_movement")).thenReturn(existing);
			factions.when(() -> FactionManager.getMovementById("alice_movement_2")).thenReturn(null);
			assertEquals("alice_movement_2", MovementIds.allocate("Alice"));
		}
	}

	@Test
	void load_keepsExistingId() {
		Faction faction = mock(Faction.class);
		when(faction.getGovernment()).thenReturn(mock(Government.class));
		MovementData data = new MovementData();
		data.id = "legacy-uuid-id";
		data.leader = "Alice";
		data.phase = "GATHERING";
		Movement movement = new Movement(faction, data);
		assertEquals("legacy-uuid-id", movement.getId());
	}
}
