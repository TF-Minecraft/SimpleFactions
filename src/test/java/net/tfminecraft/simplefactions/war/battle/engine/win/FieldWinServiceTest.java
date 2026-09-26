package net.tfminecraft.simplefactions.war.battle.engine.win;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleEndSupport;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.enums.LifeType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;

class FieldWinServiceTest {
	@AfterEach
	void restoreGrace() {
		Cache.battleEmptySideGraceSeconds = 300;
		FieldWinService.clearEmptySideTrackingForTests();
	}

	@Test
	void sideEliminated_whenLivesZeroAndNoOnlineFighters() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			BattleSide defender = new BattleSide("defender", LifeType.COLLECTIVE, 5);
			defender.setLives(0);

			assertTrue(FieldWinService.isSideEliminated(defender));
		}
	}

	@Test
	void sideNotEliminated_whenFighterOutsideJail() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			World world = mock(World.class);
			BattleSide side = new BattleSide("defender", LifeType.COLLECTIVE, 5);
			side.setJail(new Location(world, 0, 64, 0));

			assertFalse(FieldWinService.isNearJail(new Location(world, 100, 64, 100), world, side));
		}
	}

	@Test
	void sideEliminated_whenAllOnlineFightersNearJail() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			World world = mock(World.class);
			BattleSide side = new BattleSide("defender", LifeType.COLLECTIVE, 5);
			side.setJail(new Location(world, 0, 64, 0));

			assertTrue(FieldWinService.isNearJail(new Location(world, 2, 64, 2), world, side));
		}
	}

	@Test
	void capturePointsDoNotTriggerEliminationByThemselves() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");
			BattleSide defender = battle.getSideById("defender");
			defender.setLives(10);
			battle.addPoint(new CapturePoint("A", new Location(mock(World.class), 1, 64, 1), defender, 100));

			assertFalse(FieldWinService.isSideEliminated(defender));
		}
	}

	@Test
	void emptySide_notEliminatedUntilGraceElapses() {
		try (MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar()) {
			Cache.battleEmptySideGraceSeconds = 300;
			Instant start = Instant.parse("2026-09-26T10:00:00Z");
			Battle battle = startedField("grace_field", start);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			defender.setLives(0);

			assertFalse(FieldWinService.isSideEliminated(battle, defender, start.plusSeconds(299)));
			assertTrue(FieldWinService.isSideEliminated(battle, defender, start.plusSeconds(300)));
		}
	}

	@Test
	void emptySide_withLivesRemaining_isEliminatedOnlyAfterGrace() {
		try (MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar()) {
			Cache.battleEmptySideGraceSeconds = 300;
			Instant start = Instant.parse("2026-09-26T10:00:00Z");
			Battle battle = startedField("lives_field", start);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			defender.setLives(4);

			assertFalse(FieldWinService.isSideEliminated(battle, defender, start.plusSeconds(200)));
			assertTrue(FieldWinService.isSideEliminated(battle, defender, start.plusSeconds(600)));
		}
	}

	@Test
	void emptySide_graceRestartsAfterSomeoneReturns() {
		try (MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar()) {
			Cache.battleEmptySideGraceSeconds = 300;
			Instant start = Instant.parse("2026-09-26T10:00:00Z");
			Battle battle = startedField("return_field", start);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
			defender.setLives(0);
			World world = mock(World.class);
			defender.setJail(new Location(world, 0, 64, 0));

			UUID memberId = UUID.randomUUID();
			Player player = mock(Player.class);
			when(player.isOnline()).thenReturn(true);
			when(player.getWorld()).thenReturn(world);
			when(player.getLocation()).thenReturn(new Location(world, 40, 64, 40));
			bukkit.when(() -> org.bukkit.Bukkit.getPlayer(memberId)).thenReturn(player);
			defender.addBand(Warband.createWithMemberIds("def", memberId, true));

			Instant back = start.plusSeconds(1000);
			assertFalse(FieldWinService.isSideEliminated(battle, defender, back));

			bukkit.when(() -> org.bukkit.Bukkit.getPlayer(memberId)).thenReturn(null);
			Instant left = back.plusSeconds(10);
			assertFalse(FieldWinService.isSideEliminated(battle, defender, left));
			assertFalse(FieldWinService.isSideEliminated(battle, defender, left.plusSeconds(299)));
			assertTrue(FieldWinService.isSideEliminated(battle, defender, left.plusSeconds(300)));
		}
	}

	@Test
	void checkFieldWin_waitsOutEmptySideGrace() {
		try (MockedStatic<org.bukkit.Bukkit> bukkit = mockBossBar();
				MockedStatic<BattleEndSupport> end = mockStatic(BattleEndSupport.class)) {
			Cache.battleEmptySideGraceSeconds = 300;
			Battle battle = startedField("check_field", Instant.now());
			battle.getSideById(BattleTemplate.DEFENDER_SIDE).setLives(0);
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).setLives(6);

			FieldWinService.checkFieldWin(battle);
			end.verifyNoInteractions();
		}
	}

	private Battle startedField(String id, Instant startedAt) {
		Battle battle = BattleFactory.createBlank(BattleType.FIELD, id);
		battle.setStarted(true);
		battle.setStartedAt(startedAt);
		return battle;
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
