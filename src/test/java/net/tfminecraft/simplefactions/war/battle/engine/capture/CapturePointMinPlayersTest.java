package net.tfminecraft.simplefactions.war.battle.engine.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;

class CapturePointMinPlayersTest {
	private int previousCaptureMinPlayers;

	@BeforeEach
	void setUp() {
		previousCaptureMinPlayers = Cache.battleCaptureMinPlayers;
	}

	@AfterEach
	void tearDown() {
		Cache.battleCaptureMinPlayers = previousCaptureMinPlayers;
	}

	@Test
	void processCaptureTicks_ticksCaptureWhenAtMinPlayers() {
		withMockBossBar(() -> {
			Cache.battleCaptureMinPlayers = 1;
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "capture_min_test");
			BattleSide attacker = battle.getSideById("attacker");
			CapturePoint point = new CapturePoint("A", new Location(mock(World.class), 0, 64, 0), attacker, 0);
			setSideCounts(point, attacker, 1);

			point.processCaptureTicks();

			assertEquals(1, point.getCaptureProgress());
		});
	}

	@Test
	void processCaptureTicks_doesNotTickBelowMinPlayers() {
		withMockBossBar(() -> {
			Cache.battleCaptureMinPlayers = 2;
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "capture_min_test");
			BattleSide attacker = battle.getSideById("attacker");
			CapturePoint point = new CapturePoint("A", new Location(mock(World.class), 0, 64, 0), attacker, 0);
			setSideCounts(point, attacker, 1);

			point.processCaptureTicks();

			assertEquals(0, point.getCaptureProgress());
		});
	}

	@Test
	void processCaptureTicks_doesNotTickWithZeroPlayers() {
		withMockBossBar(() -> {
			Cache.battleCaptureMinPlayers = 1;
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "capture_min_test");
			BattleSide attacker = battle.getSideById("attacker");
			CapturePoint point = new CapturePoint("A", new Location(mock(World.class), 0, 64, 0), attacker, 0);
			setSideCounts(point, attacker, 0);

			point.processCaptureTicks();

			assertEquals(0, point.getCaptureProgress());
		});
	}

	private void setSideCounts(CapturePoint point, BattleSide side, int count) {
		try {
			Field playerSideField = CapturePoint.class.getDeclaredField("playerSide");
			playerSideField.setAccessible(true);
			@SuppressWarnings("unchecked")
			HashMap<BattleSide, Integer> playerSide = (HashMap<BattleSide, Integer>) playerSideField.get(point);
			playerSide.clear();
			if (count > 0) {
				playerSide.put(side, count);
			}
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
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
