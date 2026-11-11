package net.tfminecraft.simplefactions.war.campaign.buildup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;

class BuildupServiceTest {
	/** Tuesday noon in the schedule zone. */
	private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");
	private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

	private boolean enabled;
	private int firstOptionDay;
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		enabled = Cache.warBuildupEnabled;
		firstOptionDay = Cache.warBuildupFirstOptionDay;
		Cache.warBuildupEnabled = true;
		Cache.warBuildupFirstOptionDay = 7;
		attacker = faction("atk");
		defender = faction("def");
	}

	@AfterEach
	void tearDown() {
		Cache.warBuildupEnabled = enabled;
		Cache.warBuildupFirstOptionDay = firstOptionDay;
	}

	@Test
	void start_putsAVotingWarIntoItsBuildupWithAFreshGridAWeekAhead() {
		War war = votingWar();
		war.setBattleDay(LocalDate.of(2026, 8, 19));
		war.getAvailability().setMarks(UUID.randomUUID(), TODAY, Set.of(21));

		assertTrue(BuildupService.start(war, NOW));

		assertTrue(war.isBuildup());
		assertTrue(war.isBuildupFrozen());
		assertEquals(BattleSchedulePhase.BUILDUP, war.getBattleSchedulePhase());
		assertEquals(BattleWindowService.scheduleDate(NOW).plusDays(7), war.getAvailability().getEarliestBattleDay());
		assertNull(war.getBattleDay());
		assertTrue(war.getAvailability().marks().isEmpty());
	}

	@Test
	void start_leavesWarsAloneWhenOffOrWithoutAVotingSchedule() {
		War war = votingWar();
		assertFalse(BuildupService.start(null, NOW));
		assertFalse(BuildupService.start(war, null));
		war.setBattleSchedulePhase(BattleSchedulePhase.IDLE);
		assertFalse(BuildupService.start(war, NOW));
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		Cache.warBuildupEnabled = false;
		assertFalse(BuildupService.start(war, NOW));
		assertFalse(war.isBuildup());
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
	}

	@Test
	void end_liftsTheFreezeAndLetsBattleDaysStartToday() {
		War war = votingWar();
		assertTrue(BuildupService.start(war, NOW));

		BuildupService.end(war, NOW);

		assertFalse(war.isBuildupFrozen());
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		assertEquals(TODAY, war.getAvailability().getEarliestBattleDay());
		assertNull(war.getBattleDay());
	}

	@Test
	void end_keepsALockedBattleDayAndAnEarlierEarliestDay() {
		War war = votingWar();
		assertTrue(BuildupService.start(war, NOW));
		LocalDate locked = TODAY.plusDays(8);
		war.getAvailability().putBattleDay(LockedBattleDay.open(locked, false));
		war.getAvailability().setEarliestBattleDay(TODAY.minusDays(1));

		BuildupService.end(war, NOW);

		assertEquals(TODAY.minusDays(1), war.getAvailability().getEarliestBattleDay());
		assertEquals(locked, war.getBattleDay());
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		war.getAvailability().setEarliestBattleDay(null);
		BuildupService.end(war, NOW);
		assertEquals(TODAY, war.getAvailability().getEarliestBattleDay());
	}

	private War votingWar() {
		War war = new War(1, attacker, defender);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		return war;
	}

	private static Faction faction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getName()).thenReturn(id);
		when(faction.getMembers()).thenReturn(List.of());
		return faction;
	}
}
