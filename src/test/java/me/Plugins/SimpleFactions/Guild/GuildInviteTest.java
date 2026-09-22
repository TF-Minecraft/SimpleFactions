package me.Plugins.SimpleFactions.Guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.Objects.Faction;

class GuildInviteTest {

	@Test
	void inviteMatchesAndConsumesIgnoreCase() throws Exception {
		Guild guild = mock(Guild.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		setField(guild, "invites", new ArrayList<String>());

		guild.invite("steve");
		assertTrue(guild.isInvited("Steve"));
		guild.invite("STEVE");
		assertEquals(1, guild.getInvites().size());
		assertTrue(guild.consumeInvite("Steve"));
		assertFalse(guild.isInvited("steve"));
		assertTrue(guild.getInvites().isEmpty());
	}

	@Test
	void findIgnoreCaseReturnsStoredSpelling() {
		ArrayList<String> names = new ArrayList<>();
		names.add("Steve");
		assertEquals("Steve", Guild.findIgnoreCase(names, "steve"));
		assertEquals(null, Guild.findIgnoreCase(names, "Alex"));
	}

	@Test
	void kickRemovesIgnoreCase() throws Exception {
		Guild guild = mock(Guild.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		ArrayList<String> members = new ArrayList<>();
		members.add("Steve");
		setField(guild, "members", members);
		setField(guild, "host", mock(Faction.class));

		guild.kick("steve");
		assertTrue(members.isEmpty());
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field = Guild.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
