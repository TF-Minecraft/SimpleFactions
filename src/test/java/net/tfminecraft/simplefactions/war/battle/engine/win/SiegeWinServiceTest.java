package net.tfminecraft.simplefactions.war.battle.engine.win;

import static org.mockito.Mockito.when;

import net.tfminecraft.simplefactions.war.battle.warband.Warband;

import org.bukkit.entity.Player;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.time.Instant;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleEndSupport;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.enums.LifeType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;

class SiegeWinServiceTest {
	@AfterEach
	void restoreGrace() {
		Cache.battleEmptySideGraceSeconds = 300;
		FieldWinService.clearEmptySideTrackingForTests();
	}

	@Test
	void holdComplete_wouldEndWithAttackerWinner() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "test_siege");
			battle.setContestHoldRemainingSeconds(0);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			attacker.setLives(10);
			defender.setLives(10);

			assertEquals(false, FieldWinService.isSideEliminated(attacker));
			assertEquals(false, FieldWinService.isSideEliminated(defender));
			assertEquals(true, battle.getContestHoldRemainingSeconds() <= 0);
		}
	}

	@Test
	void defenderEliminated_detectedByFieldWinHelper() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			BattleSide defender = new BattleSide(BattleTemplate.DEFENDER_SIDE, LifeType.COLLECTIVE, 5);
			defender.setLives(0);
			assertTrue(FieldWinService.isSideEliminated(defender));
		}
	}

	@Test
	void attackerEliminated_detectedByFieldWinHelper() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			BattleSide attacker = new BattleSide(BattleTemplate.ATTACKER_SIDE, LifeType.COLLECTIVE, 5);
			attacker.setLives(0);
			assertTrue(FieldWinService.isSideEliminated(attacker));
		}
	}

	@Test
	void emptyDefender_doesNotEndSiegeBeforeGrace() {
		try (MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar();
				MockedStatic<BattleEndSupport> end = mockStatic(BattleEndSupport.class)) {
			Cache.battleEmptySideGraceSeconds = 300;
			Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "grace_siege");
			battle.setStarted(true);
			battle.setStartedAt(Instant.now());
			battle.setContestHoldRemainingSeconds(60);
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).setLives(8);
			battle.getSideById(BattleTemplate.DEFENDER_SIDE).setLives(0);

			SiegeWinService.checkSiegeWin(battle);

			end.verifyNoInteractions();
		}
	}

	@Test
	void emptyDefender_endsSiegeForAttackerAfterGrace() {
		try (MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar();
				MockedStatic<BattleEndSupport> end = mockStatic(BattleEndSupport.class)) {
			Cache.battleEmptySideGraceSeconds = 300;
			Instant start = Instant.parse("2026-01-01T00:00:00Z");
			Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "grace_siege_done");
			battle.setStarted(true);
			battle.setStartedAt(start);
			battle.setContestHoldRemainingSeconds(60);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			attacker.setLives(8);
			// The attackers are still online, so only the empty defenders run out their grace.
			UUID attackerId = UUID.randomUUID();
			Player attackerPlayer = mock(Player.class);
			when(attackerPlayer.isOnline()).thenReturn(true);
			bukkit.when(() -> org.bukkit.Bukkit.getPlayer(attackerId)).thenReturn(attackerPlayer);
			attacker.addBand(Warband.createWithMemberIds("atk", attackerId, true));
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			defender.setLives(0);
			FieldWinService.isSideEliminated(battle, defender, start.plusSeconds(300));

			SiegeWinService.checkSiegeWin(battle);

			end.verify(() -> BattleEndSupport.endBattle(battle, BattleTemplate.ATTACKER_SIDE));
		}
	}

	private MockedStatic<org.bukkit.Bukkit> mockBossBar() {
		BossBar bossBar = mock(BossBar.class);
		MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class);
		bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
				Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
				.thenReturn(bossBar);
		bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
				Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
				.thenReturn(bossBar);
		return bukkit;
	}
}
