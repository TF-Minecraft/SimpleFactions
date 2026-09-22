package me.Plugins.SimpleFactions.War.campaign.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.LaunchResult;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.TransitionResult;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.raid.RaidTargetService.RaidKind;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.core.WarDevMode;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class CampaignRaidServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private Faction defender;
	private War war;
	private Instant raidWindow;
	private InstallationHandler attackerHandler;
	private InstallationHandler defenderHandler;

	@BeforeEach
	void setUp() {
		Cache.warVoteCloseHour = 16;
		Cache.warRaidWindowStartHour = 19;
		Cache.warRaidWindowEndHour = 20;
		Cache.campaignRaidMusterSeconds = 60;
		Cache.campaignRaidDurationSeconds = 600;
		Cache.campaignRaidRepairLockHours = 48;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");

		Installation atkPort = new Installation("port-atk", "Atk Port", InstallationKind.PORT, 10, 0, 0, 0L);
		Installation defPort = new Installation("port-def", "Def Port", InstallationKind.PORT, 20, 0, 0, 0L);
		attackerHandler = mock(InstallationHandler.class);
		defenderHandler = mock(InstallationHandler.class);
		when(attacker.getInstallationHandler()).thenReturn(attackerHandler);
		when(defender.getInstallationHandler()).thenReturn(defenderHandler);
		when(attackerHandler.getById("port-atk")).thenReturn(atkPort);
		when(defenderHandler.getById("port-def")).thenReturn(defPort);

		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setOccupiedByAttacker(new ArrayList<>());
		war.setOccupiedByDefender(new ArrayList<>());

		raidWindow = BattleWindowService.atScheduleHour(BATTLE_DAY, 19);
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.remove(attacker);
		FactionManager.factions.remove(defender);
	}

	@Test
	void canLaunch_okDuringRaidWindow() {
		assertEquals(LaunchResult.STARTED, CampaignRaidService.canLaunch(war, attacker, raidWindow));
	}

	@Test
	void canLaunch_rejectedOutsideWindow() {
		Instant beforeWindow = BattleWindowService.atScheduleHour(BATTLE_DAY, 18);

		assertEquals(LaunchResult.REJECTED_OUTSIDE_WINDOW,
				CampaignRaidService.canLaunch(war, attacker, beforeWindow));
	}

	@Test
	void beginMuster_setsMusterStateAndEndTime() {
		assertEquals(LaunchResult.STARTED,
				CampaignRaidService.beginMuster(war, attacker, "port-atk", "port-def", raidWindow));

		CampaignRaid raid = CampaignRaidService.getActive(war);
		assertNotNull(raid);
		assertEquals(CampaignRaidState.MUSTER, raid.getState());
		assertEquals("def_port_raid", raid.getId());
		assertEquals("Def Port Raid", raid.getDisplayName());
		assertEquals(CampaignCoalition.AGGRESSOR, raid.getAttackerCoalition());
		assertEquals(RaidKind.NAVAL, raid.getRaidKind());
		assertEquals(raidWindow.plusSeconds(60), raid.getMusterEndsAt());
		assertFalse(CampaignRaidService.isSideQuotaUsed(war, CampaignCoalition.AGGRESSOR));
	}

	@Test
	void beginMuster_rejectsInvalidSourceTargetPair() {
		assertEquals(LaunchResult.REJECTED_INVALID_INPUT,
				CampaignRaidService.beginMuster(war, attacker, "port-atk", "port-atk", raidWindow));
	}

	@Test
	void transitionToFighting_consumesQuota() {
		CampaignRaidService.beginMuster(war, attacker, "port-atk", "port-def", raidWindow);
		Instant musterEnd = raidWindow.plusSeconds(60);

		assertEquals(TransitionResult.OK, CampaignRaidService.transitionToFighting(war, musterEnd));

		CampaignRaid raid = CampaignRaidService.getActive(war);
		assertEquals(CampaignRaidState.FIGHTING, raid.getState());
		assertEquals(musterEnd.plusSeconds(600), raid.getFightEndsAt());
		assertTrue(CampaignRaidService.isSideQuotaUsed(war, CampaignCoalition.AGGRESSOR));
		CampaignRaidService.endRaid(war, musterEnd.plusSeconds(1));
		assertEquals(LaunchResult.REJECTED_QUOTA_SPENT,
				CampaignRaidService.canLaunch(war, attacker, musterEnd.plusSeconds(2)));
	}

	@Test
	void defenderCanLaunchAfterAttackerQuotaSpentWhenMutexClear() {
		Installation defPort = new Installation("port-def", "Def Port", InstallationKind.PORT, 20, 0, 0, 0L);
		Installation atkPortTarget = new Installation("port-atk", "Atk Port", InstallationKind.PORT, 10, 0, 0, 0L);
		when(defenderHandler.getById("port-def")).thenReturn(defPort);
		when(attackerHandler.getById("port-atk")).thenReturn(atkPortTarget);

		CampaignRaidService.beginMuster(war, attacker, "port-atk", "port-def", raidWindow);
		CampaignRaidService.transitionToFighting(war, raidWindow.plusSeconds(60));
		CampaignRaidService.endRaid(war, raidWindow.plusSeconds(700));

		assertTrue(CampaignRaidService.isSideQuotaUsed(war, CampaignCoalition.AGGRESSOR));
		assertFalse(CampaignRaidService.isSideQuotaUsed(war, CampaignCoalition.DEFENDER));
		assertEquals(LaunchResult.STARTED,
				CampaignRaidService.canLaunch(war, defender, raidWindow.plusSeconds(701)));
	}

	@Test
	void beginMuster_rejectedWhenRaidInProgress() {
		CampaignRaidService.beginMuster(war, attacker, "port-atk", "port-def", raidWindow);

		assertEquals(LaunchResult.REJECTED_RAID_IN_PROGRESS,
				CampaignRaidService.beginMuster(war, defender, "port-def", "port-atk", raidWindow));
	}

	@Test
	void endRaid_clearsActiveRecordKeepsRepairLock() {
		Instant lockUntil = raidWindow.plus(48, ChronoUnit.HOURS);
		CampaignRaidService.setRepairLockUntil(war, "port-def", lockUntil);

		CampaignRaidService.beginMuster(war, attacker, "port-atk", "port-def", raidWindow);
		CampaignRaidService.endRaid(war, raidWindow.plusSeconds(30));

		assertNull(CampaignRaidService.getActive(war));
		assertTrue(CampaignRaidService.isRepairLocked(war, "port-def", raidWindow.plusSeconds(30)));
	}

	@Test
	void clearForNewBattleDay_resetsQuotaAndActiveRaid() {
		CampaignRaidService.beginMuster(war, attacker, "port-atk", "port-def", raidWindow);
		CampaignRaidService.transitionToFighting(war, raidWindow.plusSeconds(60));

		CampaignRaidService.clearForNewBattleDay(war);

		assertNull(CampaignRaidService.getActive(war));
		assertTrue(war.getCampaignRaidsUsed().isEmpty());
	}

	@Test
	void syncBattleDay_clearsStaleRaidWhenBattleDayAdvances() {
		CampaignRaidService.beginMuster(war, attacker, "port-atk", "port-def", raidWindow);
		war.getCampaignRaidsUsed().put(CampaignCoalition.AGGRESSOR.toJson(), BATTLE_DAY.toString());

		war.setBattleDay(BATTLE_DAY.plusDays(1));
		CampaignRaidService.syncBattleDay(war);

		assertNull(CampaignRaidService.getActive(war));
		assertTrue(war.getCampaignRaidsUsed().isEmpty());
	}

	@Test
	void resetRaidQuota_clearsOneSideOnly() {
		war.getCampaignRaidsUsed().put(CampaignCoalition.AGGRESSOR.toJson(), BATTLE_DAY.toString());
		war.getCampaignRaidsUsed().put(CampaignCoalition.DEFENDER.toJson(), BATTLE_DAY.toString());

		assertEquals(1, CampaignRaidService.resetRaidQuota(war, CampaignCoalition.AGGRESSOR));

		assertFalse(CampaignRaidService.isSideQuotaUsed(war, CampaignCoalition.AGGRESSOR));
		assertTrue(CampaignRaidService.isSideQuotaUsed(war, CampaignCoalition.DEFENDER));
	}

	@Test
	void resetRaidQuota_clearsBothSides() {
		war.getCampaignRaidsUsed().put(CampaignCoalition.AGGRESSOR.toJson(), BATTLE_DAY.toString());
		war.getCampaignRaidsUsed().put(CampaignCoalition.DEFENDER.toJson(), BATTLE_DAY.toString());

		assertEquals(2, CampaignRaidService.resetRaidQuota(war, null));

		assertFalse(CampaignRaidService.isSideQuotaUsed(war, CampaignCoalition.AGGRESSOR));
		assertFalse(CampaignRaidService.isSideQuotaUsed(war, CampaignCoalition.DEFENDER));
	}

	@Test
	void canLaunch_okWhenQuotaSpentAndWarDevmodeEnabled() {
		war.getCampaignRaidsUsed().put(CampaignCoalition.AGGRESSOR.toJson(), BATTLE_DAY.toString());
		assertTrue(CampaignRaidService.isSideQuotaUsed(war, CampaignCoalition.AGGRESSOR));

		WarDevMode.setEnabled(true);
		try {
			assertEquals(LaunchResult.STARTED, CampaignRaidService.canLaunch(war, attacker, raidWindow));
		} finally {
			WarDevMode.resetForTests();
		}
	}

	@Test
	void canLaunch_okOutsideWindowWhenWarDevmodeEnabled() {
		WarDevMode.setEnabled(true);
		try {
			Instant beforeWindow = BattleWindowService.atScheduleHour(BATTLE_DAY, 18);
			assertEquals(LaunchResult.STARTED, CampaignRaidService.canLaunch(war, attacker, beforeWindow));
		} finally {
			WarDevMode.resetForTests();
		}
	}

	@Test
	void repairLockUntilFromStart_usesConfigHours() {
		Instant start = Instant.parse("2026-08-21T19:01:00Z");
		assertEquals(start.plus(48, ChronoUnit.HOURS), CampaignRaidService.repairLockUntilFromStart(start));
	}
}
