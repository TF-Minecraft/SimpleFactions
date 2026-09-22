package me.Plugins.SimpleFactions.War.battle.engine.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSideSetupService;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;

class CapturePointChainSyncTest {
	@Test
	void syncLinearChain_ordersFromDefenderSpawnByGreedyNearestNeighbor() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "chain");
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			World world = mock(World.class);

			defender.setSpawn(new Location(world, 0, 64, 0));

			CapturePoint far = point("far", location(world, 100, 64, 0), attacker, 2);
			CapturePoint close = point("close", location(world, 5, 64, 0), attacker, 0);
			CapturePoint mid = point("mid", location(world, 10, 64, 0), defender, 1);

			battle.addPoint(far);
			battle.addPoint(mid);
			battle.addPoint(close);

			BattleCapturePoints.syncLinearChain(battle);

			assertEquals("A", close.getId());
			assertEquals(0, close.getSequenceIndex());
			assertEquals("B", mid.getId());
			assertEquals(1, mid.getSequenceIndex());
			assertEquals("C", far.getId());
			assertEquals(2, far.getSequenceIndex());
		});
	}

	@Test
	void syncLinearChain_pinsDefenderClosestFirstAndAttackerClosestLast() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "axis");
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			World world = mock(World.class);

			defender.setSpawn(new Location(world, 0, 64, 0));
			attacker.setSpawn(new Location(world, 200, 64, 0));

			CapturePoint nearDefender = point("nearDef", location(world, 8, 64, 2), defender, 0);
			CapturePoint midOffset = point("midOffset", location(world, 100, 64, 40), attacker, 1);
			CapturePoint nearAttacker = point("nearAtt", location(world, 192, 64, -3), attacker, 2);

			battle.addPoint(midOffset);
			battle.addPoint(nearAttacker);
			battle.addPoint(nearDefender);

			BattleCapturePoints.syncLinearChain(battle);

			assertEquals("A", nearDefender.getId());
			assertEquals("B", midOffset.getId());
			assertEquals("C", nearAttacker.getId());
			assertEquals(0, nearDefender.getSequenceIndex());
			assertEquals(2, nearAttacker.getSequenceIndex());
		});
	}

	@Test
	void syncLinearChain_renamesCustomIdsToLetters() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "rename");
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			World world = mock(World.class);

			defender.setSpawn(new Location(world, 0, 64, 0));
			CapturePoint tower = point("tower", location(world, 3, 64, 0), attacker, 0);
			battle.addPoint(tower);

			battle.setSequentialCapture(true);
			BattleCapturePoints.syncLinearChain(battle);

			assertEquals("A", tower.getId());
			assertEquals(0, tower.getSequenceIndex());
		});
	}

	@Test
	void frontline_attackerOwnsLastPoint_contestsPreviousPair() {
		withMockBossBar(() -> {
			BattleSide attacker = mockSide(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = mockSide(BattleTemplate.DEFENDER_SIDE);
			Location loc = location(mock(World.class), 0, 64, 0);

			List<CapturePoint> points = chainFivePoints(loc, attacker, defender);
			points.get(4).setController(attacker);
			points.get(4).setCaptureProgress(100);

			assertFalse(CapturePoint.isFrontPoint(points.get(0), points, true));
			assertFalse(CapturePoint.isFrontPoint(points.get(1), points, true));
			assertFalse(CapturePoint.isFrontPoint(points.get(2), points, true));
			assertTrue(CapturePoint.isFrontPoint(points.get(3), points, true));
			assertTrue(CapturePoint.isFrontPoint(points.get(4), points, true));
		});
	}

	@Test
	void frontline_attackerOwnsNoPoints_contestsLastPointOnly() {
		withMockBossBar(() -> {
			BattleSide attacker = mockSide(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = mockSide(BattleTemplate.DEFENDER_SIDE);
			Location loc = location(mock(World.class), 0, 64, 0);

			List<CapturePoint> points = chainFivePoints(loc, attacker, defender);

			assertFalse(CapturePoint.isFrontPoint(points.get(0), points, true));
			assertFalse(CapturePoint.isFrontPoint(points.get(3), points, true));
			assertTrue(CapturePoint.isFrontPoint(points.get(4), points, true));
		});
	}

	@Test
	void frontline_defenderOwnsNoPoints_contestsFirstPointOnly() {
		withMockBossBar(() -> {
			BattleSide attacker = mockSide(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = mockSide(BattleTemplate.DEFENDER_SIDE);
			Location loc = location(mock(World.class), 0, 64, 0);

			List<CapturePoint> points = chainFivePoints(loc, attacker, defender);
			for (CapturePoint point : points) {
				point.setController(attacker);
				point.setCaptureProgress(100);
			}

			assertTrue(CapturePoint.isFrontPoint(points.get(0), points, true));
			assertFalse(CapturePoint.isFrontPoint(points.get(1), points, true));
			assertFalse(CapturePoint.isFrontPoint(points.get(4), points, true));
		});
	}

	@Test
	void frontline_attackerOwnsLastTwoPoints_contestsMiddlePair() {
		withMockBossBar(() -> {
			BattleSide attacker = mockSide(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = mockSide(BattleTemplate.DEFENDER_SIDE);
			Location loc = location(mock(World.class), 0, 64, 0);

			List<CapturePoint> points = chainFivePoints(loc, attacker, defender);
			points.get(3).setController(attacker);
			points.get(3).setCaptureProgress(100);
			points.get(4).setController(attacker);
			points.get(4).setCaptureProgress(100);

			assertFalse(CapturePoint.isFrontPoint(points.get(0), points, true));
			assertFalse(CapturePoint.isFrontPoint(points.get(1), points, true));
			assertTrue(CapturePoint.isFrontPoint(points.get(2), points, true));
			assertTrue(CapturePoint.isFrontPoint(points.get(3), points, true));
			assertFalse(CapturePoint.isFrontPoint(points.get(4), points, true));
		});
	}

	@Test
	void frontline_twoPointChain_meetsOnBothWhenSplit() {
		withMockBossBar(() -> {
			BattleSide attacker = mockSide(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = mockSide(BattleTemplate.DEFENDER_SIDE);
			Location loc = location(mock(World.class), 0, 64, 0);

			CapturePoint nearDefender = heldPoint("A", loc, defender, 0);
			CapturePoint nearAttacker = heldPoint("B", loc, attacker, 1);
			List<CapturePoint> points = List.of(nearDefender, nearAttacker);

			assertTrue(CapturePoint.isFrontPoint(nearDefender, points, true));
			assertTrue(CapturePoint.isFrontPoint(nearAttacker, points, true));
		});
	}

	@Test
	void frontline_editorPlacedPoints_useMeetNotDefenderEnd() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "frontline");
			battle.setSequentialCapture(true);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			World world = mock(World.class);

			defender.setSpawn(new Location(world, 0, 64, 0));
			attacker.setSpawn(new Location(world, 200, 64, 0));

			BattleSideSetupService.addCapturePoint(battle, defender, location(world, 5, 64, 0));
			BattleSideSetupService.addCapturePoint(battle, defender, location(world, 100, 64, 0));
			BattleSideSetupService.addCapturePoint(battle, attacker, location(world, 150, 64, 0));
			BattleCapturePoints.syncLinearChain(battle);

			List<CapturePoint> points = battle.getPoints();
			CapturePoint a = points.get(0);
			CapturePoint b = points.get(1);
			CapturePoint c = points.get(2);

			assertFalse(CapturePoint.isFrontPoint(a, points, battle));
			assertTrue(CapturePoint.isFrontPoint(b, points, battle));
			assertTrue(CapturePoint.isFrontPoint(c, points, battle));
		});
	}

	private static List<CapturePoint> chainFivePoints(Location loc, BattleSide attacker, BattleSide defender) {
		List<CapturePoint> points = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			points.add(heldPoint(String.valueOf((char) ('A' + i)), loc, defender, i));
		}
		return points;
	}

	private static CapturePoint heldPoint(String id, Location location, BattleSide controller, int sequenceIndex) {
		CapturePoint point = new CapturePoint(id, location, controller, 100);
		point.setSequenceIndex(sequenceIndex);
		return point;
	}

	private static BattleSide mockSide(String id) {
		BattleSide side = mock(BattleSide.class);
		Mockito.when(side.getId()).thenReturn(id);
		return side;
	}

	private static CapturePoint point(String id, Location location, BattleSide controller, int sequenceIndex) {
		CapturePoint point = new CapturePoint(id, location, controller, 100);
		point.setAdvanceSideId(controller.getId());
		point.setSequenceIndex(sequenceIndex);
		return point;
	}

	private static Location location(World world, double x, double y, double z) {
		return new Location(world, x, y, z);
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
