package me.Plugins.SimpleFactions.government.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Objects.Faction;

class MovementJoinCopyTest {

	@Test
	void notVassalSupporter_playerUsesYour() {
		Faction joining = mock(Faction.class);
		Faction host = mock(Faction.class);
		when(joining.getId()).thenReturn("Invaders");
		when(host.getId()).thenReturn("Lantan");

		String player = MovementJoinCopy.notVassalSupporter(false, joining, host);
		assertTrue(player.contains("Your faction"));
		assertTrue(player.startsWith("§c"));
	}

	@Test
	void notVassalSupporter_staffNamesIds() {
		Faction joining = mock(Faction.class);
		Faction host = mock(Faction.class);
		when(joining.getId()).thenReturn("Invaders");
		when(host.getId()).thenReturn("Lantan");

		assertEquals(
				"§cInvaders is not a vassal of Lantan.",
				MovementJoinCopy.notVassalSupporter(true, joining, host));
	}

	@Test
	void backerSameRealm_staffNamesIds() {
		Faction joining = mock(Faction.class);
		Faction host = mock(Faction.class);
		when(joining.getId()).thenReturn("Invaders");
		when(host.getId()).thenReturn("Lantan");

		String staff = MovementJoinCopy.backerSameRealm(true, joining, host);
		assertTrue(staff.contains("Invaders"));
		assertTrue(staff.contains("Lantan"));
		assertTrue(staff.contains("same realm"));
	}
}
