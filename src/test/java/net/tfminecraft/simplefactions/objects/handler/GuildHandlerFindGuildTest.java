package net.tfminecraft.simplefactions.objects.handler;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

class GuildHandlerFindGuildTest {

	@Test
	void findGuildMatchesTheFirstListedIdIgnoringCase() {
		GuildHandler handler = new GuildHandler(mock(Faction.class));
		Guild merchants = guild("merchants");
		Guild smiths = guild("Smiths");
		handler.addGuild(merchants);
		handler.addGuild(smiths);
		Guild expected = null;
		for (Guild guild : handler.getGuilds()) {
			if (guild.getId().equalsIgnoreCase("SMITHS")) {
				expected = guild;
				break;
			}
		}
		assertSame(smiths, expected);
		assertSame(expected, handler.findGuild("SMITHS"));
		assertSame(merchants, handler.findGuild("merchants"));
		assertNull(handler.findGuild("bakers"));
		assertNull(handler.findGuild(null));
	}

	private static Guild guild(String id) {
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn(id);
		return guild;
	}
}
