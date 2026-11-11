package net.tfminecraft.simplefactions.war.campaign.availability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleAutoresolveService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleVoteReminders;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.history.WarHistoryEvents;
import net.tfminecraft.simplefactions.war.notice.WarNotices;
import net.tfminecraft.simplefactions.war.resolution.WarResolutionService;

/**
 * The availability rules on a war of four attackers and three defenders (test accounts), with the
 * Main window: start times 21:00 to 01:00, day lock 48 hours and time lock 24 hours before 21:00.
 */
class AvailabilitySchedulerTest {
	private static final LocalDate THU = LocalDate.of(2026, 11, 19);
	private static final LocalDate FRI = LocalDate.of(2026, 11, 20);
	private static final LocalDate SAT = LocalDate.of(2026, 11, 21);

	private final List<Object> saved = new ArrayList<>();
	private War war;
	private Faction attacker;
	private Faction defender;
	private MockedStatic<WarNotices> notices;
	private MockedStatic<BattleVoteReminders> reminders;
	private MockedStatic<WarHistoryEvents> history;
	private MockedStatic<InactivityService> inactivity;
	private MockedStatic<MercenaryEngagements> engagements;
	/** The battle is set on province 20 without preparing it; tests change what they need. */
	private MockedStatic<BattleScheduleService> schedule;

	@BeforeEach
	void setUp() {
		saved.addAll(List.of(Cache.warBattleWindowStartHour, Cache.warBattleWindowEndHour, Cache.warAvailabilityDayLockHours,
				Cache.warAvailabilityTimeLockHours, Cache.warAvailabilityMinPerSide, Cache.warAvailabilityMaxDaysWithoutBattle,
				Cache.warAvailabilityDaysShown));
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 26;
		Cache.warAvailabilityDayLockHours = 48;
		Cache.warAvailabilityTimeLockHours = 24;
		Cache.warAvailabilityMinPerSide = 3;
		Cache.warAvailabilityMaxDaysWithoutBattle = 3;
		Cache.warAvailabilityDaysShown = 7;
		BattleManager.resetForTests();
		attacker = faction("atk", "A1", "A2", "A3", "A4");
		defender = faction("def", "D1", "D2", "D3");
		war = new War(12, attacker, defender);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		notices = mockStatic(WarNotices.class);
		reminders = mockStatic(BattleVoteReminders.class);
		history = mockStatic(WarHistoryEvents.class);
		inactivity = mockStatic(InactivityService.class);
		inactivity.when(() -> InactivityService.activeOnly(any()))
				.thenAnswer(call -> new ArrayList<>((Collection<?>) call.getArgument(0)));
		engagements = mockStatic(MercenaryEngagements.class);
		engagements.when(() -> MercenaryEngagements.on(any(), any())).thenReturn(List.of());
		schedule = mockStatic(BattleScheduleService.class, CALLS_REAL_METHODS);
		schedule.when(() -> BattleScheduleService.resolveScheduledProvinceId(any())).thenReturn(20);
		schedule.when(() -> BattleScheduleService.scheduleBattleAtProvince(any(), anyInt(), any())).thenReturn(true);
	}

	@AfterEach
	void tearDown() {
		schedule.close();
		engagements.close();
		inactivity.close();
		history.close();
		reminders.close();
		notices.close();
		BattleManager.resetForTests();
		Cache.warBattleWindowStartHour = (Integer) saved.get(0);
		Cache.warBattleWindowEndHour = (Integer) saved.get(1);
		Cache.warAvailabilityDayLockHours = (Integer) saved.get(2);
		Cache.warAvailabilityTimeLockHours = (Integer) saved.get(3);
		Cache.warAvailabilityMinPerSide = (Integer) saved.get(4);
		Cache.warAvailabilityMaxDaysWithoutBattle = (Integer) saved.get(5);
		Cache.warAvailabilityDaysShown = (Integer) saved.get(6);
	}

