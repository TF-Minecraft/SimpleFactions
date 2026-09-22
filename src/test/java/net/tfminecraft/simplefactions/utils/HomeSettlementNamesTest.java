package net.tfminecraft.simplefactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.settlement.handler.SettlementHandler;

class HomeSettlementNamesTest {

	@Test
	void settlementNameWhenPresent() {
		Faction faction = mock(Faction.class);
		SettlementHandler handler = mock(SettlementHandler.class);
		Settlement settlement = mock(Settlement.class);
		when(faction.hasCapital()).thenReturn(true);
		when(faction.getCapital()).thenReturn(10);
		when(faction.getSettlementHandler()).thenReturn(handler);
		when(handler.getByProvince(10)).thenReturn(settlement);
		when(settlement.getName()).thenReturn("Lanbury");
		assertEquals("Lanbury", HomeSettlementNames.of(faction));
	}

	@Test
	void titleFallbackWhenUnsettled() {
		Faction faction = mock(Faction.class);
		SettlementHandler handler = mock(SettlementHandler.class);
		Title title = mock(Title.class);
		when(faction.hasCapital()).thenReturn(true);
		when(faction.getCapital()).thenReturn(4);
		when(faction.getSettlementHandler()).thenReturn(handler);
		when(handler.getByProvince(4)).thenReturn(null);
		when(title.getName()).thenReturn("Oxshire");
		try (MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class)) {
			titles.when(() -> TitleLoader.getByProvince(4)).thenReturn(title);
			assertEquals("Oxshire", HomeSettlementNames.of(faction));
		}
	}

	@Test
	void noneWhenNoCapital() {
		Faction faction = mock(Faction.class);
		when(faction.hasCapital()).thenReturn(false);
		assertEquals("None", HomeSettlementNames.of(faction));
		assertEquals("None", HomeSettlementNames.of((Guild) null));
	}

	@Test
	void provinceFallbackWhenNoSettlementOrTitle() {
		Faction faction = mock(Faction.class);
		SettlementHandler handler = mock(SettlementHandler.class);
		when(faction.hasCapital()).thenReturn(true);
		when(faction.getCapital()).thenReturn(12);
		when(faction.getSettlementHandler()).thenReturn(handler);
		when(handler.getByProvince(12)).thenReturn(null);
		try (MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class)) {
			titles.when(() -> TitleLoader.getByProvince(12)).thenReturn(null);
			assertEquals("Province 12", HomeSettlementNames.of(faction));
		}
	}
}
