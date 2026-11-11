package net.tfminecraft.simplefactions.war.campaign.availability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;

/** Wars saved with the old day and hour votes keep every vote as availability. */
class AvailabilityMigrationTest {
	private static final LocalDate FIRST = LocalDate.of(2026, 11, 17);
	private static final Set<Integer> EVERY_HOUR = Set.of(21, 22, 23, 24, 25);

	private int[] saved;
	private final UUID ann = UUID.randomUUID();
	private final UUID dee = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		saved = new int[] {Cache.warBattleWindowStartHour, Cache.warBattleWindowEndHour};
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 26;
	}

	@AfterEach
	void tearDown() {
		Cache.warBattleWindowStartHour = saved[0];
		Cache.warBattleWindowEndHour = saved[1];
	}

	private static War war(BattleSchedulePhase phase, LocalDate battleDay) {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		War war = new War(9, attacker, defender);
		war.setBattleSchedulePhase(phase);
		war.setBattleDay(battleDay);
		return war;
	}

	@Test
	void aBuildupDayVoteBecomesMarksAtEveryHourOfTheDaysPicked() {
		War war = war(BattleSchedulePhase.BUILDUP, null);

		AvailabilityMigration.migrate(war, Map.of(ann, Set.of(0, 3), dee, Set.of(3)), FIRST, Map.of());

		assertEquals(FIRST, war.getAvailability().getEarliestBattleDay(), "The grid starts at the old vote's first day");
		assertEquals(EVERY_HOUR, war.getAvailability().marksOn(ann, FIRST));
		assertEquals(EVERY_HOUR, war.getAvailability().marksOn(ann, FIRST.plusDays(3)));
		assertEquals(EVERY_HOUR, war.getAvailability().marksOn(dee, FIRST.plusDays(3)));
		assertTrue(war.getAvailability().battleDays().isEmpty());
		assertNull(war.getBattleDay());
		assertEquals(BattleSchedulePhase.BUILDUP, war.getBattleSchedulePhase());
	}

	@Test
	void aVotingWarKeepsItsBattleDayLockedAndItsHourVotesAsMarks() {
		LocalDate day = LocalDate.of(2026, 11, 20);
		War war = war(BattleSchedulePhase.VOTING, day);

		AvailabilityMigration.migrate(war, Map.of(), null, Map.of(ann, Set.of(21, 25, 30), dee, Set.of(19)));

		assertEquals(List.of(LockedBattleDay.open(day, false)), war.getAvailability().battleDays());
		assertEquals(Set.of(21, 25), war.getAvailability().marksOn(ann, day), "Hours outside the window drop out");
		assertTrue(war.getAvailability().marksOn(dee, day).isEmpty());
		assertEquals(day, war.getAvailability().getEarliestBattleDay());
		assertEquals(day, war.getBattleDay());
	}

	@Test
	void aScheduledWarKeepsItsDayAndHour() {
		LocalDate day = LocalDate.of(2026, 11, 20);
		War war = war(BattleSchedulePhase.SCHEDULED, day);
		war.setScheduledBattleHour(24);

		AvailabilityMigration.migrate(war, Map.of(), null, Map.of(ann, Set.of(24)));

		assertEquals(List.of(new LockedBattleDay(day, 24, false, false)), war.getAvailability().battleDays());
		assertEquals(Set.of(24), war.getAvailability().marksOn(ann, day));
	}

	@Test
	void aBattleDayVotedOnInTheBuildupMergesBothVotes() {
		War war = war(BattleSchedulePhase.AUTORESOLVE_PENDING, FIRST.plusDays(1));
		war.setScheduledBattleHour(0);

		AvailabilityMigration.migrate(war, Map.of(ann, Set.of(1)), FIRST, Map.of(ann, Set.of(22)));

		assertEquals(List.of(LockedBattleDay.open(FIRST.plusDays(1), false)), war.getAvailability().battleDays());
		assertEquals(EVERY_HOUR, war.getAvailability().marksOn(ann, FIRST.plusDays(1)));
		assertEquals(FIRST, war.getAvailability().getEarliestBattleDay());
	}

	@Test
	void aWarWithoutAScheduleOrBattleDayHasNoBattleDays() {
		War idle = war(BattleSchedulePhase.IDLE, LocalDate.of(2026, 11, 20));
		AvailabilityMigration.migrate(idle, Map.of(), null, Map.of(ann, Set.of(21)));
		assertNull(idle.getBattleDay());
		assertTrue(idle.getAvailability().marks().isEmpty());
		assertNull(idle.getAvailability().getEarliestBattleDay());

		War dayless = war(BattleSchedulePhase.VOTING, null);
		AvailabilityMigration.migrate(dayless, Map.of(), null, Map.of());
		assertTrue(dayless.getAvailability().battleDays().isEmpty());
	}
}
