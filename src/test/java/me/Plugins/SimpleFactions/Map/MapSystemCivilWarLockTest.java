package me.Plugins.SimpleFactions.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.ProvinceHandler;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarCopy;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.Map.Provinces.Province;

class MapSystemCivilWarLockTest {

	@Test
	void claim_refusesWhenClaimerLocked() {
		Player player = mock(Player.class);
		Faction faction = mock(Faction.class);
		when(faction.ownsProvince(5)).thenReturn(false);

		try (MockedStatic<CivilWarBorderLock> lock = mockStatic(CivilWarBorderLock.class)) {
			lock.when(() -> CivilWarBorderLock.isLocked(faction)).thenReturn(true);
			new MapSystem().claim(player, faction, 5, false);
			verify(player).sendMessage(CivilWarCopy.CANNOT_CLAIM);
			verify(faction, never()).addProvince(anyInt());
		}
	}

	@Test
	void unclaim_refusesWhenPlayerAndLocked() {
		Player player = mock(Player.class);
		Faction faction = mock(Faction.class);
		when(faction.getProvinces()).thenReturn(List.of(8));
		when(faction.getCapital()).thenReturn(1);

		try (MockedStatic<CivilWarBorderLock> lock = mockStatic(CivilWarBorderLock.class)) {
			lock.when(() -> CivilWarBorderLock.isLocked(faction)).thenReturn(true);
			new MapSystem().unclaim(player, faction, 8);
			verify(player).sendMessage(CivilWarCopy.CANNOT_UNCLAIM);
			verify(faction, never()).removeProvince(anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
		}
	}

	@Test
	void unclaim_nullPlayer_skipsLock() {
		Faction faction = mock(Faction.class);
		when(faction.getProvinces()).thenReturn(List.of(8));
		when(faction.getCapital()).thenReturn(1);
		ProvinceHandler handler = mock(ProvinceHandler.class);
		when(faction.getProvinceHandler()).thenReturn(handler);

		try (MockedStatic<CivilWarBorderLock> lock = mockStatic(CivilWarBorderLock.class)) {
			lock.when(() -> CivilWarBorderLock.isLocked(faction)).thenReturn(true);
			new MapSystem().unclaim(null, faction, 8);
			verify(faction).removeProvince(8, true);
		}
	}

	@Test
	void steal_refusesWhenOwnerLocked() {
		Player player = mock(Player.class);
		Faction claimer = mock(Faction.class);
		Faction owner = mock(Faction.class);
		when(claimer.ownsProvince(5)).thenReturn(false);
		when(owner.getId()).thenReturn("host");
		ProvinceHandler handler = mock(ProvinceHandler.class);
		when(claimer.getProvinceHandler()).thenReturn(handler);
		when(handler.canClaim(5, false)).thenReturn(true);

		SimpleFactions plugin = mock(SimpleFactions.class);
		me.Plugins.SimpleFactions.Managers.ProvinceManager pm =
				mock(me.Plugins.SimpleFactions.Managers.ProvinceManager.class);
		when(plugin.getProvinceManager()).thenReturn(pm);
		Province land = new Province(5, Terrain.PLAINS.name(), 50);
		when(pm.get(5)).thenReturn(land);

		try (MockedStatic<CivilWarBorderLock> lock = mockStatic(CivilWarBorderLock.class);
				MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
			sf.when(SimpleFactions::getInstance).thenReturn(plugin);
			lock.when(() -> CivilWarBorderLock.isLocked(claimer)).thenReturn(false);
			lock.when(() -> CivilWarBorderLock.isLocked(owner)).thenReturn(true);
			factions.when(() -> FactionManager.getByProvince(5)).thenReturn(owner);
			titles.when(() -> TitleManager.overProvinceCap(owner)).thenReturn(true);

			new MapSystem().claim(player, claimer, 5, false);

			verify(player).sendMessage(CivilWarCopy.CANNOT_STEAL);
			verify(claimer, never()).addProvince(anyInt());
		}
	}
}
