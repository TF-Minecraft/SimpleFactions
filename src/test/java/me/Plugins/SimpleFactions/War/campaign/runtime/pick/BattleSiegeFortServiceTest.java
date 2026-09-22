package me.Plugins.SimpleFactions.War.campaign.runtime.pick;


import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleSiegeFortService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class BattleSiegeFortServiceTest {
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.remove(attacker);
		FactionManager.factions.remove(defender);
	}

	@Test
	void currentSiegeFortInstallationId_emptyWhenFieldSlot() {
		War war = warWithSchedule(field(20), siege(18, "fort_a"));
		assertTrue(BattleSiegeFortService.currentSiegeFortInstallationId(war).isEmpty());
	}

	@Test
	void currentSiegeFortInstallationId_returnsFortOnSiegeSlot() {
		War war = warWithSchedule(field(20), siege(18, "fort_a"));
		war.setCampaignScheduleIndex(1);

		assertEquals(Optional.of("fort_a"), BattleSiegeFortService.currentSiegeFortInstallationId(war));
	}

	@Test
	void isSiegeFortInPlayForFaction_trueForOwner() {
		War war = warWithSchedule(siege(18, "fort_a"));
		mockFort(defender, "fort_a");

		assertTrue(BattleSiegeFortService.isSiegeFortInPlayForFaction(war, "def", "fort_a"));
	}

	@Test
	void isSiegeFortInPlayForFaction_falseForWrongFaction() {
		War war = warWithSchedule(siege(18, "fort_a"));
		mockFort(defender, "fort_a");

		assertFalse(BattleSiegeFortService.isSiegeFortInPlayForFaction(war, "atk", "fort_a"));
	}

	@Test
	void isSiegeFortInPlay_falseForWrongInstallation() {
		War war = warWithSchedule(siege(18, "fort_a"));
		mockFort(defender, "fort_a");

		assertFalse(BattleSiegeFortService.isSiegeFortInPlay(war, "fort_b"));
	}

	@Test
	void currentSiegeFortInstallationId_usesCounterScheduleLeg() {
		War war = warWithSchedule(field(20));
		war.setCampaignCounterSchedule(List.of(siege(5, "fort_counter")));
		war.setCampaignCounterScheduleIndex(0);
		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
		war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);
		mockFort(defender, "fort_counter");

		assertEquals(Optional.of("fort_counter"), BattleSiegeFortService.currentSiegeFortInstallationId(war));
	}

	private War warWithSchedule(ScheduledCampaignBattle... slots) {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setCampaignBattleSchedule(List.of(slots));
		war.setCampaignScheduleIndex(0);
		return war;
	}

	private static ScheduledCampaignBattle field(int provinceId) {
		return new ScheduledCampaignBattle(provinceId, CampaignBattleKind.FIELD, false, null);
	}

	private static ScheduledCampaignBattle siege(int provinceId, String fortId) {
		return new ScheduledCampaignBattle(provinceId, CampaignBattleKind.SIEGE, false, fortId);
	}

	private static void mockFort(Faction faction, String fortId) {
		Installation fort = new Installation(fortId, "Fort", InstallationKind.FORT, 18, 0, 0, 0L);
		InstallationHandler handler = mock(InstallationHandler.class);
		when(faction.getInstallationHandler()).thenReturn(handler);
		when(handler.getById(fortId)).thenReturn(fort);
	}
}
