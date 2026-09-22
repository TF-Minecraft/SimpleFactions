package me.Plugins.SimpleFactions.War.battle.template;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class ContestAreaTest {
	@Test
	void contains_insideAxisAlignedBox() {
		World world = mock(World.class);
		when(world.getName()).thenReturn("world");
		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

			ContestArea area = new ContestArea(
					new BattleLocation("world", 10, 60, 10, 0, 0),
					new BattleLocation("world", 20, 70, 20, 0, 0));
			Location inside = new Location(world, 15, 65, 15);
			Location outside = new Location(world, 25, 65, 15);

			assertTrue(area.contains(inside));
			assertFalse(area.contains(outside));
		}
	}

	@Test
	void contains_normalizesInvertedCorners() {
		World world = mock(World.class);
		when(world.getName()).thenReturn("world");
		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

			ContestArea area = new ContestArea(
					new BattleLocation("world", 20, 70, 20, 0, 0),
					new BattleLocation("world", 10, 60, 10, 0, 0));
			assertTrue(area.contains(new Location(world, 15, 65, 15)));
		}
	}

	@Test
	void contains_rejectsDifferentWorld() {
		World worldA = mock(World.class);
		World worldB = mock(World.class);
		when(worldA.getName()).thenReturn("world-a");
		when(worldB.getName()).thenReturn("world-b");
		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getWorld("world-a")).thenReturn(worldA);

			ContestArea area = new ContestArea(
					new BattleLocation("world-a", 0, 0, 0, 0, 0),
					new BattleLocation("world-a", 10, 10, 10, 0, 0));
			assertFalse(area.contains(new Location(worldB, 5, 5, 5)));
		}
	}

	@Test
	void isConfigured_requiresBothCorners() {
		World world = mock(World.class);
		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

			ContestArea area = new ContestArea();
			assertFalse(area.isConfigured());
			area.setMin(new BattleLocation("world", 0, 0, 0, 0, 0));
			assertFalse(area.isConfigured());
			area.setMax(new BattleLocation("world", 10, 10, 10, 0, 0));
			assertTrue(area.isConfigured());
		}
	}
}
