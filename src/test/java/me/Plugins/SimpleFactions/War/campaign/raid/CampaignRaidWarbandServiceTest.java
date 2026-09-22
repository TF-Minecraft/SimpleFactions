package me.Plugins.SimpleFactions.War.campaign.raid;


import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidMusterScheduler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class CampaignRaidWarbandServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);
	private static final UUID ALICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID BOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID CAROL_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

	private Faction attacker;
	private Faction defender;
	private War war;
	private CampaignRaid raid;
	private Instant raidWindow;

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
		when(attacker.getLeader()).thenReturn("Alice");
		when(defender.getLeader()).thenReturn("Carol");

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
		raid = CampaignRaidService.getActive(war);
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
	void beginMuster_createsAttackerWarbandOnly() {
		assertNotNull(CampaignRaidWarbandService.getAttackerWarband(raid));
		assertNull(CampaignRaidWarbandService.getDefenderWarband(raid));
		assertEquals(1, WarbandManager.get().size());
	}

	@Test
	void createRaidWarbands_producesCorrectIdsAndIsIdempotent() {
		assertEquals("def_port_raid_attacker", CampaignRaidWarbandService.attackerWarbandId(raid));
		assertEquals("def_port_raid_defender", CampaignRaidWarbandService.defenderWarbandId(raid));

		Warband atk = CampaignRaidWarbandService.getAttackerWarband(raid);
		assertNotNull(atk);
		assertTrue(CampaignRaidWarbandService.isRaidWarband(atk));
		assertTrue(atk.isPendingLeader());
		assertTrue(atk.isLocked());
		assertTrue(atk.isFaction());
		assertEquals(BattleTemplate.ATTACKER_SIDE, atk.getCampaignSideId());
		assertEquals("The Attacker Host", atk.getName());

		CampaignRaidWarbandService.createRaidWarbands(war, raid);
		Warband def = CampaignRaidWarbandService.getDefenderWarband(raid);
		assertNotNull(def);
		assertEquals(BattleTemplate.DEFENDER_SIDE, def.getCampaignSideId());
		assertEquals(2, WarbandManager.get().size());
	}

	@Test
	void signupAttacker_addsMemberAndPromotesWarLeader() {
		CampaignRaidWarbandService.signupAttacker(war, raid, BOB_ID, "Bob");
		Warband atk = CampaignRaidWarbandService.getAttackerWarband(raid);
		assertTrue(atk.hasMember(BOB_ID));
		assertEquals(BOB_ID, atk.getLeaderId());

		CampaignRaidWarbandService.signupAttacker(war, raid, ALICE_ID, "Alice");
		assertTrue(atk.hasMember(ALICE_ID));
		assertEquals(ALICE_ID, atk.getLeaderId());
	}

	@Test
	void enrollOnlineDefenders_addsWarbandFreeDefenders() {
		when(defender.getMembers()).thenReturn(List.of("Carol", "Dave"));

		Player carol = mock(Player.class);
		when(carol.getUniqueId()).thenReturn(CAROL_ID);
		when(carol.getName()).thenReturn("Carol");
		when(carol.isOnline()).thenReturn(true);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("Carol")).thenReturn(carol);
			bukkit.when(() -> Bukkit.getPlayerExact("Dave")).thenReturn(null);

			CampaignRaidWarbandService.enrollOnlineDefenders(war, raid);
		}

		Warband def = CampaignRaidWarbandService.getDefenderWarband(raid);
		assertTrue(def.hasMember(CAROL_ID));
		assertEquals(CAROL_ID, def.getLeaderId());
	}

	@Test
	void enrollOnlineDefenders_skipsPlayersAlreadyInWarband() {
		when(defender.getMembers()).thenReturn(List.of("Carol", "Dave"));

		Warband other = Warband.createWithMemberIds("other_band", CAROL_ID, true);
		WarbandManager.addWarband(other);

		Player carol = mock(Player.class);
		when(carol.getUniqueId()).thenReturn(CAROL_ID);
		when(carol.getName()).thenReturn("Carol");
		when(carol.isOnline()).thenReturn(true);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("Carol")).thenReturn(carol);
			bukkit.when(() -> Bukkit.getPlayerExact("Dave")).thenReturn(null);

			CampaignRaidWarbandService.enrollOnlineDefenders(war, raid);
		}

		Warband def = CampaignRaidWarbandService.getDefenderWarband(raid);
		assertFalse(def.hasMember(CAROL_ID));
	}

	@Test
	void destroyRaidWarbands_removesBothFromManager() {
		CampaignRaidWarbandService.createRaidWarbands(war, raid);
		assertEquals(2, WarbandManager.get().size());

		CampaignRaidWarbandService.destroyRaidWarbands(war, raid);

		assertNull(CampaignRaidWarbandService.getAttackerWarband(raid));
		assertNull(CampaignRaidWarbandService.getDefenderWarband(raid));
		assertTrue(WarbandManager.get().isEmpty());
	}

	@Test
	void promoteLeaderIfNeeded_promotesOldestRemainingMember() {
		Warband atk = CampaignRaidWarbandService.getAttackerWarband(raid);
		atk.addMember(BOB_ID);
		atk.setLeaderId(BOB_ID);
		atk.addMember(ALICE_ID);

		atk.removeMember(BOB_ID);
		CampaignRaidWarbandService.promoteLeaderIfNeeded(atk);

		assertEquals(ALICE_ID, atk.getLeaderId());
	}

	@Test
	void promoteLeaderIfNeeded_resetsPendingLeaderWhenEmpty() {
		Warband atk = CampaignRaidWarbandService.getAttackerWarband(raid);
		atk.addMember(BOB_ID);
		atk.setLeaderId(BOB_ID);
		atk.removeMember(BOB_ID);

		CampaignRaidWarbandService.promoteLeaderIfNeeded(atk);

		assertTrue(atk.isPendingLeader());
	}

	@Test
	void tryEnrollDefenderOnLogin_enrollsDuringFighting() {
		CampaignRaidService.transitionToFighting(war, raidWindow.plusSeconds(60));

		Player carol = mock(Player.class);
		when(carol.getUniqueId()).thenReturn(CAROL_ID);
		when(carol.getName()).thenReturn("Carol");

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getByMember("Carol")).thenReturn(defender);
			factions.when(() -> FactionManager.getByLeader("Carol")).thenReturn(null);

			CampaignRaidWarbandService.tryEnrollDefenderOnLogin(carol);
		}

		Warband def = CampaignRaidWarbandService.getDefenderWarband(raid);
		assertTrue(def.hasMember(CAROL_ID));
	}
}
