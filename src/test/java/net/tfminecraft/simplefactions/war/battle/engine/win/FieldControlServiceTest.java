package net.tfminecraft.simplefactions.war.battle.engine.win;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleEndSupport;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;

class FieldControlServiceTest {
	private GuiTestFixture ui;
	private Player alice;
	private Player bob;
	private Battle battle;
	private BattleSide attacker;
	private BattleSide defender;

	@BeforeEach
	void setUp() {
		ui = new GuiTestFixture();
		when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
				.thenAnswer(call -> mock(BossBar.class));
		Cache.battleControlEnabled = true;
		Cache.battleControlTarget = 1000;
		Cache.battleControlPerSecond = 0.5;
		Cache.battleControlAllPointsMultiplier = 4.0;
		alice = ui.player("Alice");
		bob = ui.player("Bob");
		when(Bukkit.getPlayer(any(java.util.UUID.class)))
				.thenAnswer(call -> call.getArgument(0).equals(alice.getUniqueId()) ? alice : bob);
		ui.online(alice, bob);
		battle = BattleFactory.createBlank(BattleType.FIELD, "control_test");
		attacker = battle.getSideById("attacker");
		defender = battle.getSideById("defender");
		attacker.addBand(new Warband("control_attackers", alice));
		defender.addBand(new Warband("control_defenders", bob));
		battle.setStarted(true);
		battle.setStartedAt(Instant.parse("2026-11-11T20:00:00Z"));
	}

	@AfterEach
	void tearDown() {
		Cache.battleControlEnabled = true;
		Cache.battleControlTarget = 1000;
		Cache.battleControlPerSecond = 0.5;
		Cache.battleControlAllPointsMultiplier = 4.0;
		ui.close();
	}

	@Test
	void controlOnlyCountsInRunningFieldBattlesWithPointsAndTheSettingOn() {
		assertFalse(FieldControlService.isActive(null));
		assertFalse(FieldControlService.isActive(battle), "no points yet");
		points(attacker, 100);
		assertTrue(FieldControlService.isActive(battle));

		Cache.battleControlEnabled = false;
		assertFalse(FieldControlService.isActive(battle));
		Cache.battleControlEnabled = true;

		battle.setControlVictory(false);
		assertFalse(FieldControlService.isActive(battle), "staff switched it off for this battle");
		assertTrue(FieldControlService.scoreboardLines(battle, null).isEmpty());
		battle.setControlVictory(true);

		battle.setCapturePointsEnabled(false);
		assertFalse(FieldControlService.isActive(battle));
		battle.setCapturePointsEnabled(true);

		battle.setStarted(false);
		assertFalse(FieldControlService.isActive(battle));
		battle.setStarted(true);

		battle.setBattleType(BattleType.SIEGE);
		assertFalse(FieldControlService.isActive(battle));
	}

	@Test
	void aPointIsHeldFromHalfCaptureByItsController() {
		CapturePoint point = point("A", attacker, 50);
		assertTrue(FieldControlService.isHeldBy(point, attacker));
		assertFalse(FieldControlService.isHeldBy(point, defender));
		assertFalse(FieldControlService.isHeldBy(point("B", attacker, 49), attacker));
		assertFalse(FieldControlService.isHeldBy(point("C", null, 100), attacker));
		assertFalse(FieldControlService.isHeldBy(null, attacker));
		assertFalse(FieldControlService.isHeldBy(point, null));
	}

	@Test
	void onlyTheSideHoldingMorePointsLeads() {
		points(attacker, 100, defender, 100);
		assertNull(FieldControlService.lead(battle), "1-1 is even");

		points(attacker, 100, defender, 100, attacker, 60);
		FieldControlService.Lead lead = FieldControlService.lead(battle);
		assertSame(attacker, lead.side());
		assertEquals(2, lead.held());
		assertEquals(3, lead.total());

		points(attacker, 40, defender, 20);
		assertNull(FieldControlService.lead(battle), "nobody holds anything");

		battle.addSide(null);
		points(defender, 100);
		assertSame(defender, FieldControlService.lead(battle).side());
	}

	@Test
	void rateFollowsTheShareHeldAndJumpsWhenEveryPointIsHeld() {
		assertEquals(0.0, FieldControlService.ratePerSecond(0, 3));
		assertEquals(0.0, FieldControlService.ratePerSecond(1, 0));
		assertEquals(0.5 * 2 / 3, FieldControlService.ratePerSecond(2, 3), 1e-9);
		assertEquals(2.0, FieldControlService.ratePerSecond(3, 3), 1e-9);
	}

	@Test
	void tickAddsAFifthOfASecondOfControlToTheLeaderOnly() {
		points(attacker, 100, attacker, 100, defender, 100);
		for (int i = 0; i < 5; i++) {
			FieldControlService.tick(battle);
		}
		assertEquals(0.5 * 2 / 3, battle.getControl("attacker"), 1e-9);
		assertEquals(0.0, battle.getControl("defender"));

		points(attacker, 100, defender, 100);
		FieldControlService.tick(battle);
		assertEquals(0.5 * 2 / 3, battle.getControl("attacker"), 1e-9, "an even split earns nothing");

		battle.setStarted(false);
		points(attacker, 100);
		FieldControlService.tick(battle);
		assertEquals(0.5 * 2 / 3, battle.getControl("attacker"), 1e-9, "not running");
	}

