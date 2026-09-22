package me.Plugins.SimpleFactions.War.campaign.runtime.pick;


import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationInPlayService;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class BattleInstallationInPlayServiceTest {
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
	void isInPlay_trueForCommittedPick() {
		War war = baseWar();
		setPicks(war, "atk", "port-1");

		assertTrue(BattleInstallationInPlayService.isInPlay(war, "atk", "port-1"));
	}

	@Test
	void isInPlay_trueForSiegeFortWithoutPick() {
		War war = siegeWar();
		mockFort(defender, "fort-1");

		assertTrue(BattleInstallationInPlayService.isInPlay(war, "def", "fort-1"));
	}

	@Test
	void isInPlay_falseWhenNeitherPickNorSiegeFort() {
		War war = baseWar();
		mockFort(defender, "fort-1");

		assertFalse(BattleInstallationInPlayService.isInPlay(war, "def", "fort-1"));
	}

	@Test
	void isInPlay_trueForCampaignRaidSourceAndTarget() {
		War war = baseWar();
		CampaignRaid raid = new CampaignRaid();
		raid.setAttackerCoalition(CampaignCoalition.AGGRESSOR);
		raid.setState(CampaignRaidState.FIGHTING);
		raid.setSourceInstallationId("airport-atk");
		raid.setTargetInstallationId("airport-def");
		war.setActiveCampaignRaid(raid);

		assertTrue(BattleInstallationInPlayService.isInPlay(war, "atk", "airport-atk"));
		assertTrue(BattleInstallationInPlayService.isInPlay(war, "def", "airport-def"));
		assertFalse(BattleInstallationInPlayService.isInPlay(war, "atk", "airport-def"));
		assertFalse(BattleInstallationInPlayService.isInPlay(war, "def", "airport-atk"));
	}

	@Test
	void isInPlay_ignoresCampaignRaidInstallationsDuringMuster() {
		War war = baseWar();
		CampaignRaid raid = new CampaignRaid();
		raid.setAttackerCoalition(CampaignCoalition.AGGRESSOR);
		raid.setState(CampaignRaidState.MUSTER);
		raid.setSourceInstallationId("airport-atk");
		raid.setTargetInstallationId("airport-def");
		war.setActiveCampaignRaid(raid);

		assertFalse(BattleInstallationInPlayService.isInPlay(war, "atk", "airport-atk"));
	}

	private War baseWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		return war;
	}

	private War siegeWar() {
		War war = baseWar();
		war.setObjectiveProvinceId(30);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(18, CampaignBattleKind.SIEGE, false, "fort-1")));
		war.setCampaignScheduleIndex(0);
		return war;
	}

	private static void setPicks(War war, String factionId, String installationId) {
		LinkedHashSet<String> picks = new LinkedHashSet<>();
		picks.add(installationId);
		war.setBattleInstallationPicks(Map.of(factionId, picks));
	}

	private static void mockFort(Faction faction, String fortId) {
		Installation fort = new Installation(fortId, "Fort", InstallationKind.FORT, 18, 0, 0, 0L);
		InstallationHandler handler = mock(InstallationHandler.class);
		when(faction.getInstallationHandler()).thenReturn(handler);
		when(handler.getById(fortId)).thenReturn(fort);
	}
}
