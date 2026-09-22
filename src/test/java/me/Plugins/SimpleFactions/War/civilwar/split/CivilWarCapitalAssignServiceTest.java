package me.Plugins.SimpleFactions.War.civilwar.split;



import me.Plugins.SimpleFactions.War.civilwar.CivilWarStartService;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarCapitalAssignService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

class CivilWarCapitalAssignServiceTest {

	@Test
	void pickSeat_keepsPreferredCityOnDirectLand() {
		Faction rebels = mock(Faction.class);
		SettlementHandler handler = new SettlementHandler(rebels);
		when(rebels.getSettlementHandler()).thenReturn(handler);
		when(rebels.getGuildHandler()).thenReturn(mock(GuildHandler.class));
		when(rebels.getGuildHandler().getGuilds()).thenReturn(List.of());
		handler.found("Gaba Gaba", 694, 0, 0);

		int seat = CivilWarCapitalAssignService.pickSeat(rebels, List.of(694, 700), 694);

		assertEquals(694, seat);
	}

	@Test
	void pickSeat_ignoresCityOffDirectLand_foundsRebelCamp() {
		Faction rebels = mock(Faction.class);
		when(rebels.getId()).thenReturn("Gaba_Gaba");
		when(rebels.getRGB()).thenReturn("1,2,3");
		SettlementHandler handler = new SettlementHandler(rebels);
		when(rebels.getSettlementHandler()).thenReturn(handler);
		when(rebels.getGuildHandler()).thenReturn(mock(GuildHandler.class));
		when(rebels.getGuildHandler().getGuilds()).thenReturn(List.of());
		handler.found("Evil Town", 50, 0, 0);

		int seat = CivilWarCapitalAssignService.pickSeat(rebels, List.of(694), 50);

		assertEquals(694, seat);
		assertNotNull(handler.getByProvince(694));
		assertEquals("Rebel_Camp", handler.getByProvince(694).getId());
	}

	@Test
	void pickSeat_nearestDirectCityWhenPreferredMissing() {
		Faction rebels = mock(Faction.class);
		SettlementHandler handler = new SettlementHandler(rebels);
		when(rebels.getSettlementHandler()).thenReturn(handler);
		handler.found("Cape Wells", 695, 0, 0);
		handler.found("Lanbury", 705, 0, 0);

		int seat = CivilWarCapitalAssignService.pickSeat(rebels, List.of(695, 705), 694);

		assertEquals(695, seat);
	}

	@Test
	void snapshotGuildCapitals_recordsBeforeBaseConvert() {
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn("gaba");
		when(guild.hasCapital()).thenReturn(true);
		when(guild.getCapital()).thenReturn(694);

		Map<String, Integer> snapshot = CivilWarStartService.snapshotGuildCapitals(List.of(guild));

		assertEquals(694, snapshot.get("gaba"));
	}

	@Test
	void distance_fallsBackToProvinceIdDeltaWithoutPlugin() {
		assertEquals(10.0, CivilWarCapitalAssignService.distance(694, 704));
	}

	@Test
	void hostSettlementMissing_whenNoCityOnCapitalTile() {
		Faction host = mock(Faction.class);
		SettlementHandler handler = new SettlementHandler(host);
		when(host.getSettlementHandler()).thenReturn(handler);
		when(host.hasProvince(694)).thenReturn(true);

		assertEquals(true, CivilWarCapitalAssignService.hostSettlementMissing(host, 694));
	}
}
