package me.Plugins.SimpleFactions.War.civilwar.split;


import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarLandSplitService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

import org.bukkit.Bukkit;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class CivilWarLandSplitApplyTest {

	@Test
	void apply_transfersInstallsNotDissolved() {
		Faction host = mock(Faction.class);
		Faction rebels = mock(Faction.class);
		InstallationHandler hostHandler = new InstallationHandler(host);
		InstallationHandler rebelHandler = new InstallationHandler(rebels);
		when(host.getId()).thenReturn("host");
		when(rebels.getId()).thenReturn("rebels");
		when(host.getInstallationHandler()).thenReturn(hostHandler);
		when(rebels.getInstallationHandler()).thenReturn(rebelHandler);
		hostHandler.acceptTransferred(new Installation("port-1", "Harbour", InstallationKind.PORT, 20, 0, 0, 1L));

		CivilWarLandSplitService.LandSplitPlan plan =
				new CivilWarLandSplitService.LandSplitPlan(java.util.List.of(20), java.util.List.of(10));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
			CivilWarLandSplitService.apply(host, rebels, plan);
		}

		assertNull(hostHandler.getById("port-1"));
		assertNotNull(rebelHandler.getById("port-1"));
		assertEquals(20, rebelHandler.getById("port-1").getProvince());
	}

	@Test
	void apply_transfersSettlementOnRebelTile_keepsLoyalCity() {
		Faction host = mock(Faction.class);
		Faction rebels = mock(Faction.class);
		when(host.getId()).thenReturn("host");
		when(rebels.getId()).thenReturn("rebels");
		when(host.getLeader()).thenReturn("host_leader");
		when(rebels.getLeader()).thenReturn("rebel_leader");
		when(host.getGuildHandler()).thenReturn(mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class));
		when(rebels.getGuildHandler()).thenReturn(mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class));
		when(host.getGuildHandler().getGuilds()).thenReturn(java.util.List.of());
		when(rebels.getGuildHandler().getGuilds()).thenReturn(java.util.List.of());
		when(host.getCapital()).thenReturn(-1);
		when(rebels.getCapital()).thenReturn(-1);
		when(host.getInstallationHandler()).thenReturn(new InstallationHandler(host));
		when(rebels.getInstallationHandler()).thenReturn(new InstallationHandler(rebels));
		me.Plugins.SimpleFactions.settlement.handler.SettlementHandler hostCities =
				new me.Plugins.SimpleFactions.settlement.handler.SettlementHandler(host);
		me.Plugins.SimpleFactions.settlement.handler.SettlementHandler rebelCities =
				new me.Plugins.SimpleFactions.settlement.handler.SettlementHandler(rebels);
		when(host.getSettlementHandler()).thenReturn(hostCities);
		when(rebels.getSettlementHandler()).thenReturn(rebelCities);
		hostCities.found("Gaba Gaba", 20, 0, 0);
		hostCities.found("Lanbury", 10, 0, 0);

		CivilWarLandSplitService.LandSplitPlan plan =
				new CivilWarLandSplitService.LandSplitPlan(java.util.List.of(20), java.util.List.of(10));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
			CivilWarLandSplitService.apply(host, rebels, plan);
		}

		assertNull(hostCities.getByProvince(20));
		assertNotNull(rebelCities.getByProvince(20));
		assertEquals("Gaba_Gaba", rebelCities.getByProvince(20).getId());
		assertNotNull(hostCities.getByProvince(10));
	}
}
