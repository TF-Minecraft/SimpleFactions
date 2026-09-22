package me.Plugins.SimpleFactions.War.battle.engine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.War.battle.engine.capture.CapturePoint;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;

class BattleSideSetupServiceTest {
	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
	}

	@Test
	void setSpawn_syncsLinearCapturePointsWhenDefenderSpawnSet() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "sync");
			battle.setSequentialCapture(true);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);

			CapturePoint close = BattleSideSetupService.addCapturePoint(
					battle, attacker, location(5, 64, 0));
			CapturePoint far = BattleSideSetupService.addCapturePoint(
					battle, attacker, location(100, 64, 0));

			BattleSideSetupService.setSpawn(battle, defender, location(0, 64, 0));
			BattleSideSetupService.setSpawn(battle, attacker, location(200, 64, 0));

			assertEquals("A", close.getId());
			assertEquals("B", far.getId());
			assertEquals(0, close.getSequenceIndex());
			assertEquals(1, far.getSequenceIndex());
		});
	}

	@Test
	void setSpawnAndJail_updateSideLocations() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");
			BattleSide side = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			Location spawn = location(1, 64, 2);
			Location jail = location(3, 64, 4);

			BattleSideSetupService.setSpawn(battle, side, spawn);
			BattleSideSetupService.setJail(battle, side, jail);

			assertEquals(spawn, side.getSpawn());
			assertEquals(jail, side.getJail());
		});
	}

	@Test
	void addCapturePoint_namesPointsSequentiallyGlobally() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);

			CapturePoint a = BattleSideSetupService.addCapturePoint(battle, attacker, location(10, 64, 10));
			CapturePoint b = BattleSideSetupService.addCapturePoint(battle, attacker, location(11, 64, 11));
			CapturePoint defenderPoint = BattleSideSetupService.addCapturePoint(battle, defender, location(20, 64, 20));

			assertEquals("A", a.getId());
			assertEquals("B", b.getId());
			assertEquals("C", defenderPoint.getId());
			assertEquals(attacker, a.getController());
			assertEquals(BattleTemplate.ATTACKER_SIDE, a.getAdvanceSideId());
			assertEquals(3, battle.getPoints().size());
		});
	}

	@Test
	void addCapturePoint_rejectsWhenDisabled() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");
			battle.setCapturePointsEnabled(false);
			BattleSide side = battle.getSideById(BattleTemplate.ATTACKER_SIDE);

			assertThrows(IllegalStateException.class,
					() -> BattleSideSetupService.addCapturePoint(battle, side, location(1, 64, 1)));
		});
	}

	@Test
	void createBlank_defaultsCapturePointsByType() {
		withMockBossBar(() -> {
			Battle field = BattleFactory.createBlank(BattleType.FIELD, "field");
			Battle siege = BattleFactory.createBlank(BattleType.SIEGE, "siege");

			assertEquals(true, field.isCapturePointsEnabled());
			assertEquals(false, siege.isCapturePointsEnabled());
		});
	}

	private void withMockBossBar(Runnable action) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			action.run();
		}
	}

	private Location location(double x, double y, double z) {
		World world = mock(World.class);
		return new Location(world, x, y, z);
	}
}
