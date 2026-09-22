package me.Plugins.SimpleFactions.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Objects.Handler.ProvinceHandler;
import me.Plugins.SimpleFactions.Tiers.Title;

class FactionOwnsProvinceTest {

	@Test
	void titleListingProvinceDoesNotCountAsOwningLand() throws Exception {
		Faction faction = mock(Faction.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		ProvinceHandler handler = mock(ProvinceHandler.class);
		when(handler.hasProvince(10)).thenReturn(false);
		setField(faction, "provinceHandler", handler);
		setField(faction, "titles", new ArrayList<Title>());

		Title title = mock(Title.class);
		faction.getTitles().add(title);
		assertTrue(faction.hasTitle(title));

		try (MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class)) {
			titles.when(() -> TitleLoader.getByProvince(10)).thenReturn(title);
			assertFalse(faction.hasProvince(10));
			assertFalse(faction.ownsProvince(10));
		}
	}

	@Test
	void directProvinceHoldCountsAsOwning() throws Exception {
		Faction faction = mock(Faction.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		ProvinceHandler handler = mock(ProvinceHandler.class);
		when(handler.hasProvince(10)).thenReturn(true);
		setField(faction, "provinceHandler", handler);

		assertTrue(faction.hasProvince(10));
		assertTrue(faction.ownsProvince(10));
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field = Faction.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
