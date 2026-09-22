package net.tfminecraft.simplefactions.war.civilwar.split;


import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarTitleMove;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.tiers.Title;

class CivilWarTitleMoveTest {

	@Test
	void pick_skipsLoyalistPrimaryAndUnholdable() {
		Title county = mock(Title.class);
		when(county.getId()).thenReturn("county");
		Title duchy = mock(Title.class);
		when(duchy.getId()).thenReturn("duchy");
		Faction host = mock(Faction.class);
		Faction rebels = mock(Faction.class);
		when(host.hasTitle(county)).thenReturn(true);
		when(host.hasTitle(duchy)).thenReturn(true);
		when(host.getHighestTitle()).thenReturn(duchy);
		when(duchy.canBeHeld(rebels)).thenReturn(true);
		when(county.canBeHeld(rebels)).thenReturn(true);

		try (MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> parents = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleLoader.getByProvince(694)).thenReturn(county);
			parents.when(() -> TitleManager.getParent(county)).thenReturn(duchy);
			parents.when(() -> TitleManager.getParent(duchy)).thenReturn(null);

			assertEquals(county, CivilWarTitleMove.pick(host, rebels, 694));
		}
	}

	@Test
	void pick_returnsNullWhenOnlyPrimary() {
		Title county = mock(Title.class);
		Faction host = mock(Faction.class);
		Faction rebels = mock(Faction.class);
		when(host.hasTitle(county)).thenReturn(true);
		when(host.getHighestTitle()).thenReturn(county);
		when(county.canBeHeld(rebels)).thenReturn(true);

		try (MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> parents = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleLoader.getByProvince(1)).thenReturn(county);
			parents.when(() -> TitleManager.getParent(county)).thenReturn(null);

			assertNull(CivilWarTitleMove.pick(host, rebels, 1));
		}
	}

	@Test
	void transfer_stripsThenAdds() {
		Title title = mock(Title.class);
		when(title.getId()).thenReturn("county");
		Faction from = mock(Faction.class);
		Faction to = mock(Faction.class);
		when(from.getId()).thenReturn("host");
		when(to.getId()).thenReturn("rebels");

		CivilWarTitleMove.transfer(from, to, title);

		verify(from).stripTitle(title);
		verify(to).addTitle(title);
	}
}
