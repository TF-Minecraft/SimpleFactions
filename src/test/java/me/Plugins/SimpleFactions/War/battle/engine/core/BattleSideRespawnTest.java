package me.Plugins.SimpleFactions.War.battle.engine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.War.battle.enums.LifeType;

class BattleSideRespawnTest {
	@AfterEach
	void tearDown() {
		BattleRespawnRouting.resetForTests();
	}

	@Test
	void applyDeath_usesAllRespawnsBeforeJail() {
		withMockBossBar(() -> {
			BattleSide side = new BattleSide("defender", LifeType.COLLECTIVE, 4);

			assertFalse(side.applyDeathAndNeedsJailRespawn());
			assertEquals(3, side.getLives());
			assertFalse(side.applyDeathAndNeedsJailRespawn());
			assertEquals(2, side.getLives());
			assertFalse(side.applyDeathAndNeedsJailRespawn());
			assertEquals(1, side.getLives());
			assertFalse(side.applyDeathAndNeedsJailRespawn());
			assertEquals(0, side.getLives());
			assertTrue(side.applyDeathAndNeedsJailRespawn());
			assertEquals(0, side.getLives());
		});
	}

	@Test
	void applyDeath_oneRespawn_meansSingleSpawnReturnThenJail() {
		withMockBossBar(() -> {
			BattleSide side = new BattleSide("defender", LifeType.COLLECTIVE, 1);

			assertFalse(side.applyDeathAndNeedsJailRespawn());
			assertEquals(0, side.getLives());
			assertTrue(side.applyDeathAndNeedsJailRespawn());
		});
	}

	private void withMockBossBar(Runnable action) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);
			action.run();
		}
	}
}
