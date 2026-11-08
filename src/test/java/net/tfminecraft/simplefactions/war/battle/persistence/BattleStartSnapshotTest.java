package net.tfminecraft.simplefactions.war.battle.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.database.BattleData;
import net.tfminecraft.simplefactions.database.CapturePointData;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandLeaveBlock;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.enums.LifeType;
import net.tfminecraft.simplefactions.war.battle.template.BattleLocation;

/** The start snapshot and staff hold that restart and abort rely on, in memory and on disk. */
class BattleStartSnapshotTest {
	private MockedStatic<Bukkit> bukkit;
	private Battle battle;
	private BattleSide attacker;
	private BattleSide defender;
	private CapturePoint point;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any(BarFlag[].class)))
				.thenAnswer(call -> mock(BossBar.class));
		battle = new Battle("snap");
		battle.setBattleType(BattleType.FIELD);
		attacker = new BattleSide("attacker", LifeType.COLLECTIVE, 7);
		defender = new BattleSide("defender", LifeType.COLLECTIVE, 5);
		battle.addSide(attacker);
		battle.addSide(defender);
		point = new CapturePoint("A", new BattleLocation("w", 1, 2, 3, 0f, 0f).toBukkitLocation(), defender, 100);
		point.setAdvanceSideId("attacker");
		battle.addPoint(point);
		battle.addPoint(new CapturePoint("B", new BattleLocation("w", 4, 2, 3, 0f, 0f).toBukkitLocation(), null, 0));
	}

	@AfterEach
	void tearDown() {
		CampaignWarbandLeaveBlock.resetForTests();
		bukkit.close();
	}

	@Test
	void snapshotRecordsLivesAndPointsAndRestoreUndoesTheFight() {
		battle.snapshotStart();
		attacker.tickLife();
		point.setController(attacker);
		point.setCaptureProgress(10);
		point.setAdvanceSideId(null);
		battle.getPointById("B").setController(attacker);
		battle.addPoint(new CapturePoint("C", null, attacker, 50));

		assertEquals(7, attacker.resetLivesToStart());
		battle.restorePointStarts();

		assertEquals(7, attacker.getLives());
		assertEquals(7, attacker.getMaxLives());
		assertSame(defender, point.getController());
		assertEquals(100, point.getCaptureProgress());
		assertEquals("attacker", point.getAdvanceSideId());
		assertNull(battle.getPointById("B").getController());
		assertSame(attacker, battle.getPointById("C").getController());
	}

	@Test
	void pointsRemovedSinceTheStartAreSkipped() {
		battle.snapshotStart();
		battle.getPoints().remove(point);
		battle.restorePointStarts();
		assertEquals(2, battle.getPointStarts().size());
	}

	@Test
	void sidesSavedBeforeTheStartWasRecordedFallBackToTheirMax() {
		attacker.restoreLives(3, 9);
		assertNull(attacker.getStartLives());
		assertEquals(9, attacker.resetLivesToStart());
		battle.setPointStarts(null);
		assertTrue(battle.getPointStarts().isEmpty());
	}

	@Test
	void snapshotAndHoldSurviveASaveAndLoad() {
		battle.snapshotStart();
		battle.setStaffHold(true);
		attacker.tickLife();

		BattleData data = BattleMapper.toData(battle);
		data.startPoints.add(null);
		CapturePointData unnamed = new CapturePointData();
		data.startPoints.add(unnamed);
		Battle restored = BattleMapper.fromData(data);

		assertTrue(restored.isStaffHold());
		assertEquals(Integer.valueOf(7), restored.getSideById("attacker").getStartLives());
		assertEquals(6, restored.getSideById("attacker").getLives());
		assertEquals(List.of(new Battle.PointStart("A", "defender", 100, "attacker"),
				new Battle.PointStart("B", null, 0, null)), restored.getPointStarts());
	}

	@Test
	void oldSavesLoadWithoutHoldOrSnapshot() {
		BattleData data = BattleMapper.toData(battle);
		data.startPoints = null;
		data.sides.forEach(side -> side.startLives = null);

		Battle restored = BattleMapper.fromData(data);

		assertFalse(restored.isStaffHold());
		assertTrue(restored.getPointStarts().isEmpty());
		assertNull(restored.getSideById("defender").getStartLives());
	}

	@Test
	void clearingABattleLetsItsLeaversBackIn() {
		UUID left = UUID.randomUUID();
		CampaignWarbandLeaveBlock.block("snap", "w", left);
		CampaignWarbandLeaveBlock.block("other", "w", left);
		CampaignWarbandLeaveBlock.clearBattle(null);
		CampaignWarbandLeaveBlock.clearBattle("snap");
		assertFalse(CampaignWarbandLeaveBlock.isBlocked("snap", "w", left));
		assertTrue(CampaignWarbandLeaveBlock.isBlocked("other", "w", left));
	}
}
