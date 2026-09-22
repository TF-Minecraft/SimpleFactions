package me.Plugins.SimpleFactions.settlement.handler;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.bukkit.Bukkit;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.ProvinceHandler;
import me.Plugins.SimpleFactions.SimpleFactions;

class SettlementCapitalDepartureTest {

	@Test
	void onGuildDepartedCapital_dissolvesWhenNoGuildsRemain() {
		Faction faction = mock(Faction.class);
		SettlementHandler handler = new SettlementHandler(faction);
		when(faction.getGuildHandler()).thenReturn(mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class));
		when(faction.getCapital()).thenReturn(-1);
		when(faction.getGuildHandler().getGuilds()).thenReturn(List.of());
		when(faction.getLeader()).thenReturn("leader");

		CapitalResult founded = handler.found("Old City", 100, 0, 0);
		assertNotNull(founded.getSettlement());

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("leader")).thenReturn(null);
			handler.onGuildDepartedCapital(100);
		}

		assertNull(handler.getByProvince(100));
	}

	@Test
	void dissolve_clearsOtherFactionGuildCapitalPointer() {
		Faction host = mock(Faction.class);
		Faction rebels = mock(Faction.class);
		SettlementHandler handler = new SettlementHandler(host);
		when(host.getGuildHandler()).thenReturn(mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class));
		when(host.getCapital()).thenReturn(-1);
		when(host.getGuildHandler().getGuilds()).thenReturn(List.of());
		when(host.getLeader()).thenReturn("leader");

		Guild rebelGuild = mock(Guild.class);
		when(rebelGuild.isBase()).thenReturn(false);
		when(rebelGuild.getCapital()).thenReturn(100);
		when(rebels.getGuildHandler()).thenReturn(mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class));
		when(rebels.getGuildHandler().getGuilds()).thenReturn(List.of(rebelGuild));
		when(rebels.getCapital()).thenReturn(-1);

		handler.found("Gaba Gaba", 100, 0, 0);
		FactionManager.factions.add(rebels);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("leader")).thenReturn(null);
			handler.onGuildDepartedCapital(100);
		} finally {
			FactionManager.factions.remove(rebels);
		}

		verify(rebelGuild).setCapital(-1, false);
		assertNull(handler.getByProvince(100));
	}

	@Test
	void onGuildDepartedCapital_keepsSettlementWhenOtherGuildsRemain() {
		Faction faction = mock(Faction.class);
		SettlementHandler handler = new SettlementHandler(faction);
		when(faction.getGuildHandler()).thenReturn(mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class));
		when(faction.getCapital()).thenReturn(-1);

		Guild remaining = mock(Guild.class);
		when(remaining.getCapital()).thenReturn(100);
		when(faction.getGuildHandler().getGuilds()).thenReturn(List.of(remaining));

		handler.found("Shared City", 100, 0, 0);
		handler.onGuildDepartedCapital(100);

		assertNotNull(handler.getByProvince(100));
	}

	@Test
	void provinceHandler_setCapital_notifiesSettlementOnDeparture() {
		Faction faction = mock(Faction.class);
		SettlementHandler settlementHandler = mock(SettlementHandler.class);
		Guild mainGuild = mock(Guild.class);
		ProvinceHandler provinceHandler = new ProvinceHandler(faction);

		when(faction.getSettlementHandler()).thenReturn(settlementHandler);
		when(faction.getOrCreateMainGuild()).thenReturn(mainGuild);

		provinceHandler.addProvince(100);
		provinceHandler.addProvince(200);

		try (MockedStatic<SimpleFactions> plugin = mockStatic(SimpleFactions.class)) {
			SimpleFactions instance = mock(SimpleFactions.class);
			ProvinceManager provinceManager = mock(ProvinceManager.class);
			plugin.when(SimpleFactions::getInstance).thenReturn(instance);
			when(instance.getProvinceManager()).thenReturn(provinceManager);

			provinceHandler.setCapital(100, true);
			provinceHandler.setCapital(200, true);
		}

		verify(settlementHandler).onGuildDepartedCapital(100);
	}

	@Test
	void provinceHandler_setCapital_skipsNotificationWhenDisabled() {
		Faction faction = mock(Faction.class);
		SettlementHandler settlementHandler = mock(SettlementHandler.class);
		Guild mainGuild = mock(Guild.class);
		ProvinceHandler provinceHandler = new ProvinceHandler(faction);

		when(faction.getSettlementHandler()).thenReturn(settlementHandler);
		when(faction.getOrCreateMainGuild()).thenReturn(mainGuild);

		provinceHandler.addProvince(100);

		try (MockedStatic<SimpleFactions> plugin = mockStatic(SimpleFactions.class)) {
			SimpleFactions instance = mock(SimpleFactions.class);
			ProvinceManager provinceManager = mock(ProvinceManager.class);
			plugin.when(SimpleFactions::getInstance).thenReturn(instance);
			when(instance.getProvinceManager()).thenReturn(provinceManager);

			provinceHandler.setCapital(100, true);
			provinceHandler.setCapital(-1, true, false);
		}

		verify(settlementHandler, never()).onGuildDepartedCapital(eq(100));
	}

	@Test
	void factionCapitalMove_dissolvesSettlementWithOnlyFactionCapital() {
		Faction faction = mock(Faction.class);
		SettlementHandler handler = new SettlementHandler(faction);
		ProvinceHandler provinceHandler = new ProvinceHandler(faction);
		Guild mainGuild = mock(Guild.class);

		when(faction.getSettlementHandler()).thenReturn(handler);
		when(faction.getGuildHandler()).thenReturn(mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class));
		when(faction.getOrCreateMainGuild()).thenReturn(mainGuild);
		when(faction.getGuildHandler().getGuilds()).thenReturn(List.of(mainGuild));
		when(faction.getCapital()).thenAnswer(invocation -> provinceHandler.getCapital());
		when(mainGuild.getCapital()).thenAnswer(invocation -> faction.getCapital());
		when(mainGuild.isBase()).thenReturn(true);
		when(faction.getLeader()).thenReturn("leader");

		provinceHandler.addProvince(100);
		provinceHandler.addProvince(200);
		handler.found("Capital City", 100, 0, 0);

		try (MockedStatic<SimpleFactions> plugin = mockStatic(SimpleFactions.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			SimpleFactions instance = mock(SimpleFactions.class);
			ProvinceManager provinceManager = mock(ProvinceManager.class);
			plugin.when(SimpleFactions::getInstance).thenReturn(instance);
			when(instance.getProvinceManager()).thenReturn(provinceManager);
			bukkit.when(() -> Bukkit.getPlayerExact("leader")).thenReturn(null);

			provinceHandler.setCapital(100, true);
			provinceHandler.setCapital(200, true);
		}

		assertNull(handler.getByProvince(100));
		assertNull(handler.getByProvince(200));
	}
}