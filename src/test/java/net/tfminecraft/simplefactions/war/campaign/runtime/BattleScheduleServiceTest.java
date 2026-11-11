package net.tfminecraft.simplefactions.war.campaign.runtime;


import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;

class BattleScheduleServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private static Instant voteCloseInstant() {
		return BattleWindowService.atScheduleHour(BATTLE_DAY, 16);
	}

	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		Cache.battleCampaignTemplateField = "";
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 24;
		Cache.warRaidWindowStartHour = 19;
		Cache.warRaidWindowEndHour = 20;
		Cache.warVoteCloseHour = 16;
		Cache.warDefenderChoiceDeadlineHour = 12;
		Cache.warFirstBattleAtBorder = true;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		mockMilitary(attacker);
		mockMilitary(defender);
	}

	@Test
	void isBeforeVoteClose_trueBeforeConfiguredHourOnBattleDay() {
		War war = votingWar();
		assertTrue(BattleScheduleService.isBeforeVoteClose(
				war, voteCloseInstant().minusSeconds(60)));
		assertFalse(BattleScheduleService.isBeforeVoteClose(
				war, voteCloseInstant()));
		assertTrue(BattleScheduleService.isVoteCloseDue(war, voteCloseInstant()));
	}

	@Test
	void isRaidWindowOpen_trueDuringRaidHoursOnBattleDay() {
		War war = votingWar();
		assertFalse(BattleScheduleService.isRaidWindowOpen(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 18)));
		assertTrue(BattleScheduleService.isRaidWindowOpen(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 19)));
		assertTrue(BattleScheduleService.isRaidWindowOpen(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 20)));
		assertFalse(BattleScheduleService.isRaidWindowOpen(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 21)));
	}

	@Test
	void isRaidWindowOpen_falseOffBattleDay() {
		War war = votingWar();
		war.setBattleDay(BATTLE_DAY.plusDays(1));
		assertFalse(BattleScheduleService.isRaidWindowOpen(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 19)));
	}

	@Test
	void isBattleWindowOpen_trueDuringBattleHoursOnBattleDay() {
		War war = votingWar();
		assertFalse(BattleScheduleService.isBattleWindowOpen(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 20)));
		assertTrue(BattleScheduleService.isBattleWindowOpen(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 21)));
		assertTrue(BattleScheduleService.isBattleWindowOpen(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 23)));
		assertFalse(BattleWindowService.isValidHour(24));
	}

	@Test
	void isBattleWindowOpen_falseOffBattleDay() {
		War war = votingWar();
		war.setBattleDay(BATTLE_DAY.plusDays(1));
		assertFalse(BattleScheduleService.isBattleWindowOpen(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 21)));
	}

	@Test
	void hasNoNextBattle_falseWhileAPostBattleChoiceIsOpen() {
		War war = defenderChoiceWar();
		war.setInitiativeAttacker(0);
		war.setInitiativeDefender(0);

		assertFalse(BattleScheduleService.hasNoNextBattle(war));
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		assertTrue(BattleScheduleService.hasNoNextBattle(war));
	}

	@Test
	void hasNoNextBattle_falseWhileABattleIsBeingFought() {
		War war = votingWar();
		war.setInitiativeAttacker(0);
		war.setInitiativeDefender(0);
		Battle running = mock(Battle.class);
		when(running.hasStarted()).thenReturn(true);
		try (MockedStatic<BattleManager> battles = mockStatic(BattleManager.class)) {
			battles.when(() -> BattleManager.getByWarId(war.getId())).thenReturn(running);
			assertFalse(BattleScheduleService.hasNoNextBattle(war));
		}
	}

	@Test
	void enterAutoresolvePending_setsPhaseAndClearsFlags() {
		War war = votingWar();
		war.setAutoresolveProposedByAttacker(true);
		war.setAutoresolveProposedByDefender(true);

		BattleScheduleService.enterAutoresolvePending(war);
		BattleScheduleService.enterAutoresolvePending(null);

		assertEquals(BattleSchedulePhase.AUTORESOLVE_PENDING, war.getBattleSchedulePhase());
		assertFalse(war.isAutoresolveProposedByAttacker());
		assertFalse(war.isAutoresolveProposedByDefender());
	}

	@Test
	void postBattleChoiceDeadline_isNoonOnTheDayAfterTheBattleHoweverFarTheNextBattleDayIs() {
		War war = defenderChoiceWar();
		war.setBattleDay(BATTLE_DAY.plusDays(4));
		war.getAvailability().setEarliestBattleDay(BATTLE_DAY);
		assertFalse(BattleScheduleService.isPostBattleChoiceDeadlineDue(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 12).minusSeconds(60)));
		assertTrue(BattleScheduleService.isPostBattleChoiceDeadlineDue(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 12)));
		assertTrue(BattleScheduleService.isPostBattleChoiceDeadlineDue(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY.plusDays(1), 3)));
		assertFalse(BattleScheduleService.isPostBattleChoiceDeadlineDue(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY.minusDays(1), 20)));
		assertFalse(BattleScheduleService.isPostBattleChoiceDeadlineDue(null, Instant.now()));
		assertFalse(BattleScheduleService.isPostBattleChoiceDeadlineDue(war, null));
		war.getAvailability().setEarliestBattleDay(null);
		assertTrue(BattleScheduleService.isPostBattleChoiceDeadlineDue(war, Instant.now()), "Its battle day stands in");
		war.setBattleDay(null);
		assertFalse(BattleScheduleService.isPostBattleChoiceDeadlineDue(war, Instant.now()));
		war.setBattleDay(BATTLE_DAY.minusDays(1));
		war.getAvailability().setEarliestBattleDay(BATTLE_DAY);
		assertTrue(BattleScheduleService.isPostBattleChoiceDeadlineDue(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY.minusDays(1), 12)), "The earlier of the two");
	}

	@Test
	void applyDefenderChoiceDeadline_notDueBeforeConfiguredHour() {
		War war = defenderChoiceWar();
		assertFalse(BattleScheduleService.applyPostBattleChoiceDeadline(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 12).minusSeconds(60)));
		assertFalse(war.isPostBattleChoiceResolved());
		assertFalse(BattleScheduleService.isPostBattleChoiceResolved(war));
		assertTrue(BattleScheduleService.isPostBattleChoiceResolved(votingWar()), "No choice, nothing to resolve");
		assertNull(BattleScheduleService.resolveScheduledProvinceId(war));
	}

	@Test
	void canProposeAutoresolveNow_onceADayIsLockedAndUntilTheBattleIsSet() {
		War war = votingWar();
		Instant now = BattleWindowService.atScheduleHour(BATTLE_DAY.minusDays(2), 15);

		assertTrue(BattleAutoresolveService.canProposeAutoresolveNow(war, now));
		assertFalse(BattleAutoresolveService.canProposeAutoresolveNow(war, null));
		assertFalse(BattleAutoresolveService.canProposeAutoresolveNow(null, now));

		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		assertFalse(BattleAutoresolveService.canProposeAutoresolveNow(war, now));
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setBattleDay(null);
		assertFalse(BattleAutoresolveService.canProposeAutoresolveNow(war, now));
	}

	@Test
	void scheduleBattleAtProvince_lateWindow_schedulesOneAmOnNextDate() {
		Cache.warBattleWindowEndHour = 26;
		War war = votingWar();

		withMockBossBar(() -> {
			Instant oneAm = BattleWindowService.atScheduleHour(BATTLE_DAY, 25);
			assertTrue(BattleScheduleService.scheduleBattleAtProvince(war, 20, oneAm));
			assertEquals(25, war.getScheduledBattleHour());
			assertEquals(
					BATTLE_DAY.plusDays(1).atTime(1, 0).atZone(BattleWindowService.SCHEDULE_ZONE).toInstant(),
					war.getScheduledBattleAt());
			assertEquals(BattleSchedulePhase.SCHEDULED, war.getBattleSchedulePhase());
		});
	}

	@Test
	void lateWindow_battleDayStillOpenAtOneThirtyNextMorning() {
		Cache.warBattleWindowEndHour = 26;
		War war = votingWar();
		Instant oneThirty = BATTLE_DAY.plusDays(1).atTime(1, 30)
				.atZone(BattleWindowService.SCHEDULE_ZONE).toInstant();
		assertTrue(BattleScheduleService.isOnBattleDay(war, oneThirty));
		assertTrue(BattleScheduleService.isBattleWindowOpen(war, oneThirty));
		assertFalse(BattleScheduleService.isBattleWindowOpen(war, oneThirty.plusSeconds(3600)));
	}

	@Test
	void advanceAfterBattle_takesTheFoughtDayOffAndMovesToTheNextLockedOne() {
		War war = votingWar();
		war.getAvailability().putBattleDay(LockedBattleDay.open(BATTLE_DAY.plusDays(1), false).withHour(22, false));
		war.getBattleInstallationPicks().put("atk", new java.util.LinkedHashSet<>(Set.of("fort-1")));
		war.setBattleInstallationPicksBattleDay(BATTLE_DAY);

		BattleScheduleService.advanceAfterBattle(war, BattleWindowService.atScheduleHour(BATTLE_DAY, 22));

		assertEquals(BATTLE_DAY.plusDays(1), war.getBattleDay());
		assertEquals(List.of(BATTLE_DAY.plusDays(1)),
				war.getAvailability().battleDays().stream().map(LockedBattleDay::date).toList());
		assertEquals(BATTLE_DAY.plusDays(1), war.getAvailability().getEarliestBattleDay());
		assertTrue(war.getBattleInstallationPicks().isEmpty());
	}

	@Test
	void advanceAfterBattle_withNothingLockedLeavesNoBattleDay() {
		Cache.warBattleWindowEndHour = 26;
		War war = votingWar();
		Instant oneThirty = BATTLE_DAY.plusDays(1).atTime(1, 30)
				.atZone(BattleWindowService.SCHEDULE_ZONE).toInstant();

		BattleScheduleService.advanceAfterBattle(war, oneThirty);

		assertNull(war.getBattleDay());
		assertTrue(war.getAvailability().battleDays().isEmpty());
		assertEquals(BATTLE_DAY.plusDays(1), war.getAvailability().getEarliestBattleDay());
	}

	@Test
	void advanceAfterBattle_neverMovesTheEarliestDayBack() {
		War war = votingWar();
		war.getAvailability().setEarliestBattleDay(BATTLE_DAY.plusDays(9));

		BattleScheduleService.advanceAfterBattle(war, BattleWindowService.atScheduleHour(BATTLE_DAY, 22));

		assertEquals(BATTLE_DAY.plusDays(9), war.getAvailability().getEarliestBattleDay());
	}

	@Test
	void advanceAfterBattle_withoutAFoughtDayUsesTheCurrentBattleDayOrTheRealClock() {
		War war = votingWar();
		war.setBattleDay(null);
		war.getAvailability().setEarliestBattleDay(null);

		BattleScheduleService.advanceAfterBattle(war, BattleWindowService.atScheduleHour(BATTLE_DAY, 10));

		assertEquals(BATTLE_DAY.plusDays(1), war.getAvailability().getEarliestBattleDay());
		assertEquals(BATTLE_DAY, war.getBattleDay());
		war.setBattleDay(null);
		war.getAvailability().clearBattleDays();
		war.getAvailability().setEarliestBattleDay(null);
		BattleScheduleService.advanceAfterBattle(war, null);
		assertEquals(BattleScheduleService.battleDayDate(Instant.now()).plusDays(1),
				war.getAvailability().getEarliestBattleDay());
		BattleScheduleService.advanceAfterBattle(null, Instant.now());
	}

	@Test
	void releaseScheduledBattle_sendsTheWarBackToWaitingWithoutAStartTime() {
		War war = votingWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setScheduledBattleAt(voteCloseInstant());
		war.setScheduledBattleProvinceId(20);
		war.getSignupRemindersSent().add(15);

		BattleScheduleService.releaseScheduledBattle(war);

		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		assertNull(war.getScheduledBattleAt());
		assertNull(war.getScheduledBattleProvinceId());
		assertTrue(war.getSignupRemindersSent().isEmpty());
		assertEquals(BATTLE_DAY, war.getBattleDay());
	}

	@Test
	void applyScheduledInstant_needsAWarATimeAndABattleProvince() {
		War war = votingWar();
		assertFalse(BattleScheduleService.applyScheduledInstant(null, voteCloseInstant()));
		assertFalse(BattleScheduleService.applyScheduledInstant(war, null));
		war.setInitiativeAttacker(0);
		war.setInitiativeDefender(0);
		assertFalse(BattleScheduleService.applyScheduledInstant(war, voteCloseInstant()));
	}

	private War votingWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignStartProvinceId(20);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolder(net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole.ATTACKER);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.getAvailability().putBattleDay(LockedBattleDay.open(BATTLE_DAY, false));
		war.getAvailability().setEarliestBattleDay(BATTLE_DAY);
		return war;
	}

	private War defenderChoiceWar() {
		War war = votingWar();
		war.setInitiativeHolder(net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole.ATTACKER);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleWinnerCoalition(CampaignSideRole.DEFENDER);
		war.setLastBattleOffensiveCoalition(CampaignSideRole.AGGRESSOR);
		war.setPostBattleChoiceResolved(false);
		return war;
	}

	private void mockMilitary(Faction faction) {
		Military military = mock(Military.class);
		Regiment levy = mock(Regiment.class);
		when(military.getManpowerNoLevy(anyBoolean())).thenReturn(10);
		when(military.getRegiment("levy")).thenReturn(levy);
		when(levy.getEntries()).thenReturn(List.of());
		when(faction.getMilitary()).thenReturn(military);
		when(faction.getMembers()).thenReturn(List.of());
		when(faction.getLeader()).thenReturn("leader");
		when(faction.getName()).thenReturn("faction");
	}

	private void withChoiceResolutionMocks(War war, Runnable action) {
		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class);
				MockedStatic<net.tfminecraft.simplefactions.war.battle.military.BattleForces> pool =
						mockStatic(net.tfminecraft.simplefactions.war.battle.military.BattleForces.class)) {
			warManager.when(() -> WarManager.persist(any())).then(inv -> null);
			warManager.when(() -> WarManager.getById(war.getId())).thenReturn(war);
			warManager.when(() -> WarManager.endWar(any(), any())).then(inv -> null);
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattleForces.build(
					any(), any(), any(), any(), any())).thenReturn(net.tfminecraft.simplefactions.war.wounds.TestForces.regiments(5));
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattleForces.regimentSlots(
					any(), any(), any(), any())).thenReturn(5);
			action.run();
		}
	}

	private void withMockBossBar(Runnable action) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<net.tfminecraft.simplefactions.war.battle.military.BattleForces> pool =
						mockStatic(net.tfminecraft.simplefactions.war.battle.military.BattleForces.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattleForces.build(
					any(), any(), any(), any(), any())).thenReturn(net.tfminecraft.simplefactions.war.wounds.TestForces.regiments(5));
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattleForces.regimentSlots(
					any(), any(), any(), any())).thenReturn(5);
			action.run();
		}
	}
}
