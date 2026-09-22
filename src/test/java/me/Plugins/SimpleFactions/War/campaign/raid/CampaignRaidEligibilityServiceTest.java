package me.Plugins.SimpleFactions.War.campaign.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.ValidateLaunchResult;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.raid.RaidTargetService.RaidKind;
import me.Plugins.SimpleFactions.War.campaign.raid.RaidTargetService.RaidTargetCandidate;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class CampaignRaidEligibilityServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private Faction defender;
	private War war;
	private Instant raidWindow;
	private Installation atkPort;
	private Installation atkAirport;
	private Installation atkFort;
	private Installation defPort;
	private Installation defAirport;
	private Installation defFort;
	private InstallationHandler attackerHandler;
	private InstallationHandler defenderHandler;

	@BeforeEach
	void setUp() {
		Cache.warVoteCloseHour = 16;
		Cache.warRaidWindowStartHour = 19;
		Cache.warRaidWindowEndHour = 20;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");

		atkPort = new Installation("port-atk", "Atk Port", InstallationKind.PORT, 10, 0, 0, 0L);
		atkAirport = new Installation("airport-atk", "Atk Airport", InstallationKind.AIRPORT, 11, 0, 0, 0L);
		atkFort = new Installation("fort-atk", "Atk Fort", InstallationKind.FORT, 12, 0, 0, 0L);
		defPort = new Installation("port-def", "Def Port", InstallationKind.PORT, 20, 0, 0, 0L);
		defAirport = new Installation("airport-def", "Def Airport", InstallationKind.AIRPORT, 21, 0, 0, 0L);
		defFort = new Installation("fort-def", "Def Fort", InstallationKind.FORT, 22, 0, 0, 0L);

		attackerHandler = mock(InstallationHandler.class);
		defenderHandler = mock(InstallationHandler.class);
		when(attacker.getInstallationHandler()).thenReturn(attackerHandler);
		when(defender.getInstallationHandler()).thenReturn(defenderHandler);
		when(attackerHandler.getAll()).thenReturn(List.of(atkPort, atkAirport, atkFort));
		when(defenderHandler.getAll()).thenReturn(List.of(defPort, defAirport, defFort));
		stubHandlers();

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
	void inferRaidKind_matrix() {
		assertEquals(RaidKind.NAVAL, CampaignRaidEligibilityService.inferRaidKind(
				InstallationKind.PORT, InstallationKind.PORT));
		assertEquals(RaidKind.AIR, CampaignRaidEligibilityService.inferRaidKind(
				InstallationKind.AIRPORT, InstallationKind.AIRPORT));
		assertEquals(RaidKind.FORT, CampaignRaidEligibilityService.inferRaidKind(
				InstallationKind.PORT, InstallationKind.FORT));
		assertEquals(RaidKind.FORT, CampaignRaidEligibilityService.inferRaidKind(
				InstallationKind.AIRPORT, InstallationKind.FORT));
		assertNull(CampaignRaidEligibilityService.inferRaidKind(
				InstallationKind.PORT, InstallationKind.AIRPORT));
	}

	@Test
	void listValidSources_returnsPortAndAirportOnly() {
		List<Installation> sources = CampaignRaidEligibilityService.listValidSources(war, "atk", raidWindow);

		assertEquals(List.of("airport-atk", "port-atk"), sources.stream().map(Installation::getId).toList());
	}

	@Test
	void isValidSource_acceptsUncommittedPort() {
		assertTrue(CampaignRaidEligibilityService.isValidSource(war, "atk", "port-atk", raidWindow));
	}

	@Test
	void listValidTargets_fromPortListsEnemyPortAndFort() {
		List<RaidTargetCandidate> targets = CampaignRaidEligibilityService.listValidTargets(
				war, "atk", "port-atk", raidWindow);

		Set<String> ids = targets.stream().map(RaidTargetCandidate::installationId).collect(Collectors.toSet());
		assertEquals(Set.of("port-def", "fort-def"), ids);
		assertFalse(ids.contains("airport-def"));
	}

	@Test
	void isValidTarget_acceptsUncommittedEnemyFort() {
		assertTrue(CampaignRaidEligibilityService.isValidTarget(
				war, "atk", "port-atk", "fort-def", raidWindow));
	}

	@Test
	void validateLaunch_okInfersNavalRaidKind() {
		var outcome = CampaignRaidEligibilityService.validateLaunch(
				war, "atk", "port-atk", "port-def", raidWindow);

		assertEquals(ValidateLaunchResult.OK, outcome.result());
		assertEquals(RaidKind.NAVAL, outcome.raidKind());
	}

	@Test
	void validateLaunch_rejectsOffWindow() {
		Instant beforeWindow = BattleWindowService.atScheduleHour(BATTLE_DAY, 18);

		assertEquals(ValidateLaunchResult.REJECTED_OUTSIDE_WINDOW,
				CampaignRaidEligibilityService.validateLaunch(
						war, "atk", "port-atk", "port-def", beforeWindow).result());
	}

	@Test
	void validateLaunch_rejectsCrossKindPair() {
		assertEquals(ValidateLaunchResult.REJECTED_KIND_MISMATCH,
				CampaignRaidEligibilityService.validateLaunch(
						war, "atk", "port-atk", "airport-def", raidWindow).result());
	}

	@Test
	void validateLaunch_rejectsFriendlyTarget() {
		assertEquals(ValidateLaunchResult.REJECTED_INVALID_TARGET,
				CampaignRaidEligibilityService.validateLaunch(
						war, "atk", "port-atk", "port-atk", raidWindow).result());
	}

	private void stubHandlers() {
		when(attackerHandler.getById("port-atk")).thenReturn(atkPort);
		when(attackerHandler.getById("airport-atk")).thenReturn(atkAirport);
		when(attackerHandler.getById("fort-atk")).thenReturn(atkFort);
		when(defenderHandler.getById("port-def")).thenReturn(defPort);
		when(defenderHandler.getById("airport-def")).thenReturn(defAirport);
		when(defenderHandler.getById("fort-def")).thenReturn(defFort);
	}
}
