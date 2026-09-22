package me.Plugins.SimpleFactions.War.campaign.raid;


import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidMusterScheduler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.JoinResult;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.LaunchResult;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class CampaignRaidJoinServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);
	private static final String ATTACKER_NAME = "Alice";
	private static final String DEFENDER_NAME = "Carol";
	private static final UUID ATTACKER_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID DEFENDER_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private Faction attacker;
	private Faction defender;
	private War war;
	private Instant raidWindow;
	private String raidId;

	@BeforeEach
	void setUp() {
		Cache.warVoteCloseHour = 16;
		Cache.warRaidWindowStartHour = 19;
		Cache.warRaidWindowEndHour = 20;
		Cache.campaignRaidMusterSeconds = 60;
		WarbandManager.resetForTests();
		CampaignRaidMusterScheduler.resetForTests();

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		when(attacker.getLeader()).thenReturn(ATTACKER_NAME);
		when(defender.getLeader()).thenReturn(DEFENDER_NAME);

		Installation atkPort = new Installation("port-atk", "Atk Port", InstallationKind.PORT, 10, 0, 0, 0L);
		Installation defPort = new Installation("port-def", "Def Port", InstallationKind.PORT, 20, 0, 0, 0L);
		InstallationHandler attackerHandler = mock(InstallationHandler.class);
		InstallationHandler defenderHandler = mock(InstallationHandler.class);
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
		WarManager.addWar(war);

		raidWindow = BattleWindowService.atScheduleHour(BATTLE_DAY, 19);
		CampaignRaidService.beginMuster(war, attacker, "port-atk", "port-def", raidWindow);
		raidId = CampaignRaidService.getActive(war).getId();
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.remove(attacker);
		FactionManager.factions.remove(defender);
		WarManager.get().clear();
		WarbandManager.resetForTests();
		CampaignRaidMusterScheduler.resetForTests();
	}

	@Test
	void join_okDuringMuster() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(ATTACKER_PLAYER)).thenReturn(null);

			JoinResult result = CampaignRaidJoinService.join(
					war, ATTACKER_PLAYER, ATTACKER_NAME, attacker, raidId, raidWindow.plusSeconds(10));
			assertEquals(JoinResult.OK, result);
			assertTrue(CampaignRaidService.getActive(war).getMusterParticipantIds().contains(ATTACKER_PLAYER.toString()));
			Warband atk = CampaignRaidWarbandService.getAttackerWarband(CampaignRaidService.getActive(war));
			assertNotNull(atk);
			assertTrue(atk.hasMember(ATTACKER_PLAYER));
		}
	}

	@Test
	void join_rejectsMountedPlayer() {
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.isInsideVehicle()).thenReturn(true);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(ATTACKER_PLAYER)).thenReturn(player);

			assertEquals(
					JoinResult.REJECTED_MOUNTED_ON_VEHICLE,
					CampaignRaidJoinService.join(
							war, ATTACKER_PLAYER, ATTACKER_NAME, attacker, raidId, raidWindow.plusSeconds(10)));
		}
	}

	@Test
	void join_rejectsWarbandMember() {
		Warband warband = Warband.createWithMemberIds("test_band", ATTACKER_PLAYER, true);
		WarbandManager.addWarband(warband);

		assertEquals(
				JoinResult.REJECTED_IN_WARBAND,
				CampaignRaidJoinService.join(
						war, ATTACKER_PLAYER, ATTACKER_NAME, attacker, raidId, raidWindow.plusSeconds(10)));
	}

	@Test
	void join_rejectsAfterMusterEnds() {
		CampaignRaidService.transitionToFighting(war, raidWindow.plusSeconds(60));

		assertEquals(
				JoinResult.REJECTED_NOT_MUSTER,
				CampaignRaidJoinService.join(
						war, ATTACKER_PLAYER, ATTACKER_NAME, attacker, raidId, raidWindow.plusSeconds(70)));
	}

	@Test
	void join_rejectsDefenderCoalition() {
		assertEquals(
				JoinResult.REJECTED_NOT_ATTACKER_COALITION,
				CampaignRaidJoinService.join(
						war, DEFENDER_PLAYER, DEFENDER_NAME, defender, raidId, raidWindow.plusSeconds(10)));
	}

	@Test
	void join_rejectsWrongRaidId() {
		assertEquals(
				JoinResult.REJECTED_RAID_NOT_FOUND,
				CampaignRaidJoinService.join(
						war, ATTACKER_PLAYER, ATTACKER_NAME, attacker, "cr_99_2099-01-01", raidWindow));
	}

	@Test
	void findWarByRaidId_returnsWar() {
		assertEquals(war, CampaignRaidJoinService.findWarByRaidId(raidId));
	}

	@Test
	void listJoinableRaidIds_attackerOnly() {
		assertEquals(1, CampaignRaidJoinService.listJoinableRaidIds(attacker).size());
		assertTrue(CampaignRaidJoinService.listJoinableRaidIds(attacker).contains(raidId));
		assertTrue(CampaignRaidJoinService.listJoinableRaidIds(defender).isEmpty());
	}
}
