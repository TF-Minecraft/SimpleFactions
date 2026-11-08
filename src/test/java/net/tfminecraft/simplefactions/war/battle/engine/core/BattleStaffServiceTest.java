package net.tfminecraft.simplefactions.war.battle.engine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandLeaveBlock;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.enums.LifeType;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyLedger;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.ui.BattleStaffNotices;
import net.tfminecraft.simplefactions.war.battle.ui.BattleWatchService;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;

class BattleStaffServiceTest {
	private MockedStatic<Bukkit> bukkit;
	private MockedStatic<BattlePersistenceService> persistence;
	private MockedStatic<BattleStaffNotices> notices;
	private MockedStatic<BattleEndSupport> endSupport;
	private Battle battle;
	private BattleSide attacker;
	private BattleSide defender;
	private Player soldier;
	private Location spawn;

	@BeforeEach
	void setUp() {
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any(BarFlag[].class)))
				.thenAnswer(call -> mock(BossBar.class));
		bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
		persistence = mockStatic(BattlePersistenceService.class);
		notices = mockStatic(BattleStaffNotices.class);
		endSupport = mockStatic(BattleEndSupport.class);
		battle = new Battle("b1");
		battle.setBattleType(BattleType.FIELD);
		attacker = new BattleSide("attacker", LifeType.COLLECTIVE, 10);
		defender = new BattleSide("defender", LifeType.COLLECTIVE, 8);
		battle.addSide(attacker);
		battle.addSide(defender);
		soldier = mock(Player.class);
		when(soldier.getUniqueId()).thenReturn(UUID.randomUUID());
		when(soldier.getScoreboard()).thenReturn(mock(Scoreboard.class));
		Warband band = mock(Warband.class);
		when(band.getPlayers()).thenReturn(new ArrayList<>(List.of(soldier)));
		attacker.addBand(band);
		spawn = mock(Location.class);
		attacker.setSpawn(spawn);
		defender.addBand(mock(Warband.class));
		battle.setStarted(true);
		battle.snapshotStart();
	}

	@AfterEach
	void tearDown() {
		BattleWatchService.resetForTests();
		CampaignWarbandLeaveBlock.resetForTests();
		BattleRespawnRouting.resetForTests();
		BattleCasualtyLedger.resetForTests();
		endSupport.close();
		notices.close();
		persistence.close();
		bukkit.close();
	}

	@Test
	void restartPutsLivesPointsCasualtiesAndSoldiersBackToTheStart() {
		CapturePoint point = new CapturePoint("A", mock(Location.class), defender, 100);
		battle.addPoint(point);
		battle.snapshotStart();
		point.setController(attacker);
		point.setCaptureProgress(40);
		attacker.tickLife();
		battle.setRecordedSideCasualty("attacker", 3);
		CampaignWarbandLeaveBlock.block("b1", "w", UUID.randomUUID());
		BattleRespawnRouting.scheduleJailRespawn(soldier.getUniqueId(), true);
		battle.setStartedAt(Instant.EPOCH);
		battle.stats().fighters.put("ghost", new net.tfminecraft.simplefactions.war.stats.FighterStats());

		BattleStaffService.Result result = BattleStaffService.restart(battle, "Staff");
		assertTrue(battle.getStats().fighters.isEmpty());

		assertTrue(result.ok());
		assertEquals("§aRestarted b1. Lives: attacker 10, defender 8.", result.message());
		assertEquals(10, attacker.getLives());
		assertSame(defender, point.getController());
		assertEquals(100, point.getCaptureProgress());
		assertTrue(battle.getRecordedSideCasualties().isEmpty());
		assertTrue(battle.getStartedAt().isAfter(Instant.EPOCH));
		assertFalse(BattleRespawnRouting.consumeJailRespawn(soldier.getUniqueId()));
		verify(soldier).teleport(spawn);
		verify(soldier).sendTitle(eq("§eBATTLE RESTARTED"), anyString(), anyInt(), anyInt(), anyInt());
		persistence.verify(() -> BattlePersistenceService.persistBattle(battle));
		notices.verify(() -> BattleStaffNotices.toStaff("§6[Battle] §eStaff §6restarted b1. Lives: attacker 10, defender 8.",
				"Staff"));
	}

	@Test
	void restartingABattleSavedBeforeStartStatesSaysWhatItCouldNotReset() {
		defender.setStartLives(null);
		assertTrue(BattleStaffService.restart(battle, "Staff").message().endsWith("capture points stayed as they were."));
	}

	@Test
	void restartWithoutASpawnLeavesSoldiersWhereTheyStand() {
		attacker.setSpawn(null);
		BattleStaffService.restart(battle, "Staff");
		verify(soldier, never()).teleport(any(Location.class));
	}

	@Test
	void restartingASiegeRefillsTheHoldTimerAndASequentialFieldResyncsItsPoints() {
		battle.setBattleType(BattleType.SIEGE);
		battle.setContestDurationSeconds(90);
		battle.setContestHoldRemainingSeconds(5);
		BattleStaffService.restart(battle, "Staff");
		assertEquals(90, battle.getContestHoldRemainingSeconds());

		battle.setBattleType(BattleType.FIELD);
		battle.setCapturePointsEnabled(true);
		battle.setSequentialCapture(true);
		assertTrue(BattleStaffService.restart(battle, "Staff").ok());
	}

	@Test
	void onlyRunningFieldOrSiegeBattlesCanBeRestartedOrStopped() {
		assertEquals("That battle is not running.", BattleStaffService.restart(null, "Staff").message());
		battle.setBattleType(BattleType.RAID);
		assertEquals("Raids cannot be stopped. End the raid instead.", BattleStaffService.abort(battle, "Staff").message());
		battle.setStarted(false);
		assertFalse(BattleStaffService.restart(battle, "Staff").ok());
	}

	@Test
	void abortStopsWithoutAResultAndHoldsTheBattle() {
		attacker.tickLife();
		battle.setWarId(4);

		battle.stats();
		BattleStaffService.Result result = BattleStaffService.abort(battle, "Staff");
		assertNull(battle.getStats());

		assertTrue(result.ok());
		assertTrue(result.message().endsWith("(/war admin schedule 4 battlestart) or set a new battle day (setday)."));
		assertFalse(battle.hasStarted());
		assertTrue(battle.isStaffHold());
		assertEquals(10, attacker.getLives());
		verify(soldier).sendTitle(eq("§eBATTLE STOPPED"), anyString(), anyInt(), anyInt(), anyInt());
		endSupport.verifyNoInteractions();
	}

	@Test
	void abortingTheWarsFirstBattleGivesBackItsPreBattleOptions() {
		battle.setWarId(4);
		net.tfminecraft.simplefactions.war.core.War war = mock(net.tfminecraft.simplefactions.war.core.War.class);
		try (MockedStatic<net.tfminecraft.simplefactions.managers.WarManager> wars =
				mockStatic(net.tfminecraft.simplefactions.managers.WarManager.class)) {
			wars.when(() -> net.tfminecraft.simplefactions.managers.WarManager.getById(4)).thenReturn(war);
			battle.setFirstBattleOfWar(true);
			BattleStaffService.abort(battle, "Staff");
			verify(war).setFirstBattleStarted(false);
			wars.verify(() -> net.tfminecraft.simplefactions.managers.WarManager.persist(war));

			// A later battle (after raids, a no-winner end or autoresolve) leaves the flag alone.
			battle.setFirstBattleOfWar(false);
			battle.setStarted(true);
			BattleStaffService.abort(battle, "Staff");
			verify(war).setFirstBattleStarted(false);
		}
	}

	@Test
	void onlyAPreparedCampaignBattleStartsOnRelease() {
		assertFalse(BattleStaffService.startsOnRelease(null));
		battle.setStarted(true);
		battle.setWarId(4);
		assertFalse(BattleStaffService.startsOnRelease(battle));
		battle.setStarted(false);
		battle.setWarId(null);
		assertFalse(BattleStaffService.startsOnRelease(battle));
	}

	@Test
	void releasingAnOverdueCampaignBattleSaysItStartsAtOnce() {
		battle.setStarted(false);
		battle.setWarId(4);
		net.tfminecraft.simplefactions.war.core.War war = mock(net.tfminecraft.simplefactions.war.core.War.class);
		try (MockedStatic<net.tfminecraft.simplefactions.managers.WarManager> wars =
				mockStatic(net.tfminecraft.simplefactions.managers.WarManager.class)) {
			wars.when(() -> net.tfminecraft.simplefactions.managers.WarManager.getById(4)).thenReturn(war);
			assertTrue(BattleStaffService.hold(battle, true, "Staff").message().endsWith("will not start on schedule."));
			String due = "It starts when its battle is scheduled and due.";
			assertTrue(BattleStaffService.hold(battle, false, "Staff").message().endsWith(due));
			when(war.getScheduledBattleAt()).thenReturn(Instant.EPOCH);
			assertTrue(BattleStaffService.hold(battle, false, "Staff").message().endsWith(due));
			when(war.getBattleSchedulePhase()).thenReturn(net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase.SCHEDULED);
			assertTrue(BattleStaffService.hold(battle, false, "Staff").message().endsWith("starts within a minute."));
			when(war.getScheduledBattleAt()).thenReturn(null);
			assertTrue(BattleStaffService.hold(battle, false, "Staff").message().endsWith(due));
			when(war.getScheduledBattleAt()).thenReturn(Instant.now().plusSeconds(3600));
			assertTrue(BattleStaffService.hold(battle, false, "Staff").message().endsWith(due));
		}
	}

	@Test
	void correctingARaidsLivesLeavesItsOwnBarsAlone() {
		battle.setCampaignRaid(true);
		BattleStaffService.adjustLives(battle, "defender", "set", 5, "Staff");
		assertEquals(5, defender.getLives());
	}

	@Test
	void endingLeavesTheStaffNoticeToTheBattlesEndEvent() {
		BattleStaffService.end(battle, null, "Staff");
		notices.verifyNoInteractions();
	}

	@Test
	void abortingAManualBattlePointsToTheEditor() {
		assertTrue(BattleStaffService.abort(battle, "Staff").message().endsWith("(/battle edit)."));
	}

	@Test
	void endChecksTheBattleAndTheWinner() {
		assertEquals("That battle is not running.", BattleStaffService.end(null, null, "Staff").message());
		assertEquals("No side nobody in this battle.", BattleStaffService.end(battle, "nobody", "Staff").message());
		battle.setStarted(false);
		assertFalse(BattleStaffService.end(battle, null, "Staff").ok());
	}

	@Test
	void endWithAWinnerUsesTheSidesOwnIdAndSavesAManualBattle() {
		BattleStaffService.Result result = BattleStaffService.end(battle, "ATTACKER", "Staff");

		assertEquals("§aEnded b1 with attacker as the winner.", result.message());
		endSupport.verify(() -> BattleEndSupport.endBattle(battle, "attacker", BattleEndReason.SIDE_WIN));
		persistence.verify(() -> BattlePersistenceService.persistBattle(battle));
	}

	@Test
	void endWithNoWinnerLeavesTheCampaignBattleToItsResult() {
		battle.setWarId(4);

		assertEquals("§aEnded b1 with no winner.", BattleStaffService.end(battle, null, "Staff").message());

		endSupport.verify(() -> BattleEndSupport.endBattle(battle, null, BattleEndReason.TIMER));
		persistence.verify(() -> BattlePersistenceService.persistBattle(battle), never());
	}

	@Test
	void holdOnlyAppliesToABattleThatIsNotRunning() {
		assertEquals("No such battle.", BattleStaffService.hold(null, true, "Staff").message());
		assertFalse(BattleStaffService.hold(battle, true, "Staff").ok());
		battle.setStarted(false);

		assertEquals("§aPut b1 on hold.", BattleStaffService.hold(battle, true, "Staff").message());
		assertTrue(battle.isStaffHold());
		assertEquals("§aReleased the hold on b1.", BattleStaffService.hold(battle, false, "Staff").message());
		assertFalse(battle.isStaffHold());
	}

	@Test
	void livesCanBeAddedRemovedOrSetDuringTheBattle() {
		assertEquals("§aChanged attacker lives in b1 from 10 to 13.",
				BattleStaffService.adjustLives(battle, "attacker", "ADD", 3, "Staff").message());
		BattleStaffService.adjustLives(battle, "attacker", "remove", 20, "Staff");
		assertEquals(0, attacker.getLives());
		BattleStaffService.adjustLives(battle, "attacker", "set", Integer.MAX_VALUE, "Staff");
		assertEquals(Integer.MAX_VALUE, attacker.getLives());
		assertEquals(Integer.valueOf(10), attacker.getStartLives());
	}

	@Test
	void livesCorrectionsAreChecked() {
		assertFalse(BattleStaffService.adjustLives(null, "attacker", "add", 1, "Staff").ok());
		assertEquals("No side x in this battle.", BattleStaffService.adjustLives(battle, "x", "add", 1, "Staff").message());
		assertEquals("The amount must be 0 or more.",
				BattleStaffService.adjustLives(battle, "attacker", "add", -1, "Staff").message());
		assertEquals("Use add, remove or set.",
				BattleStaffService.adjustLives(battle, "attacker", "double", 1, "Staff").message());
		battle.setStarted(false);
		assertFalse(BattleStaffService.adjustLives(battle, "attacker", "add", 1, "Staff").ok());
	}

	private static void assertSame(Object expected, Object actual) {
		org.junit.jupiter.api.Assertions.assertSame(expected, actual);
	}
}
