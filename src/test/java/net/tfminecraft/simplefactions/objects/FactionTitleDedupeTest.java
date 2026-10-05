package net.tfminecraft.simplefactions.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.tiers.Title;

class FactionTitleDedupeTest {

	@Test
	void distinctTitlesKeepsFirstCopyOfEachId() {
		Title duchy = title("DUCHY_12");
		Title county = title("COUNTY_38");
		Title countyUpper = title("county_38");

		List<Title> result = Faction.distinctTitles(Arrays.asList(county, duchy, null, duchy, countyUpper));

		assertEquals(List.of(county, duchy), result);
	}

	@Test
	void resetTitlesDropsDuplicates() throws Exception {
		Faction faction = faction(new ArrayList<>());
		Title county = title("COUNTY_1");

		faction.resetTitles(new ArrayList<>(List.of(county, county, county)));

		assertEquals(List.of(county), faction.getTitles());
	}

	@Test
	void reloadTitlesRebindsStaleCopiesSoHasTitleMatchesAgain() throws Exception {
		Title stale = title("COUNTY_42");
		Title fresh = title("COUNTY_42");
		Faction faction = faction(new ArrayList<>(List.of(stale, stale)));
		List<Faction> saved = FactionManager.factions;
		FactionManager.factions = new ArrayList<>(List.of(faction));
		try (MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class)) {
			titles.when(() -> TitleLoader.getById("COUNTY_42")).thenReturn(fresh);
			assertFalse(faction.hasTitle(fresh));

			FactionManager.reloadTitles();

			assertEquals(1, faction.getTitles().size());
			assertSame(fresh, faction.getTitles().get(0));
			assertTrue(faction.hasTitle(fresh));
		} finally {
			FactionManager.factions = saved;
		}
	}

	private static Faction faction(List<Title> titles) throws Exception {
		Faction faction = mock(Faction.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		doNothing().when(faction).updatePrestige();
		Field field = Faction.class.getDeclaredField("titles");
		field.setAccessible(true);
		field.set(faction, titles);
		return faction;
	}

	private static Title title(String id) {
		Title title = mock(Title.class);
		when(title.getId()).thenReturn(id);
		return title;
	}
}
