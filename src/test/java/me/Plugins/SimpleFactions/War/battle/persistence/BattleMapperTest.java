package me.Plugins.SimpleFactions.War.battle.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.time.Instant;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Database.BattleData;
import me.Plugins.SimpleFactions.Database.WarbandData;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.enums.LifeType;
import me.Plugins.SimpleFactions.War.battle.template.BattleLocation;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;

class BattleMapperTest {
	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
	}

	@Test
	void roundTrip_preservesBattleLayout() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "manual_field");
			battle.setDisplayName("Test Field");
			battle.setFriendlyFire(false);
			battle.setSequentialCapture(true);
			battle.setCapturePointsEnabled(true);
			battle.setStarted(true);
			battle.setContestHoldRemainingSeconds(42);

			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			attacker.restoreLives(12, 25);
			attacker.setSpawn(new BattleLocation("TFMC_Map", 1.5, 64.0, 2.5, 0f, 0f).toBukkitLocation());

			Warband warband = Warband.createWithMemberIds("band_a", UUID.randomUUID(), true);
			attacker.addBand(warband);

			BattleData data = BattleMapper.toData(battle);
			Battle restored = BattleMapper.fromData(data);

			assertNotNull(restored);
			assertEquals("manual_field", restored.getId());
			assertEquals("Test Field", restored.getDisplayName());
			assertFalse(restored.hasFriendlyFire());
			assertTrue(restored.isSequentialCapture());
			assertTrue(restored.isCapturePointsEnabled());
			assertTrue(restored.hasStarted());
			assertEquals(42, restored.getContestHoldRemainingSeconds());
			assertEquals(12, restored.getSideById(BattleTemplate.ATTACKER_SIDE).getLives());
			assertEquals(25, restored.getSideById(BattleTemplate.ATTACKER_SIDE).getMaxLives());
		}
	}

	@Test
	void roundTrip_preservesLootFlag() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "loot_off_field");
			battle.setLootEnabled(false);

			BattleData data = BattleMapper.toData(battle);

			assertFalse(data.lootEnabled);
			assertFalse(BattleMapper.fromData(data).hasLootEnabled());
		}
	}

	@Test
	void fromData_defaultsLootOnWhenKeyIsAbsent() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);

			BattleData data = new BattleData();
			data.id = "legacy_field";
			data.battleType = BattleType.FIELD.toJson();

			assertTrue(BattleMapper.fromData(data).hasLootEnabled());
		}
	}

	@Test
	void fromData_defaultsLootOffForLegacyCampaignRaid() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);

			BattleData data = new BattleData();
			data.id = "legacy_raid";
			data.battleType = BattleType.RAID.toJson();
			data.campaignRaid = true;

			assertFalse(BattleMapper.fromData(data).hasLootEnabled());
		}
	}

	@Test
	void roundTrip_startedAt() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);

			Instant startedAt = Instant.parse("2026-08-21T18:00:00Z");
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_field");
			battle.setStarted(true);
			battle.setStartedAt(startedAt);

			BattleData data = BattleMapper.toData(battle);
			Battle restored = BattleMapper.fromData(data);

			assertNotNull(restored);
			assertTrue(restored.hasStarted());
			assertEquals(startedAt, restored.getStartedAt());
		}
	}
}
