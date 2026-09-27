package net.tfminecraft.simplefactions.settlement.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.settlement.Settlement;

class SettlementPopulationTest {

	@Test
	void populationSizeCountsMembersNotGuilds() {
		Faction faction = mock(Faction.class);
		GuildHandler guilds = mock(GuildHandler.class);
		when(faction.getGuildHandler()).thenReturn(guilds);

		Guild home = guild(4, "a", "b", "c", "d", "e");
		Guild alsoHome = guild(4, "f", "g", "h", "i");
		Guild elsewhere = guild(9, "x", "y", "z", "w", "v", "u");
		when(guilds.getGuilds()).thenReturn(List.of(home, alsoHome, elsewhere));

		SettlementHandler handler = new SettlementHandler(faction);
		Settlement settlement = new Settlement("town", "Town", 4, 0, 0);

		assertEquals(9, handler.populationSize(settlement));
		assertEquals(2, handler.getPopulation(settlement).size());
	}

	private static Guild guild(int capital, String... members) {
		Guild guild = mock(Guild.class);
		when(guild.getCapital()).thenReturn(capital);
		when(guild.getMembers()).thenReturn(List.of(members));
		return guild;
	}
}
