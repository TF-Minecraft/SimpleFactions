package me.Plugins.SimpleFactions.War.campaign.zoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class FortControlServiceTest {
	private Faction attacker;
	private Faction defender;
	private Faction neutral;
	private War war;

	@BeforeEach
	void setUp() {
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		neutral = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(neutral.getId()).thenReturn("neutral");

		war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.remove(neutral);
		FactionManager.factions.remove(defender);
		FactionManager.factions.remove(attacker);
	}

	@Test
	void initializeAtDeclare_mapsOwnersToCoalitions() {
		InstallationHandler defenderInstallations = mock(InstallationHandler.class);
		InstallationHandler attackerInstallations = mock(InstallationHandler.class);
		InstallationHandler neutralInstallations = mock(InstallationHandler.class);
		when(defender.getInstallationHandler()).thenReturn(defenderInstallations);
		when(attacker.getInstallationHandler()).thenReturn(attackerInstallations);
		when(neutral.getInstallationHandler()).thenReturn(neutralInstallations);

		Installation defenderFort = new Installation(
				"fort_def",
				"Fort",
				InstallationKind.FORT,
				20,
				0,
				0,
				100L);
		Installation attackerFort = new Installation(
				"fort_atk",
				"Fort",
				InstallationKind.FORT,
				10,
				0,
				0,
				200L);
		Installation neutralFort = new Installation(
				"fort_neutral",
				"Fort",
				InstallationKind.FORT,
				30,
				0,
				0,
				300L);
		when(defenderInstallations.getAll()).thenReturn(List.of(defenderFort));
		when(attackerInstallations.getAll()).thenReturn(List.of(attackerFort));
		when(neutralInstallations.getAll()).thenReturn(List.of(neutralFort));

		FactionManager.factions.add(defender);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(neutral);

		FortControlService.initializeAtDeclare(war);

		assertEquals(CampaignCoalition.DEFENDER, war.getFortControllers().get("fort_def"));
		assertEquals(CampaignCoalition.AGGRESSOR, war.getFortControllers().get("fort_atk"));
		assertFalse(war.getFortControllers().containsKey("fort_neutral"));
	}

	@Test
	void setController_updatesEntry() {
		FortControlService.setController(war, "fort_a", CampaignCoalition.DEFENDER);
		assertEquals(CampaignCoalition.DEFENDER, FortControlService.controller(war, "fort_a").orElseThrow());

		FortControlService.setController(war, "fort_a", CampaignCoalition.AGGRESSOR);
		assertEquals(CampaignCoalition.AGGRESSOR, FortControlService.controller(war, "fort_a").orElseThrow());
	}

	@Test
	void isEnemyControlled_trueWhenControllerDiffersFromAdvancing() {
		FortControlService.setController(war, "fort_a", CampaignCoalition.DEFENDER);

		assertTrue(FortControlService.isEnemyControlled(war, "fort_a", CampaignCoalition.AGGRESSOR));
		assertFalse(FortControlService.isEnemyControlled(war, "fort_a", CampaignCoalition.DEFENDER));
	}

	@Test
	void isEnemyControlled_falseWhenNoController() {
		assertFalse(FortControlService.isEnemyControlled(war, "missing", CampaignCoalition.AGGRESSOR));
	}
}
