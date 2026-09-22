package me.Plugins.SimpleFactions.Army;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Objects.Faction;

class MilitaryQueueCancelTest {

	@Test
	void cancelQueueRemovesValidEntry() {
		Military military = new Military(mock(Faction.class));
		Regiment regiment = mock(Regiment.class);
		when(regiment.getExpansionTime()).thenReturn(1);

		assertTrue(military.enqueue(regiment));
		assertTrue(military.cancelQueue(0));
		assertTrue(military.getQueue().isEmpty());
	}

	@Test
	void cancelQueueRejectsInvalidIndex() {
		Military military = new Military(mock(Faction.class));
		assertFalse(military.cancelQueue(0));
		assertFalse(military.cancelQueue(-1));
	}
}
