package me.Plugins.SimpleFactions.War.declare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Tiers.Tier;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.declare.DeJureAnnexEligibility.DeJureTitleOption;

class DeJureAnnexEligibilityTest {
	private final List<Faction> savedFactions = new ArrayList<>();

	@BeforeEach
	void setUp() {
		savedFactions.addAll(FactionManager.factions);
		FactionManager.factions.clear();
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
	}

	@Test
	void options_omitsTitlesWithNoIncomingLand() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 2);
		Title title = mockTitle("empty", 2);
		when(attacker.getTitles()).thenReturn(List.of(title));
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		try (MockedStatic<TitleLoader> titleLoader = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleLoader.when(TitleLoader::getTitles).thenReturn(List.of(title));
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(10));

			assertTrue(DeJureAnnexEligibility.options(attacker, defender).isEmpty());
		}
	}

	@Test
	void incomingProvinces_matchesIncomingLandCount() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 2);
		when(defender.getProvinces()).thenReturn(List.of(11, 12));
		Title title = mockTitle("town", 2);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(11, 99));
			List<Integer> incoming = DeJureAnnexEligibility.incomingProvinces(attacker, defender, title);
			assertEquals(List.of(11), incoming);
			assertEquals(incoming.size(), DeJureAnnexEligibility.incomingLandCount(attacker, defender, title));
		}
	}

	@Test
	void options_includesBlockedSettlementRow() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 2);
		when(attacker.getPrestige()).thenReturn(100.0);
		Title title = mockTitle("town", 2);
		when(attacker.getTitles()).thenReturn(List.of(title));
		when(defender.getProvinces()).thenReturn(List.of(11));
		Faction settler = mockFaction("settler", 2);
		me.Plugins.SimpleFactions.settlement.Settlement settlement =
				mock(me.Plugins.SimpleFactions.settlement.Settlement.class);
		when(settlement.getCenterProvince()).thenReturn(11);
		when(settler.getSettlementHandler().getAll()).thenReturn(List.of(settlement));
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
		FactionManager.factions.add(settler);

		try (MockedStatic<TitleLoader> titleLoader = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleLoader.when(TitleLoader::getTitles).thenReturn(List.of(title));
			titleManager.when(() -> TitleManager.getOwner(title)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(11));

			List<DeJureTitleOption> options = DeJureAnnexEligibility.options(attacker, defender);
			assertEquals(1, options.size());
			assertFalse(options.get(0).eligible());
			assertTrue(options.get(0).blockReason().contains("settlements"));
		}
	}

	private static Faction mockFaction(String id, int tierLevel) {
		Faction faction = mock(Faction.class);
		Tier tier = mock(Tier.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getTier()).thenReturn(tier);
		when(tier.getTier()).thenReturn(tierLevel);
		when(faction.getRelations()).thenReturn(new HashMap<>());
		when(faction.getCapital()).thenReturn(0);
		when(faction.getSettlementHandler()).thenReturn(
				mock(me.Plugins.SimpleFactions.settlement.handler.SettlementHandler.class));
		when(faction.getSettlementHandler().getAll()).thenReturn(List.of());
		when(faction.getTitles()).thenReturn(List.of());
		when(faction.getProvinces()).thenReturn(List.of());
		when(faction.getPrestige()).thenReturn(0.0);
		return faction;
	}

	private static Title mockTitle(String id, int tierLevel) {
		Title title = mock(Title.class);
		Tier titleTier = mock(Tier.class);
		when(title.getId()).thenReturn(id);
		when(title.getName()).thenReturn(id);
		when(title.getTier()).thenReturn(titleTier);
		when(titleTier.getTier()).thenReturn(tierLevel);
		when(titleTier.getName()).thenReturn("County");
		return title;
	}
}
