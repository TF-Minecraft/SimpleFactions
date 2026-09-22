package me.Plugins.SimpleFactions.War.campaign.runtime.pick;



import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationPickService;
import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationPickService.InstallationPickResults;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationPickService.InstallationPickResults.InstallationPickToggleResult;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class BattleInstallationPickServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);
	private static final int ATTACKER_PROVINCE = 10;
	private static final int DEFENDER_PROVINCE = 20;

	private Faction attacker;
	private Faction defender;
	private final Map<Faction, Map<String, Installation>> installationsByFaction = new HashMap<>();

	@BeforeEach
	void setUp() {
		Cache.warVoteCloseHour = 16;
		installationsByFaction.clear();

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.isLeader("leader")).thenReturn(true);
		when(attacker.isLeader("member")).thenReturn(false);
		when(defender.isLeader("leader")).thenReturn(true);

		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.remove(attacker);
		FactionManager.factions.remove(defender);
	}

	@Test
	void togglePick_addsAndRemovesInstallation() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = votingWar();
			mockInstallation(attacker, "port-1", InstallationKind.PORT, ATTACKER_PROVINCE);

			Instant beforeLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 15);

			assertEquals(
					InstallationPickToggleResult.ADDED,
					BattleInstallationPickService.togglePick(war, attacker, "leader", "port-1", beforeLock));
			assertEquals(Set.of("port-1"), BattleInstallationPickService.getPicks(war, "atk"));
			assertEquals(BATTLE_DAY, war.getBattleInstallationPicksBattleDay());

			assertEquals(
					InstallationPickToggleResult.REMOVED,
					BattleInstallationPickService.togglePick(war, attacker, "leader", "port-1", beforeLock));
			assertTrue(BattleInstallationPickService.getPicks(war, "atk").isEmpty());
			assertEquals(null, war.getBattleInstallationPicksBattleDay());
		}
	}

	@Test
	void togglePick_rejectsFort() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = votingWar();
			mockInstallation(attacker, "fort-1", InstallationKind.FORT, ATTACKER_PROVINCE);

			assertEquals(
					InstallationPickToggleResult.REJECTED_INVALID_INSTALLATION,
					BattleInstallationPickService.togglePick(
							war, attacker, "leader", "fort-1", BattleWindowService.atScheduleHour(BATTLE_DAY, 15)));
		}
	}

	@Test
	void togglePick_rejectsOccupiedProvince() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = votingWar();
			war.setOccupiedByDefender(new ArrayList<>(List.of(ATTACKER_PROVINCE)));
			mockInstallation(attacker, "port-1", InstallationKind.PORT, ATTACKER_PROVINCE);

			assertEquals(
					InstallationPickToggleResult.REJECTED_INVALID_INSTALLATION,
					BattleInstallationPickService.togglePick(
							war, attacker, "leader", "port-1", BattleWindowService.atScheduleHour(BATTLE_DAY, 15)));
		}
	}

	@Test
	void togglePick_allowsRemovingLegacyFortPick() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = votingWar();
			mockInstallation(attacker, "fort-1", InstallationKind.FORT, ATTACKER_PROVINCE);
			war.getBattleInstallationPicks().put("atk", new LinkedHashSet<>(Set.of("fort-1")));
			war.setBattleInstallationPicksBattleDay(BATTLE_DAY);

			assertEquals(
					InstallationPickToggleResult.REMOVED,
					BattleInstallationPickService.togglePick(
							war, attacker, "leader", "fort-1", BattleWindowService.atScheduleHour(BATTLE_DAY, 15)));
			assertTrue(BattleInstallationPickService.getPicks(war, "atk").isEmpty());
		}
	}

	@Test
	void togglePick_rejectsNonLeader() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = votingWar();
			mockInstallation(attacker, "port-1", InstallationKind.PORT, ATTACKER_PROVINCE);

			assertEquals(
					InstallationPickToggleResult.REJECTED_NOT_LEADER,
					BattleInstallationPickService.togglePick(
							war, attacker, "member", "port-1", BattleWindowService.atScheduleHour(BATTLE_DAY, 15)));
		}
	}

	@Test
	void togglePick_rejectsNonParticipant() {
		War war = votingWar();
		Faction outsider = mock(Faction.class);
		when(outsider.getId()).thenReturn("other");
		when(outsider.isLeader("leader")).thenReturn(true);
		mockInstallation(outsider, "port-1", InstallationKind.PORT, ATTACKER_PROVINCE);

		assertEquals(
				InstallationPickToggleResult.REJECTED_NOT_PARTICIPANT,
				BattleInstallationPickService.togglePick(
						war, outsider, "leader", "port-1", BattleWindowService.atScheduleHour(BATTLE_DAY, 15)));
	}

	@Test
	void togglePick_rejectsWhenLocked() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = votingWar();
			mockInstallation(attacker, "port-1", InstallationKind.PORT, ATTACKER_PROVINCE);

			assertEquals(
					InstallationPickToggleResult.REJECTED_LOCKED,
					BattleInstallationPickService.togglePick(
							war, attacker, "leader", "port-1", BattleWindowService.atScheduleHour(BATTLE_DAY, 16)));
		}
	}

	@Test
	void togglePick_rejectsUnknownInstallation() {
		War war = votingWar();
		InstallationHandler handler = mock(InstallationHandler.class);
		when(attacker.getInstallationHandler()).thenReturn(handler);
		when(handler.getById("missing")).thenReturn(null);

		assertEquals(
				InstallationPickToggleResult.REJECTED_INVALID_INSTALLATION,
				BattleInstallationPickService.togglePick(
						war, attacker, "leader", "missing", BattleWindowService.atScheduleHour(BATTLE_DAY, 15)));
	}

	@Test
	void getAllPicks_returnsUnmodifiableCopyForCurrentDay() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = votingWar();
			mockInstallation(attacker, "port-1", InstallationKind.PORT, ATTACKER_PROVINCE);
			mockInstallation(defender, "port-2", InstallationKind.PORT, DEFENDER_PROVINCE);

			Instant beforeLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 15);
			BattleInstallationPickService.togglePick(war, attacker, "leader", "port-1", beforeLock);
			BattleInstallationPickService.togglePick(war, defender, "leader", "port-2", beforeLock);

			Map<String, Set<String>> all = BattleInstallationPickService.getAllPicks(war);
			assertEquals(Set.of("port-1"), all.get("atk"));
			assertEquals(Set.of("port-2"), all.get("def"));
			assertFalse(all.isEmpty());
		}
	}

	@Test
	void clearForNewBattleDay_emptiesPicks() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = votingWar();
			mockInstallation(attacker, "port-1", InstallationKind.PORT, ATTACKER_PROVINCE);
			BattleInstallationPickService.togglePick(
					war, attacker, "leader", "port-1", BattleWindowService.atScheduleHour(BATTLE_DAY, 15));

			BattleInstallationPickService.clearForNewBattleDay(war);

			assertTrue(war.getBattleInstallationPicks().isEmpty());
			assertEquals(null, war.getBattleInstallationPicksBattleDay());
			assertTrue(BattleInstallationPickService.getAllPicks(war).isEmpty());
		}
	}

	@Test
	void syncBattleDay_clearsStalePicksWhenBattleDayChanges() {
		War war = votingWar();
		LinkedHashSet<String> picks = new LinkedHashSet<>();
		picks.add("port-1");
		war.getBattleInstallationPicks().put("atk", picks);
		war.setBattleInstallationPicksBattleDay(BATTLE_DAY.minusDays(1));

		BattleInstallationPickService.getPicks(war, "atk");

		assertTrue(war.getBattleInstallationPicks().isEmpty());
		assertEquals(null, war.getBattleInstallationPicksBattleDay());
	}

	@Test
	void syncBattleDay_prunesIneligibleFortPicks() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = votingWar();
			mockInstallation(attacker, "fort-1", InstallationKind.FORT, ATTACKER_PROVINCE);
			war.getBattleInstallationPicks().put("atk", new LinkedHashSet<>(Set.of("fort-1")));
			war.setBattleInstallationPicksBattleDay(BATTLE_DAY);

			assertTrue(BattleInstallationPickService.getPicks(war, "atk").isEmpty());
		}
	}

	@Test
	void isLocked_trueAtVoteCloseHour() {
		War war = votingWar();
		assertFalse(BattleInstallationPickService.isLocked(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 15)));
		assertTrue(BattleInstallationPickService.isLocked(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 16)));
	}

	@Test
	void getVisibleEnemyPicks_emptyBeforeLock() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = votingWar();
			mockInstallation(attacker, "port-1", InstallationKind.PORT, ATTACKER_PROVINCE);
			Instant beforeLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 15);
			BattleInstallationPickService.togglePick(war, attacker, "leader", "port-1", beforeLock);

			assertTrue(BattleInstallationPickService.getVisibleEnemyPicks(war, "def", beforeLock).isEmpty());
		}
	}

	@Test
	void getVisibleEnemyPicks_returnsEnemyCommitsAfterLock() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = votingWar();
			mockInstallation(defender, "port-1", InstallationKind.PORT, DEFENDER_PROVINCE);
			Instant beforeLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 15);
			Instant afterLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 16);
			BattleInstallationPickService.togglePick(war, defender, "leader", "port-1", beforeLock);

			Map<String, Set<String>> enemyPicks = BattleInstallationPickService.getVisibleEnemyPicks(war, "atk", afterLock);

			assertEquals(Set.of("port-1"), enemyPicks.get("def"));
			assertTrue(enemyPicks.containsKey("def"));
		}
	}

	@Test
	void getVisibleEnemyPicks_excludesOwnAndAllies() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = votingWar();
			mockInstallation(attacker, "port-1", InstallationKind.PORT, ATTACKER_PROVINCE);
			mockInstallation(defender, "port-2", InstallationKind.PORT, DEFENDER_PROVINCE);
			Instant beforeLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 15);
			Instant afterLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 16);
			BattleInstallationPickService.togglePick(war, attacker, "leader", "port-1", beforeLock);
			BattleInstallationPickService.togglePick(war, defender, "leader", "port-2", beforeLock);

			Map<String, Set<String>> attackerView = BattleInstallationPickService.getVisibleEnemyPicks(war, "atk", afterLock);
			Map<String, Set<String>> defenderView = BattleInstallationPickService.getVisibleEnemyPicks(war, "def", afterLock);

			assertFalse(attackerView.containsKey("atk"));
			assertEquals(Set.of("port-2"), attackerView.get("def"));
			assertFalse(defenderView.containsKey("def"));
			assertEquals(Set.of("port-1"), defenderView.get("atk"));
		}
	}

	@Test
	void getVisibleEnemyPicks_includesEmptyEnemyFactionPostLock() {
		War war = votingWar();
		Instant afterLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 16);

		Map<String, Set<String>> enemyPicks = BattleInstallationPickService.getVisibleEnemyPicks(war, "atk", afterLock);

		assertTrue(enemyPicks.containsKey("def"));
		assertTrue(enemyPicks.get("def").isEmpty());
	}

	@Test
	void getPicks_visibleToOwnFactionRegardlessOfLock() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = votingWar();
			mockInstallation(attacker, "port-1", InstallationKind.PORT, ATTACKER_PROVINCE);
			Instant beforeLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 15);
			Instant afterLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 16);
			BattleInstallationPickService.togglePick(war, attacker, "leader", "port-1", beforeLock);

			assertEquals(Set.of("port-1"), BattleInstallationPickService.getPicks(war, "atk"));
			assertTrue(BattleInstallationPickService.getVisibleEnemyPicks(war, "atk", beforeLock).isEmpty());
			assertTrue(BattleInstallationPickService.getVisibleEnemyPicks(war, "atk", afterLock).containsKey("def"));
		}
	}

	@Test
	void getPicks_seedsDefenderZocPortForNavalSlot() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = navalVotingWar("zoc-port");
			mockInstallation(defender, "zoc-port", InstallationKind.PORT, DEFENDER_PROVINCE);

			assertEquals(Set.of("zoc-port"), BattleInstallationPickService.getPicks(war, "def"));
			assertEquals(BATTLE_DAY, war.getBattleInstallationPicksBattleDay());
		}
	}

	@Test
	void togglePick_rejectsUnpickingDefenderZocPort() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = navalVotingWar("zoc-port");
			mockInstallation(defender, "zoc-port", InstallationKind.PORT, DEFENDER_PROVINCE);
			Instant beforeLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 15);

			assertEquals(Set.of("zoc-port"), BattleInstallationPickService.getPicks(war, "def"));
			assertEquals(
					InstallationPickToggleResult.REJECTED_ZOC_PORT,
					BattleInstallationPickService.togglePick(war, defender, "leader", "zoc-port", beforeLock));
			assertEquals(Set.of("zoc-port"), BattleInstallationPickService.getPicks(war, "def"));
		}
	}

	@Test
	void togglePick_defenderCanToggleOtherPortBesideZoc() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = navalVotingWar("zoc-port");
			mockInstallation(defender, "zoc-port", InstallationKind.PORT, DEFENDER_PROVINCE);
			mockInstallation(defender, "port-extra", InstallationKind.PORT, DEFENDER_PROVINCE);
			Instant beforeLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 15);

			assertEquals(
					InstallationPickToggleResult.ADDED,
					BattleInstallationPickService.togglePick(war, defender, "leader", "port-extra", beforeLock));
			assertEquals(Set.of("zoc-port", "port-extra"), BattleInstallationPickService.getPicks(war, "def"));
			assertEquals(
					InstallationPickToggleResult.REMOVED,
					BattleInstallationPickService.togglePick(war, defender, "leader", "port-extra", beforeLock));
			assertEquals(Set.of("zoc-port"), BattleInstallationPickService.getPicks(war, "def"));
		}
	}

	@Test
	void togglePick_attackerCanStillToggleOwnPortOnNavalDay() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = navalVotingWar("zoc-port");
			mockInstallation(defender, "zoc-port", InstallationKind.PORT, DEFENDER_PROVINCE);
			mockInstallation(attacker, "port-1", InstallationKind.PORT, ATTACKER_PROVINCE);
			Instant beforeLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 15);

			assertEquals(
					InstallationPickToggleResult.ADDED,
					BattleInstallationPickService.togglePick(war, attacker, "leader", "port-1", beforeLock));
			assertEquals(Set.of("port-1"), BattleInstallationPickService.getPicks(war, "atk"));
			assertEquals(
					InstallationPickToggleResult.REMOVED,
					BattleInstallationPickService.togglePick(war, attacker, "leader", "port-1", beforeLock));
			assertTrue(BattleInstallationPickService.getPicks(war, "atk").isEmpty());
		}
	}

	@Test
	void clearForNewBattleDay_reseedsDefenderZocPort() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War war = navalVotingWar("zoc-port");
			mockInstallation(defender, "zoc-port", InstallationKind.PORT, DEFENDER_PROVINCE);
			mockInstallation(defender, "port-extra", InstallationKind.PORT, DEFENDER_PROVINCE);
			Instant beforeLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 15);
			BattleInstallationPickService.togglePick(war, defender, "leader", "port-extra", beforeLock);

			BattleInstallationPickService.clearForNewBattleDay(war);

			assertEquals(Set.of("zoc-port"), BattleInstallationPickService.getPicks(war, "def"));
			assertEquals(BATTLE_DAY, war.getBattleInstallationPicksBattleDay());
		}
	}

	private War votingWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setOccupiedByAttacker(new ArrayList<>());
		war.setOccupiedByDefender(new ArrayList<>());
		return war;
	}

	private War navalVotingWar(String zocPortId) {
		War war = votingWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(DEFENDER_PROVINCE, CampaignBattleKind.NAVAL, false, null, zocPortId)));
		war.setCampaignScheduleIndex(0);
		return war;
	}

	private void mockInstallation(
			Faction faction,
			String installationId,
			InstallationKind kind,
			int province) {
		Installation installation = new Installation(
				installationId,
				"Test",
				kind,
				province,
				0,
				0,
				0L);
		Map<String, Installation> byId = installationsByFaction.computeIfAbsent(faction, ignored -> {
			Map<String, Installation> map = new HashMap<>();
			InstallationHandler handler = mock(InstallationHandler.class);
			when(faction.getInstallationHandler()).thenReturn(handler);
			when(handler.getById(anyString())).thenAnswer(invocation -> map.get(invocation.getArgument(0)));
			return map;
		});
		byId.put(installationId, installation);
	}

	private void stubProvinceOwnership(MockedStatic<TitleManager> titleManager) {
		titleManager.when(() -> TitleManager.getByProvince(ATTACKER_PROVINCE)).thenReturn(attacker);
		titleManager.when(() -> TitleManager.getByProvince(DEFENDER_PROVINCE)).thenReturn(defender);
	}
}
