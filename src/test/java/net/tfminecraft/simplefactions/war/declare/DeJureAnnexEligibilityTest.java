package net.tfminecraft.simplefactions.war.declare;

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

import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.war.declare.DeJureAnnexEligibility.DeJureTitleOption;

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
	void options_blocksTitleThatIsAllSettlements() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 2);
		when(attacker.getPrestige()).thenReturn(100.0);
		Title title = mockTitle("town", 2);
		when(attacker.getTitles()).thenReturn(List.of(title));
		when(defender.getProvinces()).thenReturn(List.of(11));
		settle(defender, "Oakvale", 11);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		try (MockedStatic<TitleLoader> titleLoader = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleLoader.when(TitleLoader::getTitles).thenReturn(List.of(title));
			titleManager.when(() -> TitleManager.getOwner(title)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(11));

			List<DeJureTitleOption> options = DeJureAnnexEligibility.options(attacker, defender);
			assertEquals(1, options.size());
			assertFalse(options.get(0).eligible());
			assertEquals("§cNothing in this title can be taken: it is all settlements or the road to them.",
					options.get(0).blockReason());
			assertEquals(0, options.get(0).preview().taken());
		}
	}

	@Test
	void evaluate_annexesAroundTheHoldersSettlementAndPreviewsIt() {
		Faction attacker = mockFaction("attacker", 4);
		Faction defender = mockFaction("defender", 2);
		when(defender.getName()).thenReturn("Defender");
		when(attacker.getPrestige()).thenReturn(100.0);
		Title title = mockTitle("town", 2);
		when(attacker.getTitles()).thenReturn(List.of(title));
		when(defender.getProvinces()).thenReturn(List.of(11, 12));
		settle(defender, "Oakvale", 11);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getOwner(title)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getProvinces(title)).thenReturn(List.of(11, 12));

			DeJureTitleOption option = DeJureAnnexEligibility.evaluate(attacker, defender, title);
			assertTrue(option.eligible(), option.blockReason());
			assertEquals(new DeJureAnnexEligibility.DeJureAnnexPreview(
					1, 2, List.of(new DeJureAnnexEligibility.KeptSettlement("Defender", "Oakvale")), 0),
					option.preview());
			assertEquals(java.util.Set.of(12),
					DeJureAnnexEligibility.partition(attacker, defender, title).taken());
		}
	}

	private static void settle(Faction faction, String name, int province) {
		net.tfminecraft.simplefactions.settlement.Settlement settlement =
				mock(net.tfminecraft.simplefactions.settlement.Settlement.class);
		when(settlement.getCenterProvince()).thenReturn(province);
		when(settlement.getName()).thenReturn(name);
		when(faction.getSettlementHandler().getAll()).thenReturn(List.of(settlement));
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
				mock(net.tfminecraft.simplefactions.settlement.handler.SettlementHandler.class));
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
