package me.Plugins.SimpleFactions.War.battle.engine.win;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleLocation;
import me.Plugins.SimpleFactions.War.battle.template.ContestArea;

class SiegeContestServiceTest {
	@BeforeEach
	void setUp() {
		SiegeContestService.resetForTests();
		Cache.battleSiegeContestDurationSeconds = 180;
	}

	@Test
	void resolveControlState_attackerMajority() {
		assertEquals(
				SiegeContestService.ControlState.ATTACKER,
				SiegeContestService.resolveControlState(4, 2));
	}

	@Test
	void resolveControlState_defenderMajority() {
		assertEquals(
				SiegeContestService.ControlState.DEFENDER,
				SiegeContestService.resolveControlState(2, 4));
	}

	@Test
	void resolveControlState_contestedWhenThresholdNotMet() {
		assertEquals(
				SiegeContestService.ControlState.CONTESTED,
				SiegeContestService.resolveControlState(2, 2));
	}

	@Test
	void tickHoldSeconds_attackerTicksDown() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "test_siege");
			battle.setContestDurationSeconds(180);
			battle.setContestHoldRemainingSeconds(180);

			SiegeContestService.tickHoldSeconds(battle, SiegeContestService.ControlState.ATTACKER);
			assertEquals(179, battle.getContestHoldRemainingSeconds());
		}
	}

	@Test
	void tickHoldSeconds_defenderTicksUp() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "test_siege");
			battle.setContestDurationSeconds(180);
			battle.setContestHoldRemainingSeconds(100);

			SiegeContestService.tickHoldSeconds(battle, SiegeContestService.ControlState.DEFENDER);
			assertEquals(101, battle.getContestHoldRemainingSeconds());
		}
	}

	@Test
	void tickHoldSeconds_contestedPauses() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "test_siege");
			battle.setContestHoldRemainingSeconds(50);

			SiegeContestService.tickHoldSeconds(battle, SiegeContestService.ControlState.CONTESTED);
			assertEquals(50, battle.getContestHoldRemainingSeconds());
		}
	}

	@Test
	void tickHoldSeconds_clampsAtZeroAndMax() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "test_siege");
			battle.setContestDurationSeconds(180);
			battle.setContestHoldRemainingSeconds(0);
			SiegeContestService.tickHoldSeconds(battle, SiegeContestService.ControlState.ATTACKER);
			assertEquals(0, battle.getContestHoldRemainingSeconds());

			battle.setContestHoldRemainingSeconds(180);
			SiegeContestService.tickHoldSeconds(battle, SiegeContestService.ControlState.DEFENDER);
			assertEquals(180, battle.getContestHoldRemainingSeconds());
		}
	}

	@Test
	void countPresence_ignoresUnconfiguredArea() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "test_siege");
			battle.setContestArea(new ContestArea());
			int[] presence = SiegeContestService.countPresence(battle, battle.getContestArea());
			assertEquals(0, presence[0]);
			assertEquals(0, presence[1]);
		}
	}
}
