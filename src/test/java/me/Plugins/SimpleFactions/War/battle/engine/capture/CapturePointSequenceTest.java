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
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;

import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;



class CapturePointSequenceTest {

	@Test
	void autoIds_incrementGloballyAcrossSides() {

		BossBar bossBar = mock(BossBar.class);

		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {

			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(

					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))

					.thenReturn(bossBar);

			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(

					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))

					.thenReturn(bossBar);



			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");

			BattleSide attacker = battle.getSideById("attacker");

			BattleSide defender = battle.getSideById("defender");

			Location loc = new Location(mock(World.class), 0, 64, 0);



			CapturePoint a = BattleCapturePoints.createAtPlayer(battle, "attacker", loc, attacker);

			battle.addPoint(a);

			BattleCapturePoints.afterPointListChanged(battle);

			CapturePoint b = BattleCapturePoints.createAtPlayer(battle, "attacker", loc, attacker);

			battle.addPoint(b);

			BattleCapturePoints.afterPointListChanged(battle);

			CapturePoint c = BattleCapturePoints.createAtPlayer(battle, "defender", loc, defender);

			battle.addPoint(c);

			BattleCapturePoints.afterPointListChanged(battle);



			assertEquals("A", battle.getPointById("A").getId());

			assertEquals("B", battle.getPointById("B").getId());

			assertEquals("C", battle.getPointById("C").getId());

			assertEquals(0, battle.getPointById("A").getSequenceIndex());

			assertEquals(1, battle.getPointById("B").getSequenceIndex());

			assertEquals(2, battle.getPointById("C").getSequenceIndex());

		}

	}



	@Test

	void removePoint_renumbersLetterStyleIds() {

		BossBar bossBar = mock(BossBar.class);

		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {

			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(

					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))

					.thenReturn(bossBar);

			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(

					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))

					.thenReturn(bossBar);



			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");

			BattleSide attacker = battle.getSideById("attacker");

			Location loc = new Location(mock(World.class), 0, 64, 0);



			CapturePoint a = BattleCapturePoints.createAtPlayer(battle, "attacker", loc, attacker);

			battle.addPoint(a);

			BattleCapturePoints.afterPointListChanged(battle);

			CapturePoint b = BattleCapturePoints.createAtPlayer(battle, "attacker", loc, attacker);

			battle.addPoint(b);

			BattleCapturePoints.afterPointListChanged(battle);

			CapturePoint c = BattleCapturePoints.createAtPlayer(battle, "attacker", loc, attacker);

			battle.addPoint(c);

			BattleCapturePoints.afterPointListChanged(battle);



			BattleCapturePoints.removePoint(battle, b);



			assertEquals(2, battle.getPoints().size());

			assertEquals("A", battle.getPoints().get(0).getId());

			assertEquals("B", battle.getPoints().get(1).getId());

			assertEquals(0, battle.getPoints().get(0).getSequenceIndex());

			assertEquals(1, battle.getPoints().get(1).getSequenceIndex());

		}

	}



	@Test

	void removePoint_renamesCustomIdOnCompress() {

		BossBar bossBar = mock(BossBar.class);

		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {

			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(

					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))

					.thenReturn(bossBar);

			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(

					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))

					.thenReturn(bossBar);



			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");

			BattleSide attacker = battle.getSideById("attacker");

			Location loc = new Location(mock(World.class), 0, 64, 0);



			CapturePoint custom = new CapturePoint("tower", loc, attacker, 100);

			custom.setAdvanceSideId("attacker");

			custom.setSequenceIndex(0);

			battle.addPoint(custom);

			CapturePoint b = BattleCapturePoints.createAtPlayer(battle, "attacker", loc, attacker);

			battle.addPoint(b);

			BattleCapturePoints.afterPointListChanged(battle);



			BattleCapturePoints.removePoint(battle, custom);



			assertEquals(1, battle.getPoints().size());

			assertEquals("A", battle.getPoints().get(0).getId());

			assertEquals(0, battle.getPoints().get(0).getSequenceIndex());

		}

	}



	@Test

	void sequentialFront_blocksDeepCapturePerSideWhenNotGlobal() {

		BossBar bossBar = mock(BossBar.class);

		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {

			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(

					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))

					.thenReturn(bossBar);

			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(

					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))

					.thenReturn(bossBar);



			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");

			BattleSide attacker = battle.getSideById("attacker");

			BattleSide defender = battle.getSideById("defender");

			Location loc = new Location(mock(World.class), 0, 64, 0);



			CapturePoint front = new CapturePoint("A", loc, defender, 50);

			front.setAdvanceSideId("attacker");

			front.setSequenceIndex(0);



			CapturePoint deep = new CapturePoint("B", loc, defender, 50);

			deep.setAdvanceSideId("attacker");

			deep.setSequenceIndex(1);



			List<CapturePoint> points = new ArrayList<>();

			points.add(front);

			points.add(deep);



			assertTrue(CapturePoint.isFrontPoint(front, points, false));

			assertFalse(CapturePoint.isFrontPoint(deep, points, false));

		}

	}

}


