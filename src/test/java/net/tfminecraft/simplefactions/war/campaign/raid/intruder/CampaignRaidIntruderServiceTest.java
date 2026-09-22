package net.tfminecraft.simplefactions.war.campaign.raid.intruder;




import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidState;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidMusterScheduler;
import net.tfminecraft.simplefactions.war.campaign.raid.intruder.CampaignRaidIntruderService;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.raid.RaidAttackerEliminationService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;

class CampaignRaidIntruderServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);
	private static final int TARGET_PROVINCE = 20;
	private static final int WRONG_PROVINCE = 10;
	private static final String BATTLE_ID = "cr_battle_1_2026-08-21";

	private static final UUID BOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID CAROL_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

	private Faction attacker;
	private Faction defender;
	private War war;
	private CampaignRaid raid;
	private Battle battle;

	@BeforeEach
	void setUp() {
		Cache.warVoteCloseHour = 16;
		Cache.warRaidWindowStartHour = 19;
		Cache.warRaidWindowEndHour = 20;
		Cache.campaignRaidMusterSeconds = 60;
		WarbandManager.resetForTests();
		BattleManager.resetForTests();
		RaidAttackerEliminationService.resetForTests();
		CampaignRaidIntruderService.resetForTests();
		CampaignRaidMusterScheduler.resetForTests();

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		when(attacker.getLeader()).thenReturn("Alice");
		when(defender.getLeader()).thenReturn("Carol");
		when(attacker.getMembers()).thenReturn(new ArrayList<>());
		when(defender.getMembers()).thenReturn(new ArrayList<>());

		Installation atkPort = new Installation("port-atk", "Atk Port", InstallationKind.PORT, WRONG_PROVINCE, 0, 0, 0L);
		Installation defPort = new Installation("port-def", "Def Port", InstallationKind.PORT, TARGET_PROVINCE, 0, 0, 0L);
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

		when(attacker.getMembers()).thenReturn(new ArrayList<>(List.of("Bob")));
		when(defender.getMembers()).thenReturn(new ArrayList<>(List.of("Carol")));
		when(attacker.isMemberIgnoreCase("Bob")).thenReturn(true);
		when(defender.isMemberIgnoreCase("Carol")).thenReturn(true);

		Instant raidWindow = BattleWindowService.atScheduleHour(BATTLE_DAY, 19);
		CampaignRaidService.beginMuster(war, attacker, "port-atk", "port-def", raidWindow);
		raid = CampaignRaidService.getActive(war);

		battle = new Battle(BATTLE_ID);
		battle.setStarted(true);
		BattleManager.addBattle(battle);

		raid.setState(CampaignRaidState.FIGHTING);
		raid.setBattleId(BATTLE_ID);
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.remove(attacker);
		FactionManager.factions.remove(defender);
		WarManager.get().clear();
		WarbandManager.resetForTests();
		BattleManager.resetForTests();
		RaidAttackerEliminationService.resetForTests();
		CampaignRaidIntruderService.resetForTests();
		CampaignRaidMusterScheduler.resetForTests();
	}

	@Test
	void activeAttackerParticipant_exempt() {
		CampaignRaidWarbandService.signupAttacker(war, raid, BOB_ID, "Bob");

		assertFalse(CampaignRaidIntruderService.shouldPenalize(
				war, raid, BOB_ID, "Bob", TARGET_PROVINCE));
	}

	@Test
	void eliminatedAttacker_penalized() {
		CampaignRaidWarbandService.signupAttacker(war, raid, BOB_ID, "Bob");
		RaidAttackerEliminationService.markOut(battle, BOB_ID);

		assertTrue(CampaignRaidIntruderService.shouldPenalize(
				war, raid, BOB_ID, "Bob", TARGET_PROVINCE));
	}

	@Test
	void defenderInProvince_exempt() {
		assertFalse(CampaignRaidIntruderService.shouldPenalize(
				war, raid, CAROL_ID, "Carol", TARGET_PROVINCE));
	}

	@Test
	void nonParticipantAttacker_penalized() {
		assertTrue(CampaignRaidIntruderService.shouldPenalize(
				war, raid, BOB_ID, "Bob", TARGET_PROVINCE));
	}

	@Test
	void wrongProvince_exempt() {
		assertFalse(CampaignRaidIntruderService.shouldPenalize(
				war, raid, BOB_ID, "Bob", WRONG_PROVINCE));
	}

	@Test
	void notFighting_exempt() {
		raid.setState(CampaignRaidState.MUSTER);

		assertFalse(CampaignRaidIntruderService.shouldPenalize(
				war, raid, BOB_ID, "Bob", TARGET_PROVINCE));
	}
}
