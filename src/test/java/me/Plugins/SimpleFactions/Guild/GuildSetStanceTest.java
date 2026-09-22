package me.Plugins.SimpleFactions.Guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.Stance;

class GuildSetStanceTest {

	@Test
	void setStance_changesGetStanceTowardOtherFaction() throws Exception {
		Faction host = mock(Faction.class);
		when(host.getId()).thenReturn("Invaders");
		Faction overlord = mock(Faction.class);
		when(overlord.getId()).thenReturn("Lantan");

		GuildType type = mock(GuildType.class);
		when(type.isBase()).thenReturn(true);

		Guild guild = mock(Guild.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		setField(guild, "host", host);
		setField(guild, "type", type);
		setField(guild, "stance", Stance.SUPPORT);

		assertEquals(Stance.SUPPORT, guild.getStance(overlord));
		guild.setStance(Stance.OPPOSE);
		assertEquals(Stance.OPPOSE, guild.getStance(overlord));
		assertEquals(Stance.SUPPORT, guild.getStance(host));
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field = Guild.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
