package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import net.tfminecraft.simplefactions.objects.Faction;

class GuildInviteTest {

	@Test
	void inviteMatchesAndConsumesExactKeys() throws Exception {
		Guild guild = mock(Guild.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		setField(guild, "invites", new ArrayList<String>());

		guild.invite("steve");
		assertTrue(guild.isInvited("steve"));
		assertFalse(guild.isInvited("Steve"));
		guild.invite("steve");
		assertEquals(1, guild.getInvites().size());
		assertFalse(guild.consumeInvite("STEVE"));
		assertTrue(guild.consumeInvite("steve"));
		assertFalse(guild.isInvited("steve"));
		assertTrue(guild.getInvites().isEmpty());
	}

	@Test
	void findReturnsOnlyAnExactKey() {
		ArrayList<String> names = new ArrayList<>();
		names.add("Steve");
		assertEquals("Steve", Guild.find(names, "Steve"));
		assertEquals(null, Guild.find(names, "steve"));
		assertEquals(null, Guild.find(names, "Alex"));
	}

	@Test
	void kickRemovesOnlyAnExactKey() throws Exception {
		Guild guild = mock(Guild.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		ArrayList<String> members = new ArrayList<>();
		members.add("Steve");
		setField(guild, "members", members);
		setField(guild, "host", mock(Faction.class));

		guild.kick("steve");
		assertEquals(1, members.size());
		guild.kick("Steve");
		assertTrue(members.isEmpty());
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field = Guild.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
