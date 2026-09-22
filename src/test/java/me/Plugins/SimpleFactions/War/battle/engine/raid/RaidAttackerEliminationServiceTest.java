package me.Plugins.SimpleFactions.War.battle.engine.raid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.War.battle.engine.capture.CapturePoint;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleLocation;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.template.CapturePointDefinition;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;

class RaidAttackerEliminationServiceTest {
	@BeforeEach
	void setUp() {
		RaidAttackerEliminationService.resetForTests();
		BattleManager.resetForTests();
	}

	@Test
	void markOut_tracksAttackerAsOut() {
		withMockWorldAndBossBar(() -> {
			Battle battle = startedRaid("test_raid");
			UUID attackerId = UUID.randomUUID();
			RaidAttackerEliminationService.markOut(battle, attackerId);
			assertTrue(RaidAttackerEliminationService.isMarkedOut(battle, attackerId));
		});
	}

	@Test
	void offlineAttackerCountsAsOut() {
		withMockWorldAndBossBar(() -> {
			Battle battle = startedRaid("test_raid");
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			UUID offlineId = UUID.randomUUID();
			attacker.addBand(Warband.createWithMemberIds("alpha", offlineId, true));
			assertTrue(RaidAttackerEliminationService.isAttackerSideEliminated(battle));
		});
	}

	@Test
	void markedOutAttackerCountsAsOut() {
		withMockWorldAndBossBar(() -> {
			Battle battle = startedRaid("test_raid");
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			UUID attackerId = UUID.randomUUID();
			attacker.addBand(Warband.createWithMemberIds("alpha", attackerId, true));
			RaidAttackerEliminationService.markOut(battle, attackerId);
			assertTrue(RaidAttackerEliminationService.isAttackerSideEliminated(battle));
		});
	}

	@Test
	void emptyAttackerSideIsNotEliminated() {
		withMockWorldAndBossBar(() -> {
			Battle battle = startedRaid("test_raid");
			assertFalse(RaidAttackerEliminationService.isAttackerSideEliminated(battle));
		});
	}

	private Battle startedRaid(String id) {
		Battle battle = BattleFactory.createBlank(BattleType.RAID, id);
		battle.setRaidTarget(new CapturePointDefinition("target", new BattleLocation("world", 10, 64, 10, 0, 0)));
		battle.start();
		return battle;
	}

	private void withMockWorldAndBossBar(Runnable action) {
		World world = mock(World.class);
		when(world.getName()).thenReturn("world");
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
			bukkit.when(() -> Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);
			action.run();
		}
	}
}
