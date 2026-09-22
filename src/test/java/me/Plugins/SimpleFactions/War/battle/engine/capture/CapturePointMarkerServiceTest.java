package me.Plugins.SimpleFactions.War.battle.engine.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.engine.capture.CapturePointMarkerService.MarkerColor;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;

class CapturePointMarkerServiceTest {
	private int previousCaptureMinPlayers;

	@BeforeEach
	void setUp() {
		previousCaptureMinPlayers = Cache.battleCaptureMinPlayers;
		Cache.battleCaptureMinPlayers = 1;
	}

	@AfterEach
	void tearDown() {
		Cache.battleCaptureMinPlayers = previousCaptureMinPlayers;
	}

	@Test
	void resolveColor_sequentialOffFront_isGrayEvenWhenFriendly() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "marker_gray");
			battle.setSequentialCapture(true);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			Location loc = new Location(mock(World.class), 0, 64, 0);

			CapturePoint a = new CapturePoint("A", loc, defender, 100);
			a.setSequenceIndex(0);

			CapturePoint b = new CapturePoint("B", loc, defender, 100);
			b.setSequenceIndex(1);

			CapturePoint c = new CapturePoint("C", loc, attacker, 100);
			c.setSequenceIndex(2);

			CapturePoint deep = new CapturePoint("D", loc, attacker, 100);
			deep.setSequenceIndex(3);

			List<CapturePoint> points = List.of(a, b, c, deep);

			assertEquals(MarkerColor.GRAY,
					CapturePointMarkerService.resolveColor(battle, a, points, true));
			assertEquals(MarkerColor.GRAY,
					CapturePointMarkerService.resolveColor(battle, deep, points, true));
		});
	}

	@Test
	void resolveColor_frontFriendly_isGreen() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "marker_green");
			battle.setSequentialCapture(true);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			Location loc = new Location(mock(World.class), 0, 64, 0);

			CapturePoint a = new CapturePoint("A", loc, defender, 100);
			a.setSequenceIndex(0);
			CapturePoint b = new CapturePoint("B", loc, defender, 100);
			b.setSequenceIndex(1);
			CapturePoint c = new CapturePoint("C", loc, attacker, 100);
			c.setSequenceIndex(2);

			List<CapturePoint> points = List.of(a, b, c);

			assertEquals(MarkerColor.GREEN,
					CapturePointMarkerService.resolveColor(battle, b, points, true));
		});
	}

	@Test
	void resolveColor_frontEnemy_isRed() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "marker_red");
			battle.setSequentialCapture(true);
			Location loc = new Location(mock(World.class), 0, 64, 0);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);

			CapturePoint point = new CapturePoint("E", loc, defender, 100);
			point.setSequenceIndex(4);

			assertEquals(MarkerColor.RED,
					CapturePointMarkerService.resolveColor(battle, point, List.of(point), false));
		});
	}

	@Test
	void resolveColor_twoSidesInZone_isYellow() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "marker_yellow_sides");
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			Location loc = new Location(mock(World.class), 0, 64, 0);

			CapturePoint point = new CapturePoint("A", loc, defender, 100);
			point.setAdvanceSideId(BattleTemplate.ATTACKER_SIDE);
			point.setSequenceIndex(0);
			setSideCounts(point, attacker, 2);
			setSideCounts(point, defender, 1);

			assertEquals(MarkerColor.YELLOW,
					CapturePointMarkerService.resolveColor(battle, point, List.of(point), true));
		});
	}

	@Test
	void resolveColor_partialProgressWithPresence_isYellow() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "marker_yellow_progress");
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			Location loc = new Location(mock(World.class), 0, 64, 0);

			CapturePoint point = new CapturePoint("A", loc, attacker, 50);
			point.setAdvanceSideId(BattleTemplate.ATTACKER_SIDE);
			point.setSequenceIndex(0);
			setSideCounts(point, attacker, 1);

			assertEquals(MarkerColor.YELLOW,
					CapturePointMarkerService.resolveColor(battle, point, List.of(point), true));
		});
	}

	@Test
	void isContested_twoSidesMeetingMin_isTrue() {
		BattleSide attacker = mock(BattleSide.class);
		BattleSide defender = mock(BattleSide.class);
		CapturePoint point = new CapturePoint("A", mock(Location.class), attacker, 100);
		setSideCounts(point, attacker, 1);
		setSideCounts(point, defender, 1);

		assertTrue(point.isContested());
	}

	private static void setSideCounts(CapturePoint point, BattleSide side, int count) {
		try {
			Field playerSideField = CapturePoint.class.getDeclaredField("playerSide");
			playerSideField.setAccessible(true);
			@SuppressWarnings("unchecked")
			HashMap<BattleSide, Integer> playerSide = (HashMap<BattleSide, Integer>) playerSideField.get(point);
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
