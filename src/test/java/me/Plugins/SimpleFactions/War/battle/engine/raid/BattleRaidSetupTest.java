package me.Plugins.SimpleFactions.War.battle.engine.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

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
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;
import me.Plugins.SimpleFactions.War.battle.template.BattleLocation;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.template.CapturePointDefinition;

class BattleRaidSetupTest {
	@Test
	void onStart_campaignRaidSkipsCapturePoint() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.RAID, "campaign_raid_test");
			battle.setCampaignRaid(true);
			battle.setRaidTarget(new CapturePointDefinition("target", new BattleLocation("world", 10, 64, 10, 0, 0)));
			battle.start();

			assertTrue(battle.getPoints().isEmpty());
		});
	}

	@Test
	void onStart_seedsTargetPointAndClearsBounds() {
		withMockWorldAndBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.RAID, "test_raid");
			battle.setProvinceId(42);
			battle.setRaidTarget(new CapturePointDefinition("target", new BattleLocation("world", 10, 64, 10, 0, 0)));
			battle.start();

			assertEquals(java.util.Collections.emptySet(), battle.getAllowedProvinceIds());
			assertEquals(1, battle.getPoints().size());
			assertEquals("target", battle.getPoints().get(0).getId());
			assertEquals(BattleTemplate.DEFENDER_SIDE, battle.getPoints().get(0).getController().getId());
			assertEquals(1, battle.getPointManager().getPoints().size());
		});
	}

	@Test
	void onStart_appliesDefenderLivesPool() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.RAID, "test_raid");
			battle.setDefenderRespawnMode(DefenderRespawnMode.LIVES);
			battle.setDefenderLives(15);
			battle.start();

			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			assertEquals(15, defender.getLives());
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			assertEquals(0, attacker.getLives());
		});
	}

	@Test
	void onStart_infiniteDefendersGetHighLifeDisplay() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.RAID, "test_raid");
			battle.setDefenderRespawnMode(DefenderRespawnMode.INFINITE);
			battle.start();

			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			assertTrue(defender.getLives() > 1000);
		});
	}

	private void withMockBossBar(Runnable action) {
		World world = mock(World.class);
		when(world.getName()).thenReturn("world");
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.getWorld("world")).thenReturn(world);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);
			action.run();
		}
	}

	private void withMockWorldAndBossBar(Runnable action) {
		withMockBossBar(action);
	}
}
