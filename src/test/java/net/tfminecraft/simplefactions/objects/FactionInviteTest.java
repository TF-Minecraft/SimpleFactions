package net.tfminecraft.simplefactions.objects;

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
	void inviteMatchesAndConsumesExactKeys() throws Exception {
		Faction faction = mock(Faction.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		setField(faction, "invited", new ArrayList<String>());

		faction.invite("steve");
		assertFalse(faction.isInvited("Steve"));
		assertTrue(faction.isInvited("steve"));
		faction.invite("steve");
		assertEquals(1, faction.getInvited().size());
		assertFalse(faction.consumeInvite("Steve"));
		assertTrue(faction.consumeInvite("steve"));
		assertFalse(faction.isInvited("steve"));
		assertTrue(faction.getInvited().isEmpty());
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field = Faction.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