	private static Faction faction(String id, String... members) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getName()).thenReturn(id);
		when(faction.getMembers()).thenReturn(List.of(members));
		return faction;
	}

	private void mark(String member, LocalDate date, Integer... hours) {
		war.getAvailability().setMarks(BattleScheduleLookups.spoofMemberUuid(member), date, Set.of(hours));
	}

	/** Enough of both sides at 22:00 (three attackers, all three defenders). */
	private void enoughAt22(LocalDate date) {
		for (String member : List.of("A1", "A2", "A3", "D1", "D2", "D3")) {
			mark(member, date, 22);
		}
	}

	private static Instant at(LocalDate date, int hour) {
		return BattleWindowService.atScheduleHour(date, hour);
	}

	private LockedBattleDay day(LocalDate date) {
		return war.getAvailability().battleDay(date);
	}

	@Test
	void onlyAWarWithABattleScheduleIsProcessed() {
		assertFalse(AvailabilityScheduler.process(war, null));
		assertFalse(AvailabilityScheduler.isScheduling(null));
		war.setBattleSchedulePhase(BattleSchedulePhase.IDLE);
		assertFalse(AvailabilityScheduler.process(war, at(FRI, 12)));
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.end(WarEndReason.ADMIN_END);
		assertFalse(AvailabilityScheduler.process(war, at(FRI, 12)));
	}

	@Test
	void aDateBecomesABattleDayAtItsDayLockWhenEnoughOfBothSidesCanComeAtOneTime() {
		war.getAvailability().setEarliestBattleDay(FRI);
		enoughAt22(FRI);
		Instant dayLock = AvailabilityCalendar.dayLockAt(FRI);

		assertFalse(AvailabilityScheduler.process(war, dayLock.minusSeconds(1)));
		assertTrue(AvailabilityScheduler.process(war, dayLock));

		assertEquals(LockedBattleDay.open(FRI, false), day(FRI));
		assertEquals(FRI, war.getBattleDay());
		assertEquals(FRI, war.getAvailability().getLastDayLockProcessed());
		assertEquals(0, war.getAvailability().getNoBattleStreak());
		history.verify(() -> WarHistoryEvents.battleDayLocked(war, FRI, AvailabilityCalendar.timeLockAt(FRI), false));
		history.verify(() -> WarHistoryEvents.votesChanged(war));
		reminders.verify(() -> BattleVoteReminders.battleDayLocked(war, FRI, dayLock));
		String announced = "§6⚔ Fri 20/11 §eis a battle day in " + war.getName() + "§e. Its start time is decided "
				+ "19/11/2026 21:00 CET / 15:00 EST§e.";
		notices.verify(() -> WarNotices.send("A1", announced));
		notices.verify(() -> WarNotices.send(eq("D3"), anyString()));
	}

	@Test
	void sidesNeedMarksAtTheSameHourAndASmallSideNeedsAllOfItself() {
		war.getAvailability().setEarliestBattleDay(FRI);
		for (String member : List.of("A1", "A2", "A3")) {
			mark(member, FRI, 21);
		}
		for (String member : List.of("D1", "D2", "D3")) {
			mark(member, FRI, 22);
		}
		assertTrue(AvailabilityScheduler.process(war, AvailabilityCalendar.dayLockAt(FRI)));
		assertNull(day(FRI), "Three of each side, but never at the same time");
		assertEquals(1, war.getAvailability().getNoBattleStreak());

		when(defender.getMembers()).thenReturn(List.of("D1", "D2"));
		for (String member : List.of("A1", "A2", "A3", "D1", "D2")) {
			mark(member, SAT, 23);
		}
		assertTrue(AvailabilityScheduler.process(war, AvailabilityCalendar.dayLockAt(SAT)));
		assertNotNull(day(SAT), "Both defenders were enough for a side of two");
	}

	@Test
	void theThirdDateInARowWithoutABattleBecomesOneAnyway() {
		war.getAvailability().setEarliestBattleDay(THU);
		mark("A1", SAT, 22);
		assertTrue(AvailabilityScheduler.process(war, AvailabilityCalendar.dayLockAt(FRI)));
		assertEquals(2, war.getAvailability().getNoBattleStreak());
		assertTrue(war.getAvailability().battleDays().isEmpty());

		assertTrue(AvailabilityScheduler.process(war, AvailabilityCalendar.dayLockAt(SAT)));

		assertEquals(LockedBattleDay.open(SAT, true), day(SAT));
		assertEquals(0, war.getAvailability().getNoBattleStreak());
		history.verify(() -> WarHistoryEvents.battleDayLocked(war, SAT, AvailabilityCalendar.timeLockAt(SAT), true));
	}

	@Test
	void withoutAFallbackDatesStayWithoutABattle() {
		Cache.warAvailabilityMaxDaysWithoutBattle = 0;
		war.getAvailability().setEarliestBattleDay(THU);
		assertTrue(AvailabilityScheduler.process(war, AvailabilityCalendar.dayLockAt(SAT.plusDays(2))));
		// Thursday's and Friday's windows had closed: only Saturday to Monday were decided.
		assertEquals(3, war.getAvailability().getNoBattleStreak());
		assertTrue(war.getAvailability().battleDays().isEmpty());
	}

	@Test
	void battlesCanFollowEachOtherButNoMoreThanTwoWait() {
		war.getAvailability().setEarliestBattleDay(THU);
		enoughAt22(THU);
		enoughAt22(FRI);
		enoughAt22(SAT);
		war.getAvailability().setNoBattleStreak(2);

		assertTrue(AvailabilityScheduler.process(war, AvailabilityCalendar.dayLockAt(SAT)));

		assertNotNull(day(THU));
		assertNotNull(day(FRI));
		assertNull(day(SAT), "Two battle days are already waiting");
		assertEquals(0, war.getAvailability().getNoBattleStreak());
		assertEquals(THU, war.getBattleDay());
	}

	@Test
	void aDayStaffAlreadyLockedIsLeftAsItIs() {
		war.getAvailability().setEarliestBattleDay(FRI);
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRI, false).withHour(23, false));
		war.getAvailability().setNoBattleStreak(2);

		assertTrue(AvailabilityScheduler.process(war, AvailabilityCalendar.dayLockAt(FRI)));

		assertEquals(23, day(FRI).hour());
		assertEquals(0, war.getAvailability().getNoBattleStreak());
		history.verify(() -> WarHistoryEvents.battleDayLocked(any(), any(), any(), anyBoolean()), never());
	}

	private static boolean anyBoolean() {
		return org.mockito.ArgumentMatchers.anyBoolean();
	}

	@Test
	void afterDowntimeDatesWhoseWindowClosedAreSkippedAndTheRestDecided() {
		war.getAvailability().setEarliestBattleDay(LocalDate.of(2026, 10, 1));
		enoughAt22(LocalDate.of(2026, 11, 17));
		// Wednesday noon: Monday's and Tuesday's windows are over, Wednesday's and Thursday's days are due.
		Instant wednesdayNoon = at(LocalDate.of(2026, 11, 18), 12);

		assertTrue(AvailabilityScheduler.process(war, wednesdayNoon));

		assertTrue(war.getAvailability().battleDays().isEmpty(), "Tuesday's marks came too late to matter");
		assertEquals(THU, war.getAvailability().getLastDayLockProcessed());
		assertEquals(2, war.getAvailability().getNoBattleStreak());
	}

	@Test
	void theFirstPassStartsTheGridTodayForAWarThatHadNoEarliestDay() {
		Instant mondayNoon = at(LocalDate.of(2026, 11, 16), 12);
		assertTrue(AvailabilityScheduler.process(war, mondayNoon));
		assertEquals(LocalDate.of(2026, 11, 16), war.getAvailability().getEarliestBattleDay());
	}

	@Test
	void theTimeLockFixesTheHourMostOfBothSidesCanMake() {
		war.getAvailability().setEarliestBattleDay(FRI);
		war.getAvailability().setLastDayLockProcessed(FRI);
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRI, false));
		war.setBattleDay(FRI);
		enoughAt22(FRI);
		mark("A4", FRI, 21, 24);
		mark("D1", FRI, 22, 24);
		Instant timeLock = AvailabilityCalendar.timeLockAt(FRI);
		{

			assertFalse(AvailabilityScheduler.process(war, timeLock.minusSeconds(1)));
			assertTrue(AvailabilityScheduler.process(war, timeLock));

			assertEquals(22, day(FRI).hour());
			schedule.verify(() -> BattleScheduleService.scheduleBattleAtProvince(war, 20, at(FRI, 22)));
		}
		notices.verify(() -> WarNotices.send(eq("A1"), contains("starts at 22:00 CET / 16:00 EST")));
	}

	@Test
	void aWindowWithNoStartHourLeavesTheTimeOpen() {
		Cache.warBattleWindowEndHour = 21;
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRI, false));
		war.getAvailability().setEarliestBattleDay(FRI);
		war.getAvailability().setLastDayLockProcessed(FRI);

		AvailabilityScheduler.process(war, AvailabilityCalendar.timeLockAt(FRI));

		assertNull(day(FRI).hour());
	}

	@Test
	void aForcedDayTakesTheMostMarkedHourAndIsAutoresolvedWhenASideNeverMarked() {
		war.getAvailability().setEarliestBattleDay(FRI);
		war.getAvailability().setLastDayLockProcessed(SAT);
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRI, true));
		war.getAvailability().putBattleDay(LockedBattleDay.open(SAT, true));
		mark("A1", FRI, 23, 25);
		mark("A2", FRI, 25);
		mark("D1", FRI, 21);
		mark("A1", SAT, 24);
		try (MockedStatic<BattleAutoresolveService> autoresolve = mockStatic(BattleAutoresolveService.class)) {

			assertTrue(AvailabilityScheduler.process(war, AvailabilityCalendar.timeLockAt(SAT)));

			assertEquals(new LockedBattleDay(FRI, 25, true, false), day(FRI), "Most marks, either side");
			assertEquals(new LockedBattleDay(SAT, 24, true, true), day(SAT), "No defender marked Saturday");
			autoresolve.verifyNoInteractions();
			schedule.verify(() -> BattleScheduleService.scheduleBattleAtProvince(war, 20, at(FRI, 25)));
		}
		notices.verify(() -> WarNotices.send(eq("A1"), contains("marked no time on Sat 21/11§e, so that battle is autoresolved.")));
	}

	@Test
	void anAutoresolvedDayIsResolvedWhenItIsNext() {
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRI, true).withHour(21, true));
		war.getAvailability().setEarliestBattleDay(FRI);
		war.getAvailability().setLastDayLockProcessed(FRI);
		try (MockedStatic<BattleAutoresolveService> autoresolve = mockStatic(BattleAutoresolveService.class)) {
			autoresolve.when(() -> BattleAutoresolveService.resolve(war)).thenReturn(true);

			assertTrue(AvailabilityScheduler.process(war, at(THU, 22)));

			autoresolve.verify(() -> BattleAutoresolveService.resolve(war));
			schedule.verify(() -> BattleScheduleService.scheduleBattleAtProvince(any(), anyInt(), any()), never());
		}
	}

	@Test
	void theNextBattleWaitsForAnOpenChoiceAndARunningBattleAndEndsAWarWithNothingLeft() {
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRI, false).withHour(21, false));
		war.getAvailability().setEarliestBattleDay(FRI);
		war.getAvailability().setLastDayLockProcessed(FRI);
		Instant now = at(THU, 22);
		try (MockedStatic<WarResolutionService> resolution = mockStatic(WarResolutionService.class)) {
			schedule.when(() -> BattleScheduleService.resolveScheduledProvinceId(war)).thenReturn(null);
			schedule.when(() -> BattleScheduleService.hasNoNextBattle(war)).thenReturn(false);
			AvailabilityScheduler.process(war, now);
			resolution.verifyNoInteractions();
			assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());

			Battle running = mock(Battle.class);
			when(running.hasStarted()).thenReturn(true);
			try (MockedStatic<BattleManager> battles = mockStatic(BattleManager.class)) {
				battles.when(() -> BattleManager.getByWarId(war.getId())).thenReturn(running);
				AvailabilityScheduler.process(war, now);
			}
			schedule.verify(() -> BattleScheduleService.resolveScheduledProvinceId(war), times(1));

			schedule.when(() -> BattleScheduleService.hasNoNextBattle(war)).thenReturn(true);
			assertTrue(AvailabilityScheduler.process(war, now));
			resolution.verify(() -> WarResolutionService.endWhitePeace(war));
		}
	}

	@Test
	void theDeadlineOfAnOpenChoiceIsAppliedAndAWarItEndsGoesNoFurther() {
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRI, false).withHour(21, false));
		war.getAvailability().setEarliestBattleDay(FRI);
		{
			schedule.when(() -> BattleScheduleService.applyPostBattleChoiceDeadline(eq(war), any())).thenAnswer(call -> {
				war.end(WarEndReason.WHITE_PEACE);
				return true;
			});

			assertTrue(AvailabilityScheduler.process(war, at(THU, 22)));

			schedule.verify(() -> BattleScheduleService.resolveScheduledProvinceId(war), never());
			history.verify(() -> WarHistoryEvents.votesChanged(war), never());
		}
	}

	@Test
	void hoursPastMidnightAndTheDaylightSavingChangeAreScheduledOnTheirOwnClock() {
		// Sunday 25 October 2026: summer time ends that morning.
		LocalDate sunday = LocalDate.of(2026, 10, 25);
		war.getAvailability().setEarliestBattleDay(sunday);
		for (String member : List.of("A1", "A2", "A3", "D1", "D2", "D3")) {
			mark(member, sunday, 25);
		}
		assertTrue(AvailabilityScheduler.process(war, Instant.parse("2026-10-23T19:00:00Z")));
		assertNotNull(day(sunday), "Decided at 21:00 CEST on Friday");
		{
			assertFalse(AvailabilityScheduler.process(war, Instant.parse("2026-10-24T18:59:00Z")));
			assertTrue(AvailabilityScheduler.process(war, Instant.parse("2026-10-24T19:00:00Z")));

			assertEquals(25, day(sunday).hour());
			// 01:00 CET on Monday, after the change.
			schedule.verify(() -> BattleScheduleService.scheduleBattleAtProvince(war, 20, Instant.parse("2026-10-26T00:00:00Z")));
		}
	}

	@Test
	void aBattleDayThatPassedUnfoughtIsDroppedUnlessItsBattleIsSet() {
		war.getAvailability().putBattleDay(LockedBattleDay.open(THU, false));
		war.getAvailability().putBattleDay(LockedBattleDay.open(SAT, false));
		war.getAvailability().setEarliestBattleDay(THU);
		war.getAvailability().setLastDayLockProcessed(SAT);
		war.setBattleDay(THU);
		Instant fridayNoon = at(FRI, 12);

		assertTrue(AvailabilityScheduler.process(war, fridayNoon));

		assertNull(day(THU));
		assertEquals(SAT, war.getBattleDay());

		war.getAvailability().putBattleDay(LockedBattleDay.open(THU, false).withHour(22, false));
		war.setBattleDay(THU);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		AvailabilityScheduler.process(war, fridayNoon);
		assertNotNull(day(THU), "The launcher owns a battle that is set");
		assertEquals(THU, war.getBattleDay());
	}

	@Test
	void aBuildupWithNoBattleDayStaysInItsPhaseUntilOneIsLocked() {
		war.setBuildup(true);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		assertTrue(AvailabilityScheduler.sync(war));
		assertEquals(BattleSchedulePhase.BUILDUP, war.getBattleSchedulePhase());
		assertFalse(AvailabilityScheduler.sync(war));
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRI, false));
		assertTrue(AvailabilityScheduler.sync(war));
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		war.setBattleSchedulePhase(BattleSchedulePhase.AUTORESOLVE_PENDING);
		assertFalse(AvailabilityScheduler.sync(war));

		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.getSignupRemindersSent().add(30);
		war.getAvailability().removeBattleDay(FRI);
		war.getAvailability().putBattleDay(LockedBattleDay.open(SAT, false));
		assertTrue(AvailabilityScheduler.sync(war));
		assertEquals(SAT, war.getBattleDay());
		assertTrue(war.getSignupRemindersSent().isEmpty(), "What belonged to Friday is cleared");
	}

	@Test
	void staffLockAndUnlockDays() {
		Instant now = at(LocalDate.of(2026, 11, 17), 12);
		assertNull(AvailabilityScheduler.lockDay(war, SAT, now));
		assertEquals(SAT, war.getBattleDay());
		reminders.verify(() -> BattleVoteReminders.battleDayLocked(war, SAT, now));
		assertEquals("That day is already a battle day.", AvailabilityScheduler.lockDay(war, SAT, now));
		assertEquals("That day is in the past.", AvailabilityScheduler.lockDay(war, LocalDate.of(2026, 11, 16), now));

		assertEquals("That day is not a battle day.", AvailabilityScheduler.unlockDay(war, FRI));
		assertNull(AvailabilityScheduler.unlockDay(war, SAT));
		assertNull(war.getBattleDay());
	}

	@Test
	void staffSetTheTimeOfALaterBattleDayWithoutTouchingTheNextOne() {
		Instant now = at(LocalDate.of(2026, 11, 17), 12);
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRI, false));
		war.getAvailability().putBattleDay(LockedBattleDay.open(SAT, false));
		war.setBattleDay(FRI);
		war.setBattleSchedulePhase(BattleSchedulePhase.AUTORESOLVE_PENDING);

		assertNull(AvailabilityScheduler.setHour(war, SAT, 23, now));
		assertEquals(23, day(SAT).hour());
		assertEquals("That day's battle is being autoresolved.", AvailabilityScheduler.setHour(war, FRI, 23, now));
		assertEquals("That time has already passed.", AvailabilityScheduler.setHour(war, SAT, 21, at(SAT, 22)));
		assertEquals("That day is not a battle day.", AvailabilityScheduler.setHour(war, THU, 21, now));
		assertTrue(AvailabilityScheduler.skip(war));
		assertEquals(SAT, war.getBattleDay());
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		history.verify(() -> WarHistoryEvents.postponed(war));
	}

	@Test
	void everyoneHearsOfBattleDaysIncludingTheMercenariesTheyHired() {
		MercenaryCompany company = mock(MercenaryCompany.class);
		when(company.getEnlisted()).thenReturn(List.of("Merc", "A1"));
		engagements.when(() -> MercenaryEngagements.on(war, war.getDefenders()))
				.thenReturn(List.of(new MercenaryEngagements.Engagement(company, null)));

		AvailabilityScheduler.tellEveryone(war, "news");

		for (String member : List.of("A1", "A2", "A3", "A4", "D1", "D2", "D3", "Merc")) {
			notices.verify(() -> WarNotices.send(member, "news"));
		}
		notices.verify(() -> WarNotices.send("A1", "news"), times(1));
	}

	@Test
	void devMarksAndTheStaffSummary() {
		assertEquals(4, AvailabilityScheduler.castMarks(war, FRI, 22, BelligerentRole.ATTACKER));
		assertEquals(3, AvailabilityScheduler.castMarks(war, FRI, 22, BelligerentRole.DEFENDER));
		assertEquals(0, AvailabilityScheduler.castMarks(war, FRI, 22, BelligerentRole.ATTACKER, BelligerentRole.DEFENDER));
		war.getAvailability().setEarliestBattleDay(FRI);
		war.getAvailability().putBattleDay(LockedBattleDay.open(FRI, true).withHour(25, true));
		war.getAvailability().setNoBattleStreak(1);

		List<String> lines = AvailabilityScheduler.describe(war, at(LocalDate.of(2026, 11, 17), 12));

		assertEquals("§eAvailability of war 12 §7(need 3 attackers and 3 defenders at one time; 1 days in a row without"
				+ " a battle)", lines.get(0));
		assertEquals("§6Battle day 2026-11-20§7: 01:00 CET, forced, autoresolved", lines.get(1));
		assertEquals("§f2026-11-20 §7battle: 21:00 0/0 22:00 4/3 23:00 0/0 00:00 0/0 01:00 0/0", lines.get(2));
		assertTrue(lines.get(lines.size() - 1).startsWith("§7Next lock: §f"));
	}
}
