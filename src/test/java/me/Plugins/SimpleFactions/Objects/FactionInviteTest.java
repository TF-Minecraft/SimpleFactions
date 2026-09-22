package me.Plugins.SimpleFactions.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FactionInviteTest {

	@Test
	void inviteMatchesAndConsumesIgnoreCase() throws Exception {
		Faction faction = mock(Faction.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		setField(faction, "invited", new ArrayList<String>());

		faction.invite("steve");
		assertTrue(faction.isInvited("Steve"));
		faction.invite("STEVE");
		assertEquals(1, faction.getInvited().size());
		assertTrue(faction.consumeInvite("Steve"));
		assertFalse(faction.isInvited("steve"));
		assertTrue(faction.getInvited().isEmpty());
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field = Faction.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
