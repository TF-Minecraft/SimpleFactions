package net.tfminecraft.simplefactions.war.campaign.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.core.War;

/** A staff hold lasts one battle day: a new day releases it so the next battle is not skipped in silence. */
class BattleScheduleStaffHoldTest {
	private MockedStatic<BattlePersistenceService> persistence;
	private War war;
	private Battle battle;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		persistence = mockStatic(BattlePersistenceService.class);
		war = mock(War.class);
		when(war.getId()).thenReturn(5);
		battle = new Battle("campaign_w5");
		battle.setWarId(5);
	}

	@AfterEach
	void tearDown() {
		persistence.close();
		BattleManager.resetForTests();
	}

	@Test
	void aNewBattleDayReleasesTheHold() {
		BattleScheduleService.releaseStaffHold(war);
		BattleManager.addBattle(battle);
		BattleScheduleService.releaseStaffHold(war);
		persistence.verify(() -> BattlePersistenceService.persistBattle(battle), never());

		battle.setStaffHold(true);
		BattleScheduleService.releaseStaffHold(war);
		assertFalse(battle.isStaffHold());
		persistence.verify(() -> BattlePersistenceService.persistBattle(battle));
	}

	@Test
	void postponingOrSkippingTheBattleDayReleasesTheHoldToo() {
		when(war.getBattleDay()).thenReturn(java.time.LocalDate.of(2026, 11, 8));
		BattleManager.addBattle(battle);
		battle.setStaffHold(true);
		try (var progression = mockStatic(net.tfminecraft.simplefactions.war.campaign.progression.CampaignProgressionService.class);
				var picks = mockStatic(net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService.class);
				var raids = mockStatic(net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService.class);
				var freeze = mockStatic(net.tfminecraft.simplefactions.war.freeze.PreparationFreeze.class)) {
			BattleScheduleService.postpone(war, java.time.Instant.EPOCH);
			assertFalse(battle.isStaffHold());
			battle.setStaffHold(true);
			BattleScheduleService.skipBattleDay(war, java.time.Instant.EPOCH);
			assertFalse(battle.isStaffHold());
			// A scheduled war keeps its old start time, so releasing the hold would start the battle.
			battle.setStaffHold(true);
			when(war.getBattleSchedulePhase()).thenReturn(net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase.SCHEDULED);
			BattleScheduleService.skipBattleDay(war, java.time.Instant.EPOCH);
			assertTrue(battle.isStaffHold());
		}
	}

	@Test
	void aRunningBattleKeepsItsState() {
		BattleManager.addBattle(battle);
		battle.setStaffHold(true);
		battle.setStarted(true);
		BattleScheduleService.releaseStaffHold(war);
		assertTrue(battle.isStaffHold());
	}
}
