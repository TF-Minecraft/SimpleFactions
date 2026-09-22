package me.Plugins.SimpleFactions.War.civilwar.split;


import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarLandSplitService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarLandSplitService.LandSplitPlan;

class CivilWarLandSplitServiceTest {

	@Test
	void twoProvinces_loyalistsKeepCapital() {
		Faction host = mock(Faction.class);
		when(host.getProvinces()).thenReturn(new ArrayList<>(List.of(10, 20)));
		when(host.getCapital()).thenReturn(10);
		when(host.getGuildHandler()).thenReturn(mock(GuildHandler.class));
		when(host.getGuildHandler().getGuilds()).thenReturn(List.of());

		LandSplitPlan plan = CivilWarLandSplitService.plan(host, List.of());

		assertEquals(List.of(20), plan.rebelProvinceIds());
		assertEquals(List.of(10), plan.loyalProvinceIds());
	}

	@Test
	void tie_goesToLoyalists() {
		Faction host = hostWithGuilds(List.of(1, 2, 3), 1);
		Guild rebel = guild("rebel");
		Guild loyal = guild("loyal");
		when(host.getGuildHandler().getGuilds()).thenReturn(List.of(rebel, loyal));

		LandSplitPlan plan = CivilWarLandSplitService.plan(host, List.of(rebel), (provinceId, guild) -> 1.0);

		assertNull(plan);
	}

	@Test
	void rebelPresenceWinsTiles() {
		Faction host = hostWithGuilds(List.of(1, 2, 3), 1);
		Guild rebel = guild("rebel");
		Guild loyal = guild("loyal");
		when(host.getGuildHandler().getGuilds()).thenReturn(List.of(rebel, loyal));

		LandSplitPlan plan = CivilWarLandSplitService.plan(host, List.of(rebel), (provinceId, guild) -> {
			if ("rebel".equals(guild.getId()) && provinceId == 2) {
				return 10;
			}
			if ("loyal".equals(guild.getId())) {
				return 1;
			}
			return 0;
		});

		assertEquals(List.of(2), plan.rebelProvinceIds());
		assertEquals(List.of(1, 3), plan.loyalProvinceIds());
	}

	@Test
	void oneProvince_returnsNull() {
		Faction host = mock(Faction.class);
		when(host.getProvinces()).thenReturn(new ArrayList<>(List.of(5)));
		when(host.getCapital()).thenReturn(5);
		GuildHandler handler = mock(GuildHandler.class);
		when(host.getGuildHandler()).thenReturn(handler);
		when(handler.getGuilds()).thenReturn(List.of());

		assertNull(CivilWarLandSplitService.plan(host, List.of()));
	}

	private static Faction hostWithGuilds(List<Integer> provinces, int capital) {
		Faction host = mock(Faction.class);
		when(host.getProvinces()).thenReturn(new ArrayList<>(provinces));
		when(host.getCapital()).thenReturn(capital);
		GuildHandler handler = mock(GuildHandler.class);
		when(host.getGuildHandler()).thenReturn(handler);
		return host;
	}

	private static Guild guild(String id) {
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn(id);
		return guild;
	}
}
