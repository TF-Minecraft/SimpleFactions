package me.Plugins.SimpleFactions.War.campaign.raid;



import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.raid.RaidTargetService;
import me.Plugins.SimpleFactions.War.campaign.raid.RaidTargetService.RaidKind;
import me.Plugins.SimpleFactions.War.campaign.raid.RaidTargetService.RaidTargetCandidate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class RaidTargetServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private Faction defender;
	private War war;
	private Installation port;
	private Installation fort;
	private Installation airport;
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

		port = new Installation("port-1", "Harbour", InstallationKind.PORT, 10, 0, 0, 0L);
		fort = new Installation("fort-1", "North Fort", InstallationKind.FORT, 11, 0, 0, 0L);
		airport = new Installation("airport-1", "Airfield", InstallationKind.AIRPORT, 12, 0, 0, 0L);

		defenderHandler = mock(InstallationHandler.class);
		when(defender.getInstallationHandler()).thenReturn(defenderHandler);
		when(defenderHandler.getById("port-1")).thenReturn(port);
		when(defenderHandler.getById("fort-1")).thenReturn(fort);
		when(defenderHandler.getById("airport-1")).thenReturn(airport);
		when(defenderHandler.getAll()).thenReturn(List.of(port, fort, airport));

		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setOccupiedByAttacker(new ArrayList<>());
		war.setOccupiedByDefender(new ArrayList<>());
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.remove(attacker);
		FactionManager.factions.remove(defender);
	}

	@Test
	void isValidTarget_uncommittedPort_ok() {
		Instant raidWindow = BattleWindowService.atScheduleHour(BATTLE_DAY, 19);

		assertTrue(RaidTargetService.isValidTarget(war, "atk", "port-1", RaidKind.NAVAL, raidWindow));
	}

	@Test
	void isValidTarget_uncommittedFort_okForFortRaid() {
		Instant raidWindow = BattleWindowService.atScheduleHour(BATTLE_DAY, 19);

		assertTrue(RaidTargetService.isValidTarget(war, "atk", "fort-1", RaidKind.FORT, raidWindow));
	}

	@Test
	void isValidTarget_wrongKind_rejected() {
		Instant raidWindow = BattleWindowService.atScheduleHour(BATTLE_DAY, 19);

		assertFalse(RaidTargetService.isValidTarget(war, "atk", "port-1", RaidKind.FORT, raidWindow));
	}

	@Test
	void isValidTarget_outsideRaidWindow_rejected() {
		Instant beforeRaidWindow = BattleWindowService.atScheduleHour(BATTLE_DAY, 15);

		assertFalse(RaidTargetService.isValidTarget(war, "atk", "port-1", RaidKind.NAVAL, beforeRaidWindow));
	}

	@Test
	void listValidTargets_returnsAllEnemyOperationalForKind() {
		Instant raidWindow = BattleWindowService.atScheduleHour(BATTLE_DAY, 19);

		List<RaidTargetCandidate> navalTargets = RaidTargetService.listValidTargets(
				war, "atk", RaidKind.NAVAL, raidWindow);
		Set<String> navalIds = navalTargets.stream()
				.map(RaidTargetCandidate::installationId)
				.collect(Collectors.toSet());
		assertEquals(Set.of("port-1"), navalIds);

		List<RaidTargetCandidate> fortTargets = RaidTargetService.listValidTargets(
				war, "atk", RaidKind.FORT, raidWindow);
		Set<String> fortIds = fortTargets.stream()
				.map(RaidTargetCandidate::installationId)
				.collect(Collectors.toSet());
		assertEquals(Set.of("fort-1"), fortIds);
	}
}
