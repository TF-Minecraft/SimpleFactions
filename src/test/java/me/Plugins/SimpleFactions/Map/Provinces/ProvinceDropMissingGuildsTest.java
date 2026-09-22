package me.Plugins.SimpleFactions.Map.Provinces;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;

class ProvinceDropMissingGuildsTest {

	@Test
	void dropMissingGuilds_removesUnknownIdsAndKeepsLiveGuilds() {
		Province province = new Province(1, "PLAINS", 1);
		Guild live = mock(Guild.class);
		when(live.getId()).thenReturn("alive");
		province.setData("alive", new ProvinceDataEntry(live));
		province.setData("The_Village", new ProvinceDataEntry(mock(Guild.class)));

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getGuildByString("alive")).thenReturn(live);
			factions.when(() -> FactionManager.getGuildByString("The_Village")).thenReturn(null);

			province.dropMissingGuilds();
		}

		assertNotNull(province.getAllData().get("alive"));
		assertNull(province.getAllData().get("The_Village"));
	}
}
