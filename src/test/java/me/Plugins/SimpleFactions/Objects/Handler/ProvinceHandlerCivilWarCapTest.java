package me.Plugins.SimpleFactions.Objects.Handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock;

class ProvinceHandlerCivilWarCapTest {

	@Test
	void provinceCap_skipsWhenLocked() {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn("host");
		try (MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class);
				MockedStatic<TitleManager> tm = mockStatic(TitleManager.class);
				MockedStatic<CivilWarBorderLock> lock = mockStatic(CivilWarBorderLock.class)) {
			titles.when(() -> TitleLoader.getByProvince(org.mockito.ArgumentMatchers.anyInt())).thenReturn(null);
			tm.when(() -> TitleManager.getByProvince(org.mockito.ArgumentMatchers.anyInt())).thenReturn(null);
			lock.when(() -> CivilWarBorderLock.isLocked(faction)).thenReturn(true);
			ProvinceHandler handler = new ProvinceHandler(faction, 1, List.of(1, 2));
			handler.provinceCap();
			assertEquals(2, handler.getProvinces().size());
		}
	}
}
