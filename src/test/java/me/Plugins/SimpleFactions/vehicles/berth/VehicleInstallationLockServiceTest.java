package me.Plugins.SimpleFactions.vehicles.berth;


import me.Plugins.SimpleFactions.vehicles.berth.VehicleInstallationLockService;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class VehicleInstallationLockServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);
	private static final Instant NOW = Instant.parse("2026-08-21T17:00:00Z");

	private Faction attacker;
	private Faction defender;
	private War war;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		Cache.campaignRaidRepairLockHours = 48;
		Cache.warVoteCloseHour = 16;
		Cache.worldName = "world";

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");

		Installation atkPort = new Installation("port-atk", "Atk Port", InstallationKind.PORT, 10, 0, 0, 0L);
		Installation atkIdle = new Installation("port-atk-idle", "Atk Idle", InstallationKind.PORT, 10, 0, 0, 0L);
		Installation defPort = new Installation("port-def", "Def Port", InstallationKind.PORT, 20, 100, 100, 0L);
		Installation fort = new Installation("fort-1", "Fort", InstallationKind.FORT, 18, 50, 50, 0L);
		mockInstallations(attacker, atkPort, atkIdle);
		mockInstallations(defender, defPort, fort);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setOccupiedByAttacker(new ArrayList<>());
		war.setOccupiedByDefender(new ArrayList<>());
		WarManager.addWar(war);
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.remove(attacker);
		FactionManager.factions.remove(defender);
		WarManager.get().clear();
		BattleManager.resetForTests();
	}

	@Test
	void sourceLocked_duringFightingOnly() {
		setActiveRaid(CampaignRaidState.MUSTER);
		assertFalse(VehicleInstallationLockService.isVehicleLocked("port-atk", NOW));

		setActiveRaid(CampaignRaidState.FIGHTING);
		assertTrue(VehicleInstallationLockService.isVehicleLocked("port-atk", NOW));
	}

	@Test
	void sourceUnlocked_afterRaidEnds() {
		setActiveRaid(CampaignRaidState.MUSTER);
		CampaignRaidService.endRaid(war, NOW.plusSeconds(30));

		assertFalse(VehicleInstallationLockService.isVehicleLocked("port-atk", NOW.plusSeconds(30)));
	}

	@Test
	void targetLocked_duringFight() {
		setActiveRaid(CampaignRaidState.FIGHTING);

		assertTrue(VehicleInstallationLockService.isVehicleLocked("port-def", NOW));
	}

	@Test
	void targetStillLocked_oneHourAfterFightStart() {
		Instant fightStart = NOW;
		CampaignRaidService.setRepairLockUntil(
				war, "port-def", CampaignRaidService.repairLockUntilFromStart(fightStart));
		setActiveRaid(CampaignRaidState.FIGHTING);
		CampaignRaidService.endRaid(war, fightStart.plusSeconds(10));

		assertTrue(VehicleInstallationLockService.isVehicleLocked(
				"port-def", fightStart.plus(1, ChronoUnit.HOURS)));
		assertFalse(VehicleInstallationLockService.isVehicleLocked(
				"port-atk", fightStart.plus(1, ChronoUnit.HOURS)));
	}

	@Test
	void targetUnlocked_after48Hours() {
		Instant fightStart = NOW;
		Instant until = CampaignRaidService.repairLockUntilFromStart(fightStart);
		CampaignRaidService.setRepairLockUntil(war, "port-def", until);

		assertFalse(VehicleInstallationLockService.isVehicleLocked("port-def", until));
		assertFalse(VehicleInstallationLockService.isVehicleLocked(
				"port-def", until.plusSeconds(1)));
	}

	@Test
	void campaignBattleInPlay_locksInstallation() {
		War siegeWar = siegeWar();
		WarManager.addWar(siegeWar);

		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(
					Mockito.anyString(),
					Mockito.any(BarColor.class),
					Mockito.any(BarStyle.class))).thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(
					Mockito.anyString(),
					Mockito.any(BarColor.class),
					Mockito.any(BarStyle.class),
					Mockito.any())).thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "campaign_w2_p18");
			battle.setWarId(2);
			battle.setProvinceId(18);
			battle.setStarted(true);
			BattleManager.addBattle(battle);

			assertTrue(VehicleInstallationLockService.isVehicleLocked("fort-1", NOW));
			assertFalse(VehicleInstallationLockService.isVehicleLocked("port-def", NOW));
		}
	}

	@Test
	void pickLock_committedPickUnlockedBeforeVoteClose() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			setPicks(war, "atk", "port-atk");
			Instant beforeLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 15);

			assertFalse(VehicleInstallationLockService.isVehicleLocked("port-atk", beforeLock));
			assertFalse(VehicleInstallationLockService.isVehicleLocked("port-atk-idle", beforeLock));
		}
	}

	@Test
	void pickLock_committedPickLockedAfterVoteClose_idlePortStaysOpen() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			setPicks(war, "atk", "port-atk");
			Instant afterLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 16);

			assertTrue(VehicleInstallationLockService.isVehicleLocked("port-atk", afterLock));
			assertFalse(VehicleInstallationLockService.isVehicleLocked("port-atk-idle", afterLock));
		}
	}

	@Test
	void pickLock_defenderZocPortLockedAfterVoteClose() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			war.setCampaignBattleSchedule(List.of(
					new ScheduledCampaignBattle(20, CampaignBattleKind.NAVAL, false, null, "port-def")));
			war.setCampaignScheduleIndex(0);
			Instant afterLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 16);

			assertTrue(VehicleInstallationLockService.isVehicleLocked("port-def", afterLock));
			assertFalse(VehicleInstallationLockService.isVehicleLocked("port-atk", afterLock));
		}
	}

	@Test
	void pickLock_siegeFortLockedAfterVoteClose_withoutPick() {
		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubProvinceOwnership(titleManager);
			War siege = siegeWar();
			siege.setBattleDay(BATTLE_DAY);
			siege.setOccupiedByAttacker(new ArrayList<>());
			siege.setOccupiedByDefender(new ArrayList<>());
			WarManager.addWar(siege);
			Instant afterLock = BattleWindowService.atScheduleHour(BATTLE_DAY, 16);

			assertTrue(VehicleInstallationLockService.isVehicleLocked("fort-1", afterLock));
			assertFalse(VehicleInstallationLockService.isVehicleLocked("port-def", afterLock));
		}
	}

	private void setActiveRaid(CampaignRaidState state) {
		CampaignRaid raid = new CampaignRaid();
		raid.setWarId(war.getId());
		raid.setBattleDay(BATTLE_DAY);
		raid.setSourceInstallationId("port-atk");
		raid.setTargetInstallationId("port-def");
		raid.setState(state);
		war.setActiveCampaignRaid(raid);
	}

	private War siegeWar() {
		War siegeWar = new War(2, attacker, defender);
		siegeWar.setGoal(WarGoalType.SUBJUGATE);
		siegeWar.setWarType(WarType.SUBJUGATE);
		siegeWar.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		siegeWar.setObjectiveProvinceId(30);
		siegeWar.setCampaignProvinces(List.of(5, 10, 20, 30));
		siegeWar.setCursorIndex(2);
		siegeWar.setInitiativeAttacker(4);
		siegeWar.setInitiativeDefender(4);
		siegeWar.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		siegeWar.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		siegeWar.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(18, CampaignBattleKind.SIEGE, false, "fort-1")));
		siegeWar.setCampaignScheduleIndex(0);
		return siegeWar;
	}

	private static void mockInstallations(Faction faction, Installation... installations) {
		InstallationHandler handler = mock(InstallationHandler.class);
		when(faction.getInstallationHandler()).thenReturn(handler);
		for (Installation installation : installations) {
			when(handler.getById(installation.getId())).thenReturn(installation);
		}
		when(handler.getAll()).thenReturn(List.of(installations));
	}

	private static void setPicks(War war, String factionId, String installationId) {
		LinkedHashSet<String> picks = new LinkedHashSet<>();
		picks.add(installationId);
		war.setBattleInstallationPicks(Map.of(factionId, picks));
		war.setBattleInstallationPicksBattleDay(BATTLE_DAY);
	}

	private void stubProvinceOwnership(MockedStatic<TitleManager> titleManager) {
		titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
		titleManager.when(() -> TitleManager.getByProvince(18)).thenReturn(defender);
		titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
	}
}
