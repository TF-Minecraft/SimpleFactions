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
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.BattleScheduleCloseResult;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.CloseVoteOptions;

class BattleScheduleServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private static Instant voteCloseInstant() {
		return BattleWindowService.atScheduleHour(BATTLE_DAY, 16);
	}

	private Faction attacker;
	private Faction defender;
	private UUID attackerVoter1;
	private UUID attackerVoter2;
	private UUID defenderVoter1;
	private UUID defenderVoter2;

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
		Cache.warBattleVotingMinPlayers = 4;
		Cache.warBattleVotingRequireSmallestSideFull = true;
		Cache.warBattleVotingPassIfEither = true;
		Cache.warFirstBattleAtBorder = true;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		mockMilitary(attacker);
		mockMilitary(defender);

		attackerVoter1 = UUID.randomUUID();
		attackerVoter2 = UUID.randomUUID();
		defenderVoter1 = UUID.randomUUID();
		defenderVoter2 = UUID.randomUUID();
	}

	@Test
	void closeVote_schedulesWhenQuorumMet() {
		War war = votingWar();
		addCrossSideVotes(war, 21);

		withMockBossBar(() -> {
			BattleScheduleCloseResult result = BattleScheduleService.closeVote(
					war,
					voteCloseInstant(),
					uuidToFaction(),
					name -> null);

			assertEquals(BattleScheduleCloseResult.SCHEDULED, result);
			assertEquals(BattleSchedulePhase.SCHEDULED, war.getBattleSchedulePhase());
			assertEquals(21, war.getScheduledBattleHour());
			assertEquals(Integer.valueOf(20), war.getScheduledBattleProvinceId());
			assertEquals(
					BattleWindowService.computeScheduledBattleAt(BATTLE_DAY, 21),
					war.getScheduledBattleAt());
		});
	}

	@Test
	void closeVote_postponesWhenQuorumFails() {
		War war = votingWar();
		war.getBattleVotes().put(attackerVoter1, Set.of(21));

		BattleScheduleCloseResult result = BattleScheduleService.closeVote(
				war,
				voteCloseInstant(),
				uuidToFaction(),
				name -> null);

		assertEquals(BattleScheduleCloseResult.POSTPONED, result);
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		assertEquals(BATTLE_DAY.plusDays(1), war.getBattleDay());
		assertEquals(1, war.getPostponementsThisCycle());
		assertTrue(war.getBattleVotes().containsKey(attackerVoter1));
		assertNull(war.getScheduledBattleAt());
		assertFalse(war.isDefenderChoiceResolved());
	}

	@Test
	void postpone_clearsInstallationPicks() {
		War war = votingWar();
		war.getBattleInstallationPicks().put("atk", new java.util.LinkedHashSet<>(Set.of("fort-1")));
		war.setBattleInstallationPicksBattleDay(BATTLE_DAY);

		BattleScheduleService.postpone(war);

		assertEquals(BATTLE_DAY.plusDays(1), war.getBattleDay());
		assertTrue(war.getBattleInstallationPicks().isEmpty());
		assertNull(war.getBattleInstallationPicksBattleDay());
	}

	@Test
	void isBeforeVoteClose_trueBeforeConfiguredHourOnBattleDay() {
		War war = votingWar();
		assertTrue(BattleScheduleService.isBeforeVoteClose(
				war, voteCloseInstant().minusSeconds(60)));
		assertFalse(BattleScheduleService.isBeforeVoteClose(
				war, voteCloseInstant()));
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
	void closeVote_resetsPostponementsWhenScheduled() {
		War war = votingWar();
		war.setPostponementsThisCycle(2);
		addCrossSideVotes(war, 21);

		withMockBossBar(() -> {
			BattleScheduleCloseResult result = BattleScheduleService.closeVote(
					war,
					voteCloseInstant(),
					uuidToFaction(),
					name -> null);

			assertEquals(BattleScheduleCloseResult.SCHEDULED, result);
			assertEquals(0, war.getPostponementsThisCycle());
		});
	}

	@Test
	void closeVote_autoresolvesWhenPostponementsExhausted() {
		Cache.warBattleVotingMaxPostponements = 1;
		War war = votingWar();
		war.setPostponementsThisCycle(1);

		try (MockedStatic<BattleAutoresolveService> autoresolve = mockStatic(BattleAutoresolveService.class)) {
			autoresolve.when(() -> BattleAutoresolveService.resolve(war)).thenReturn(true);

			BattleScheduleCloseResult result = BattleScheduleService.closeVote(
					war,
					voteCloseInstant(),
					uuidToFaction(),
					name -> null);

			assertEquals(BattleScheduleCloseResult.AUTORESOLVED, result);
			assertEquals(BATTLE_DAY, war.getBattleDay());
			autoresolve.verify(() -> BattleAutoresolveService.resolve(war));
		}
	}

	@Test
	void closeVote_endsInWhitePeaceWhenNoBattleCanFollowTheLastPostponement() {
		Cache.warBattleVotingMaxPostponements = 1;
		int freezeHours = Cache.warPostponeFreezeHours;
		Cache.warPostponeFreezeHours = 24;
		War war = votingWar();
		war.setPostponementsThisCycle(1);
		// The side with the initiative has no fuel left, so no battle can be scheduled.
		war.setInitiativeAttacker(0);

		try (MockedStatic<BattleAutoresolveService> autoresolve = mockStatic(BattleAutoresolveService.class);
				MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			autoresolve.when(() -> BattleAutoresolveService.resolve(war)).thenReturn(false);

			BattleScheduleCloseResult result = BattleScheduleService.closeVote(
					war,
					voteCloseInstant(),
					uuidToFaction(),
					name -> null);

			assertEquals(BattleScheduleCloseResult.ENDED_NO_NEXT_BATTLE, result);
			warManager.verify(() -> WarManager.endWar(war, WarEndReason.WHITE_PEACE));
			assertEquals(BATTLE_DAY, war.getBattleDay());
			assertEquals(1, war.getPostponementsThisCycle());
			assertNull(war.getPreparationFrozenUntil());
		} finally {
			Cache.warPostponeFreezeHours = freezeHours;
		}
	}

	@Test
	void closeVote_stopsWhenThePostBattleDeadlineEndsTheWar() {
		Cache.warBattleVotingMaxPostponements = 1;
		War war = votingWar();
		try (MockedStatic<net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService> choice =
				mockStatic(net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.class);
				MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			choice.when(() -> net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService
					.needsAnyChoice(war)).thenReturn(true);
			war.setPostBattleChoiceResolved(false);
			// The loser could not attack, so its deadline accepted peace and the war ended.
			choice.when(() -> net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService
					.applyDeadlineIfDue(eq(war), any())).thenAnswer(call -> {
						war.end(WarEndReason.WHITE_PEACE);
						return true;
					});

			BattleScheduleCloseResult result = BattleScheduleService.closeVote(
					war,
					voteCloseInstant(),
					uuidToFaction(),
					name -> null);

			assertEquals(BattleScheduleCloseResult.WAR_ENDED, result);
			assertEquals(0, war.getPostponementsThisCycle(), "An ended war is not postponed");
			warManager.verify(() -> WarManager.endWar(any(), any()), never());
		}
	}

	@Test
	void closeVote_aStartedBattleStillPostponesAtTheLimit() {
		Cache.warBattleVotingMaxPostponements = 1;
		War war = votingWar();
		war.setPostponementsThisCycle(1);
		war.setInitiativeAttacker(0);
		Battle running = mock(Battle.class);
		when(running.getWarId()).thenReturn(war.getId());
		when(running.hasStarted()).thenReturn(true);
		BattleManager.addBattle(running);

		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			BattleScheduleCloseResult result = BattleScheduleService.closeVote(
					war,
					voteCloseInstant(),
					uuidToFaction(),
					name -> null);

			assertEquals(BattleScheduleCloseResult.POSTPONED, result);
			assertEquals(BATTLE_DAY.plusDays(1), war.getBattleDay());
			assertEquals(2, war.getPostponementsThisCycle());
			warManager.verify(() -> WarManager.endWar(any(), any()), org.mockito.Mockito.never());
		} finally {
			BattleManager.resetForTests();
		}
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
	void enterAutoresolvePending_setsPhaseAndClearsFlags() {
		War war = votingWar();
		war.setAutoresolveProposedByAttacker(true);
		war.setAutoresolveProposedByDefender(true);

		BattleScheduleService.enterAutoresolvePending(war);

		assertEquals(BattleSchedulePhase.AUTORESOLVE_PENDING, war.getBattleSchedulePhase());
		assertFalse(war.isAutoresolveProposedByAttacker());
		assertFalse(war.isAutoresolveProposedByDefender());
	}

	@Test
	void closeVote_forceImmediateBypassesVoteCloseHour() {
		War war = defenderChoiceWar();

		withMockBossBar(() -> {
			assertTrue(BattleScheduleService.applyPostBattleChoiceDeadline(
					war, BattleWindowService.atScheduleHour(BATTLE_DAY, 12)));
			assertTrue(war.isPostBattleChoiceResolved());
		});
	}

	@Test
	void closeVote_blockedWhenDefenderDeadlineAfterVoteClose() {
		Cache.warDefenderChoiceDeadlineHour = 18;
		Cache.warVoteCloseHour = 16;
		War war = defenderChoiceWar();

		BattleScheduleCloseResult result = BattleScheduleService.closeVote(
				war,
				voteCloseInstant(),
				uuidToFaction(),
				name -> null);

		assertEquals(BattleScheduleCloseResult.BLOCKED_DEFENDER_CHOICE, result);
		assertFalse(war.isPostBattleChoiceResolved());
	}

	@Test
	void applyDefenderChoiceDeadline_notDueBeforeConfiguredHour() {
		War war = defenderChoiceWar();
		assertFalse(BattleScheduleService.applyPostBattleChoiceDeadline(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 12).minusSeconds(60)));
		assertFalse(war.isPostBattleChoiceResolved());
	}

	@Test
	void closeVote_schedulesAfterAutoPushAtCloseWhenChoiceNeeded() {
		War war = defenderChoiceWar();
		addCrossSideVotes(war, 21);

		withMockBossBar(() -> {
			BattleScheduleCloseResult result = BattleScheduleService.closeVote(
					war,
					voteCloseInstant(),
					uuidToFaction(),
					name -> null);

			assertEquals(BattleScheduleCloseResult.SCHEDULED, result);
			assertTrue(war.isPostBattleChoiceResolved());
			assertEquals(Integer.valueOf(5), war.getScheduledBattleProvinceId());
		});
	}

	@Test
	void closeVote_skippedWhenWrongPhaseOrDay() {
		War war = votingWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);

		assertEquals(
				BattleScheduleCloseResult.SKIPPED,
				BattleScheduleService.closeVote(
						war,
						voteCloseInstant(),
						uuidToFaction(),
						name -> null));

		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		assertEquals(
				BattleScheduleCloseResult.SKIPPED,
				BattleScheduleService.closeVote(
						war,
						BattleWindowService.atScheduleHour(BATTLE_DAY.minusDays(1), 16),
						uuidToFaction(),
						name -> null));
	}

	@Test
	void applyDefenderChoiceDeadline_autoHoldsAtDeadline() {
		War war = votingWar();
		addCrossSideVotes(war, 21);

		withMockBossBar(() -> {
			BattleScheduleCloseResult result = BattleScheduleService.closeVote(
					war,
					BattleWindowService.atScheduleHour(BATTLE_DAY, 10),
					uuidToFaction(),
					name -> null,
					CloseVoteOptions.admin(false));

			assertEquals(BattleScheduleCloseResult.SCHEDULED, result);
			assertEquals(BattleSchedulePhase.SCHEDULED, war.getBattleSchedulePhase());
		});
	}

	@Test
	void closeVote_forceQuorumBypassesMinPlayers() {
		War war = votingWar();
		UUID atk = UUID.randomUUID();
		UUID def = UUID.randomUUID();
		war.getBattleVotes().put(atk, Set.of(21));
		war.getBattleVotes().put(def, Set.of(21));

		Function<UUID, Faction> factions = uuid -> uuid.equals(def) ? defender : attacker;
		withMockBossBar(() -> {
			BattleScheduleCloseResult result = BattleScheduleService.closeVote(
					war,
					voteCloseInstant(),
					factions,
					name -> null,
					CloseVoteOptions.admin(true));

			assertEquals(BattleScheduleCloseResult.SCHEDULED, result);
		});
	}

	@Test
	void closeVote_clearsForceQuorumFlagAfterAttempt() {
		War war = votingWar();
		war.setForceQuorumNextClose(true);
		addCrossSideVotes(war, 21);

		withMockBossBar(() -> {
			BattleScheduleService.closeVote(
					war,
					voteCloseInstant(),
					uuidToFaction(),
					name -> null,
					CloseVoteOptions.scheduled());

			assertFalse(war.isForceQuorumNextClose());
		});
	}

	@Test
	void canProposeAutoresolveNow_onlyDuringVotingBeforeVoteCloseOnBattleDay() {
		War war = votingWar();

		assertTrue(BattleAutoresolveService.canProposeAutoresolveNow(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 15)));

		assertFalse(BattleAutoresolveService.canProposeAutoresolveNow(
				war, voteCloseInstant()));

		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		assertFalse(BattleAutoresolveService.canProposeAutoresolveNow(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 15)));
	}

	@Test
	void closeVote_lateWindow_schedulesOneAmOnNextDate() {
		Cache.warBattleWindowEndHour = 26;
		War war = votingWar();
		addCrossSideVotes(war, 25);

		withMockBossBar(() -> {
			BattleScheduleCloseResult result = BattleScheduleService.closeVote(
					war,
					voteCloseInstant(),
					uuidToFaction(),
					name -> null);

			assertEquals(BattleScheduleCloseResult.SCHEDULED, result);
			assertEquals(25, war.getScheduledBattleHour());
			assertEquals(
					BATTLE_DAY.plusDays(1).atTime(1, 0).atZone(BattleWindowService.SCHEDULE_ZONE).toInstant(),
					war.getScheduledBattleAt());
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
	void advanceAfterBattle_movesToNextDayAndClearsVotes() {
		War war = votingWar();
		addCrossSideVotes(war, 21);
		Instant afterBattle = BattleWindowService.atScheduleHour(BATTLE_DAY, 22);

		BattleScheduleService.advanceAfterBattle(war, afterBattle);

		assertEquals(BATTLE_DAY.plusDays(1), war.getBattleDay());
		assertTrue(war.getBattleVotes().isEmpty());
	}

	@Test
	void advanceAfterBattle_afterMidnightStillCountsAsFoughtDay() {
		Cache.warBattleWindowEndHour = 26;
		War war = votingWar();
		Instant oneThirty = BATTLE_DAY.plusDays(1).atTime(1, 30)
				.atZone(BattleWindowService.SCHEDULE_ZONE).toInstant();

		BattleScheduleService.advanceAfterBattle(war, oneThirty);

		assertEquals(BATTLE_DAY.plusDays(1), war.getBattleDay());
	}

	@Test
	void advanceAfterBattle_staleBattleDayJumpsPastToday() {
		War war = votingWar();
		Instant later = BattleWindowService.atScheduleHour(BATTLE_DAY.plusDays(3), 22);

		BattleScheduleService.advanceAfterBattle(war, later);

		assertEquals(BATTLE_DAY.plusDays(4), war.getBattleDay());
	}

	@Test
	void advanceAfterBattle_lateResultAfterRolloverMovesOnlyOneDay() {
		War war = votingWar();
		Instant threeAmNextDay = BattleWindowService.atScheduleHour(BATTLE_DAY.plusDays(1), 3);

		BattleScheduleService.advanceAfterBattle(war, threeAmNextDay);

		assertEquals(BATTLE_DAY.plusDays(1), war.getBattleDay());
	}

	@Test
	void advanceAfterBattle_resultAfterNextDaysVoteCloseSkipsToTheDayAfter() {
		War war = votingWar();
		Instant afterVoteClose = BattleWindowService.atScheduleHour(BATTLE_DAY.plusDays(1), 17);

		BattleScheduleService.advanceAfterBattle(war, afterVoteClose);

		assertEquals(BATTLE_DAY.plusDays(2), war.getBattleDay());
	}

	@Test
	void advanceAfterBattle_withoutAFoughtDayUsesTheCurrentBattleDay() {
		War war = votingWar();
		war.setBattleDay(null);
		Instant morning = BattleWindowService.atScheduleHour(BATTLE_DAY, 10);

		BattleScheduleService.advanceAfterBattle(war, morning);

		assertEquals(BATTLE_DAY, war.getBattleDay());
	}

	@Test
	void advanceAfterBattle_withoutATimeUsesTheRealClock() {
		War war = votingWar();
		war.setBattleDay(LocalDate.of(2000, 1, 1));

		BattleScheduleService.advanceAfterBattle(war, null);

		assertFalse(war.getBattleDay().isBefore(BattleScheduleService.battleDayDate(Instant.now())));
	}

	@Test
	void scheduleChanges_ignoreMissingWarsDaysAndTimes() {
		BattleScheduleService.advanceAfterBattle(null, Instant.now());
		assertFalse(BattleScheduleService.setBattleDay(null, BATTLE_DAY));
		War war = votingWar();
		assertFalse(BattleScheduleService.setBattleDay(war, null));
		assertFalse(BattleScheduleService.catchUpStaleBattleDay(null, Instant.now()));
		assertFalse(BattleScheduleService.catchUpStaleBattleDay(war, null));
		war.setBattleDay(null);
		assertFalse(BattleScheduleService.catchUpStaleBattleDay(war, Instant.now()));
		assertNull(war.getBattleDay());
	}

	@Test
	void setBattleDay_scheduledWarReopensVoteOnNewDay() {
		War war = votingWar();
		addCrossSideVotes(war, 21);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);

		assertTrue(BattleScheduleService.setBattleDay(war, BATTLE_DAY.plusDays(2)));

		assertEquals(BATTLE_DAY.plusDays(2), war.getBattleDay());
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		assertTrue(war.getBattleVotes().isEmpty());
		assertNull(war.getScheduledBattleAt());
	}

	@Test
	void catchUpStaleBattleDay_movesPastVotingDayToToday() {
		War war = votingWar();
		addCrossSideVotes(war, 21);
		Instant twoDaysLater = BattleWindowService.atScheduleHour(BATTLE_DAY.plusDays(2), 10);

		assertTrue(BattleScheduleService.catchUpStaleBattleDay(war, twoDaysLater));

		assertEquals(BATTLE_DAY.plusDays(2), war.getBattleDay());
		assertTrue(war.getBattleVotes().isEmpty());
		assertEquals(0, war.getPostponementsThisCycle());
	}

	@Test
	void catchUpStaleBattleDay_afterTodaysVoteCloseMovesToTomorrow() {
		War war = votingWar();
		Instant afterVoteClose = BattleWindowService.atScheduleHour(BATTLE_DAY.plusDays(2), 17);

		assertTrue(BattleScheduleService.catchUpStaleBattleDay(war, afterVoteClose));

		assertEquals(BATTLE_DAY.plusDays(3), war.getBattleDay());
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		assertEquals(0, war.getPostponementsThisCycle());
	}

	@Test
	void catchUpStaleBattleDay_leavesCurrentOrFutureDayAlone() {
		War war = votingWar();
		addCrossSideVotes(war, 21);

		assertFalse(BattleScheduleService.catchUpStaleBattleDay(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 10)));
		assertFalse(BattleScheduleService.catchUpStaleBattleDay(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY.minusDays(1), 10)));

		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		assertFalse(BattleScheduleService.catchUpStaleBattleDay(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY.plusDays(2), 10)));
		assertEquals(BATTLE_DAY, war.getBattleDay());
		assertFalse(war.getBattleVotes().isEmpty());
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

	private void addCrossSideVotes(War war, int hour) {
		war.getBattleVotes().put(attackerVoter1, Set.of(hour));
		war.getBattleVotes().put(attackerVoter2, Set.of(hour));
		war.getBattleVotes().put(defenderVoter1, Set.of(hour));
		war.getBattleVotes().put(defenderVoter2, Set.of(hour));
	}

	private Function<UUID, Faction> uuidToFaction() {
		return uuid -> {
			if (uuid.equals(defenderVoter1) || uuid.equals(defenderVoter2)) {
				return defender;
			}
			return attacker;
		};
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
				MockedStatic<net.tfminecraft.simplefactions.war.battle.military.BattlePoolService> pool =
						mockStatic(net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.class)) {
			warManager.when(() -> WarManager.persist(any())).then(inv -> null);
			warManager.when(() -> WarManager.getById(war.getId())).thenReturn(war);
			warManager.when(() -> WarManager.endWar(any(), any())).then(inv -> null);
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.totalCommittedRegiments(
					any(), any(Integer.class), any())).thenReturn(5);
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.totalCommittedRegiments(
					any(), any(Integer.class), any(), any())).thenReturn(5);
			action.run();
		}
	}

	private void withMockBossBar(Runnable action) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<net.tfminecraft.simplefactions.war.battle.military.BattlePoolService> pool =
						mockStatic(net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.totalCommittedRegiments(
					any(), any(Integer.class), any())).thenReturn(5);
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.totalCommittedRegiments(
					any(), any(Integer.class), any(), any())).thenReturn(5);
			action.run();
		}
	}
}
