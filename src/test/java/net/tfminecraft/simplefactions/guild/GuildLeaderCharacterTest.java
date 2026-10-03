package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import net.tfminecraft.simplefactions.objects.Faction;

class GuildLeaderCharacterTest {

	@Test
	void aRealmsOwnGuildKeepsTheRealmsNameWhenItBecomesOrdinary() throws Exception {
		Guild guild = mock(Guild.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		Faction host = mock(Faction.class);
		when(host.getLeaderCharacter()).thenReturn("Grunk the Bold");
		when(host.getLeaderCharacterOf()).thenReturn("rushork");
		setField(guild, "host", host);
		setField(guild, "type", type(true));
		assertEquals("Grunk the Bold", guild.getLeaderCharacter());

		// What convert() does before the type changes.
		guild.keepHostLeaderCharacter();
		setField(guild, "type", type(false));

		assertEquals("Grunk the Bold", guild.getLeaderCharacter());
		assertEquals("rushork", guild.getLeaderCharacterOf());
	}

	private static GuildType type(boolean base) {
		GuildType type = mock(GuildType.class);
		when(type.isBase()).thenReturn(base);
		return type;
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field = Guild.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
