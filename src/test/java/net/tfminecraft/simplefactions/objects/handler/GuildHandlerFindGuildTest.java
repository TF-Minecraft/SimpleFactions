package net.tfminecraft.simplefactions.objects.handler;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

class GuildHandlerFindGuildTest {

	@Test
	void findGuildMatchesTheFirstListedIdIgnoringCaseOnTheServerThread() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
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
	}

	@Test
	void anAsyncLookupReadsACopySoAGuildAddedMeanwhileCannotBreakIt() {
		GuildHandler handler = new GuildHandler(mock(Faction.class));
		Guild merchants = guild("merchants");
		handler.addGuild(merchants);
		Guild smiths = guild("smiths");
		handler.addGuild(smiths);
		AtomicBoolean added = new AtomicBoolean();
		// The server thread founds a guild while the preview is still walking the realm.
		for (Guild guild : new Guild[] {merchants, smiths}) {
			String id = guild.getId();
			when(guild.getId()).thenAnswer(call -> {
				if (added.compareAndSet(false, true)) handler.addGuild(guild("bakers"));
				return id;
			});
		}
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
			assertNull(handler.findGuild("nobody"));
		}
		// No server at all (unit tests, shutdown) reads a copy too.
		assertSame(merchants, handler.findGuild("MERCHANTS"));
		assertSame(handler.getGuilds().stream().filter(g -> g.getId().equals("bakers")).findFirst().orElseThrow(),
				handler.findGuild("bakers"));
	}

	private static Guild guild(String id) {
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn(id);
		return guild;
	}
}
