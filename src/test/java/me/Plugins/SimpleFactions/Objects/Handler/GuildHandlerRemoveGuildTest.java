package me.Plugins.SimpleFactions.Objects.Handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

class GuildHandlerRemoveGuildTest {

	@Test
	void removeGuild_skipsSettlementDissolveWhenTold() {
		Faction faction = mock(Faction.class);
		SettlementHandler settlements = mock(SettlementHandler.class);
		ProvinceHandler provinces = mock(ProvinceHandler.class);
		when(faction.getSettlementHandler()).thenReturn(settlements);
		when(faction.getProvinceHandler()).thenReturn(provinces);
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn("gaba");
		when(guild.isBase()).thenReturn(false);
		when(guild.hasCapital()).thenReturn(true);
		when(guild.getCapital()).thenReturn(694);

		GuildHandler handler = new GuildHandler(faction);
		handler.addGuild(guild);
		handler.removeGuild("gaba", false);

		verify(settlements, never()).onGuildDepartedCapital(694);
		verify(provinces).revalidateClaims();
	}

	@Test
	void removeGuild_skipsRevalidateWhenTold() {
		Faction faction = mock(Faction.class);
		SettlementHandler settlements = mock(SettlementHandler.class);
		ProvinceHandler provinces = mock(ProvinceHandler.class);
		when(faction.getSettlementHandler()).thenReturn(settlements);
		when(faction.getProvinceHandler()).thenReturn(provinces);
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn("gaba");
		when(guild.isBase()).thenReturn(false);
		when(guild.hasCapital()).thenReturn(true);
		when(guild.getCapital()).thenReturn(694);

		GuildHandler handler = new GuildHandler(faction);
		handler.addGuild(guild);
		handler.removeGuild("gaba", false, false);

		verify(settlements, never()).onGuildDepartedCapital(694);
		verify(provinces, never()).revalidateClaims();
	}

	@Test
	void removeGuild_dissolvesEmptySettlementByDefault() {
		Faction faction = mock(Faction.class);
		SettlementHandler settlements = mock(SettlementHandler.class);
		ProvinceHandler provinces = mock(ProvinceHandler.class);
		when(faction.getSettlementHandler()).thenReturn(settlements);
		when(faction.getProvinceHandler()).thenReturn(provinces);
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn("gaba");
		when(guild.isBase()).thenReturn(false);
		when(guild.hasCapital()).thenReturn(true);
		when(guild.getCapital()).thenReturn(694);

		GuildHandler handler = new GuildHandler(faction);
		handler.addGuild(guild);
		handler.removeGuild("gaba");

		verify(settlements).onGuildDepartedCapital(694);
	}

	@Test
	void removeGuild_clearsLiveAndSnapshotProvinceData() {
		Faction faction = mock(Faction.class);
		SettlementHandler settlements = mock(SettlementHandler.class);
		ProvinceHandler provinces = mock(ProvinceHandler.class);
		when(faction.getSettlementHandler()).thenReturn(settlements);
		when(faction.getProvinceHandler()).thenReturn(provinces);
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn("gaba");
		when(guild.isBase()).thenReturn(false);
		when(guild.hasCapital()).thenReturn(true);
		when(guild.getCapital()).thenReturn(694);

		ProvinceManager live = mock(ProvinceManager.class);
		ProvinceManager snapshot = mock(ProvinceManager.class);
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(live);
		when(plugin.getProvinceSnapshot()).thenReturn(snapshot);

		GuildHandler handler = new GuildHandler(faction);
		handler.addGuild(guild);
		try (MockedStatic<SimpleFactions> simpleFactions = mockStatic(SimpleFactions.class)) {
			simpleFactions.when(SimpleFactions::getInstance).thenReturn(plugin);
			handler.removeGuild("gaba");
		}

		verify(live).clearGuildData("gaba");
		verify(snapshot).clearGuildData("gaba");
		verify(settlements).onGuildDepartedCapital(694);
	}
}