	@Test
	void crossingAMilestoneTellsBothSides() {
		points(attacker, 100, defender, 100, attacker, 100);
		battle.setControl("attacker", 499.99);

		FieldControlService.tick(battle);
		FieldControlService.tick(battle);

		verify(alice, times(1)).sendMessage(contains("§aYour side has §e50%"));
		verify(bob, times(1)).sendMessage(contains("§cThe enemy has §e50%"));
		verify(alice).playSound(any(Location.class), eq(Sound.BLOCK_NOTE_BLOCK_BELL), eq(1f), eq(1f));
		verify(bob, never()).sendMessage(contains("75%"));
	}

	@Test
	void reachingTheTargetEndsTheBattleForTheLeaderByControl() {
		points(attacker, 100);
		battle.setControl("attacker", 999.9);

		try (MockedStatic<BattleEndSupport> end = mockStatic(BattleEndSupport.class)) {
			FieldControlService.tick(battle);
			end.verify(() -> BattleEndSupport.endBattle(battle, "attacker", BattleEndReason.CONTROL));
		}
		assertEquals(1000.0, battle.getControl("attacker"));
	}

	@Test
	void theControlLeaderNeedsTheMostControlOnItsOwn() {
		assertNull(FieldControlService.controlLeader(battle));
		battle.setControl("defender", 30);
		assertEquals("defender", FieldControlService.controlLeader(battle));
		battle.setControl("attacker", 30);
		assertNull(FieldControlService.controlLeader(battle));
		battle.addSide(null);
		battle.setControl("attacker", 31);
		assertEquals("attacker", FieldControlService.controlLeader(battle));
	}

	@Test
	void sidebarShowsFriendlyAndEnemyControlAndWhoIsGaining() {
		assertTrue(FieldControlService.scoreboardLines(battle, attacker).isEmpty());
		points(attacker, 100, attacker, 100, defender, 100);
		battle.setControl("attacker", 340.7);
		battle.setControl("defender", 12);

		assertEquals(List.of("§aFriendly control§f: 340/1000 §e▲", "§cEnemy control§f: 12/1000"),
				FieldControlService.scoreboardLines(battle, attacker));
		assertEquals(List.of("§cEnemy control§f: 340/1000 §e▲", "§aFriendly control§f: 12/1000"),
				FieldControlService.scoreboardLines(battle, defender));
		assertEquals(List.of("§eattacker control§f: 340/1000 §e▲", "§edefender control§f: 12/1000"),
				FieldControlService.scoreboardLines(battle, null));

		BattleSide third = new BattleSide("rebels", net.tfminecraft.simplefactions.war.battle.enums.LifeType.COLLECTIVE, 5);
		battle.addSide(third);
		battle.addSide(null);
		points(attacker, 100, defender, 100);
		assertEquals(List.of(
				"§aFriendly control§f: 340/1000",
				"§cEnemy control (defender)§f: 12/1000",
				"§cEnemy control (rebels)§f: 0/1000"),
				FieldControlService.scoreboardLines(battle, attacker));
	}

	@Test
	void milestoneMessagesNameTheShare() {
		assertEquals("§aYour side has §e75%§a of the control needed to win the battle.",
				FieldControlService.milestoneMessage(true, 0.75));
		assertTrue(FieldControlService.milestoneMessage(false, 0.9).contains("§e90%"));
	}

	@Test
	void controlScoresIgnoreCaseAndDropEmptyValues() {
		battle.setControl("Attacker", 5);
		assertEquals(5.0, battle.getControl("attacker"));
		assertEquals(0.0, battle.getControl(null));
		battle.setControl(null, 3);
		battle.setControl("attacker", Double.NaN);
		assertEquals(Map.of(), battle.getControlScores());
		java.util.Map<String, Double> saved = new java.util.HashMap<>();
		saved.put("defender", 7.5);
		saved.put("attacker", null);
		battle.restoreControlScores(saved);
		assertEquals(Map.of("defender", 7.5), battle.getControlScores());
		battle.restoreControlScores(null);
		assertEquals(Map.of(), battle.getControlScores());
		battle.setControl("defender", 2);
		battle.resetControl();
		assertEquals(0.0, battle.getControl("defender"));
	}

	/** Replaces the running points: pairs of owner and capture progress. */
	private void points(Object... ownerAndProgress) {
		List<CapturePoint> list = new ArrayList<>();
		for (int i = 0; i < ownerAndProgress.length; i += 2) {
			list.add(point("P" + i, (BattleSide) ownerAndProgress[i], (Integer) ownerAndProgress[i + 1]));
		}
		battle.getPointManager().setPoints(list);
	}

	private CapturePoint point(String id, BattleSide owner, int progress) {
		return new CapturePoint(id, new Location(ui.world, 0, 64, 0), owner, progress);
	}
}
