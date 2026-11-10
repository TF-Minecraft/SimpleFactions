package net.tfminecraft.simplefactions.war.campaign.buildup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.BattleHourTally;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.BattleVoteToggleResult;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;

class BuildupServiceTest {
	/** Tuesday noon in the schedule zone: well before the day's vote close hour. */
	private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

	private final Map<String, Object> saved = new HashMap<>();
	private Faction attacker;
	private Faction defender;
	private Faction outsider;
	private final UUID alice = UUID.randomUUID();
	private final UUID bob = UUID.randomUUID();
	private final UUID carol = UUID.randomUUID();
	private Function<UUID, Faction> factions;
	private Function<String, UUID> accounts;

	@BeforeEach
	void setUp() {
		saved.put("enabled", Cache.warBuildupEnabled);
		saved.put("first", Cache.warBuildupFirstOptionDay);
		saved.put("options", Cache.warBuildupDayOptions);
		saved.put("close", Cache.warBuildupDayVoteCloseHours);
		saved.put("min", Cache.warBattleVotingMinPlayers);
		saved.put("full", Cache.warBattleVotingRequireSmallestSideFull);
		saved.put("either", Cache.warBattleVotingPassIfEither);
		saved.put("voteClose", Cache.warVoteCloseHour);
		Cache.warBuildupEnabled = true;
		Cache.warBuildupFirstOptionDay = 7;
		Cache.warBuildupDayOptions = 5;
		Cache.warBuildupDayVoteCloseHours = 72;
		Cache.warBattleVotingMinPlayers = 4;
		Cache.warBattleVotingRequireSmallestSideFull = true;
		Cache.warBattleVotingPassIfEither = true;
		Cache.warVoteCloseHour = 16;
		attacker = faction("atk", List.of("Alice", "Bob"));
		defender = faction("def", List.of("Carol"));
		outsider = faction("other", List.of("Olga"));
		factions = uuid -> uuid.equals(carol) ? defender : attacker;
		accounts = name -> switch (name) {
			case "Alice" -> alice;
			case "Bob" -> bob;
			case "Carol" -> carol;
			default -> null;
		};
	}

	@AfterEach
	void tearDown() {
		Cache.warBuildupEnabled = (Boolean) saved.get("enabled");
		Cache.warBuildupFirstOptionDay = (Integer) saved.get("first");
		Cache.warBuildupDayOptions = (Integer) saved.get("options");
		Cache.warBuildupDayVoteCloseHours = (Integer) saved.get("close");
		Cache.warBattleVotingMinPlayers = (Integer) saved.get("min");
		Cache.warBattleVotingRequireSmallestSideFull = (Boolean) saved.get("full");
		Cache.warBattleVotingPassIfEither = (Boolean) saved.get("either");
		Cache.warVoteCloseHour = (Integer) saved.get("voteClose");
	}

	@Test
	void start_putsAVotingWarIntoItsBuildupWithAFreshDayVote() {
		War war = votingWar();
		war.setBattleDay(LocalDate.of(2026, 8, 19));
		war.getBattleVotes().put(alice, Set.of(21));
		war.getDayVotes().put(bob, Set.of(1));

		assertTrue(BuildupService.start(war, NOW));

		assertTrue(war.isBuildup());
		assertTrue(war.isBuildupFrozen());
		assertEquals(BattleSchedulePhase.BUILDUP, war.getBattleSchedulePhase());
		assertEquals(BattleWindowService.scheduleDate(NOW).plusDays(7), war.getDayVoteFirstDay());
		assertEquals(NOW.plus(72, ChronoUnit.HOURS), war.getDayVoteClosesAt());
		assertNull(war.getBattleDay());
		assertTrue(war.getBattleVotes().isEmpty());
		assertTrue(war.getDayVotes().isEmpty());
		assertTrue(BuildupService.isDayVoteOpen(war));
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
	void theDayVoteIsOpenOnlyForAnActiveWarStillInItsBuildupPhase() {
		War war = buildupWar();
		assertFalse(BuildupService.isDayVoteOpen(null));
		war.setDayVoteFirstDay(null);
		assertFalse(BuildupService.isDayVoteOpen(war));
		assertNull(BuildupService.dayOf(war, 0));
		war = buildupWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		assertFalse(BuildupService.isDayVoteOpen(war));
		war = buildupWar();
		war.end(WarEndReason.ADMIN_END);
		assertFalse(BuildupService.isDayVoteOpen(war));
	}

	@Test
	void dayOptionsAreOffsetsFromTheFirstDayOffered() {
		Cache.warBuildupDayOptions = 3;
		War war = buildupWar();

		assertEquals(List.of(0, 1, 2), BuildupService.dayOptions());
		assertEquals(war.getDayVoteFirstDay().plusDays(2), BuildupService.dayOf(war, 2));
	}

	@Test
	void toggleDayVote_addsAndRemovesDaysForParticipantsOnly() {
		War war = buildupWar();

		assertTrue(BuildupService.canToggleDayVote(war, attacker));
		assertFalse(BuildupService.canToggleDayVote(war, outsider));
		assertFalse(BuildupService.canToggleDayVote(war, null));
		assertEquals(BattleVoteToggleResult.ADDED, BuildupService.toggleDayVote(war, alice, 1, attacker, true));
		assertEquals(BattleVoteToggleResult.ADDED, BuildupService.toggleDayVote(war, alice, 3, attacker, true));
		assertEquals(Set.of(1, 3), BuildupService.getPlayerSelections(war, alice));
		assertEquals(BattleVoteToggleResult.REMOVED, BuildupService.toggleDayVote(war, alice, 1, attacker, true));
		assertEquals(Set.of(3), BuildupService.getPlayerSelections(war, alice));
		assertEquals(BattleVoteToggleResult.REMOVED, BuildupService.toggleDayVote(war, alice, 3, attacker, true));
		assertFalse(war.getDayVotes().containsKey(alice), "An empty pick set is dropped");
		assertEquals(Set.of(), BuildupService.getPlayerSelections(war, alice));
		assertEquals(Set.of(), BuildupService.getPlayerSelections(null, alice));
		assertEquals(Set.of(), BuildupService.getPlayerSelections(war, null));
	}

	@Test
	void toggleDayVote_rejectsEveryoneWhoCannotVote() {
		War war = buildupWar();

		assertEquals(BattleVoteToggleResult.REJECTED_NOT_PARTICIPANT,
				BuildupService.toggleDayVote(null, alice, 0, attacker, true));
		assertEquals(BattleVoteToggleResult.REJECTED_NOT_PARTICIPANT,
				BuildupService.toggleDayVote(war, null, 0, attacker, true));
		assertEquals(BattleVoteToggleResult.REJECTED_OFFLINE,
				BuildupService.toggleDayVote(war, alice, 0, attacker, false));
		assertEquals(BattleVoteToggleResult.REJECTED_NOT_PARTICIPANT,
				BuildupService.toggleDayVote(war, alice, 0, null, true));
		assertEquals(BattleVoteToggleResult.REJECTED_NOT_PARTICIPANT,
				BuildupService.toggleDayVote(war, alice, 0, outsider, true));
		assertEquals(BattleVoteToggleResult.REJECTED_INVALID_HOUR,
				BuildupService.toggleDayVote(war, alice, -1, attacker, true));
		assertEquals(BattleVoteToggleResult.REJECTED_INVALID_HOUR,
				BuildupService.toggleDayVote(war, alice, 5, attacker, true));
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		assertEquals(BattleVoteToggleResult.REJECTED_VOTE_CLOSED,
				BuildupService.toggleDayVote(war, alice, 0, attacker, true));
		assertTrue(war.getDayVotes().isEmpty());
	}

	@Test
	void buildDayTally_countsEachSidePerOfferedDay() {
		War war = buildupWar();
		war.getDayVotes().put(alice, Set.of(0, 2));
		war.getDayVotes().put(bob, Set.of(2));
		war.getDayVotes().put(carol, Set.of(2, 4, 9));

		Map<Integer, BattleHourTally> tally = BuildupService.buildDayTally(war, factions);

		assertEquals(List.of(0, 1, 2, 3, 4), List.copyOf(tally.keySet()));
		assertEquals(new BattleHourTally(1, 0), tally.get(0));
		assertEquals(new BattleHourTally(2, 1), tally.get(2));
		assertEquals(new BattleHourTally(0, 1), tally.get(4));
	}

	@Test
	void closeDayVote_withQuorumPicksTheDayBothSidesCanBestMake() {
		War war = buildupWar();
		war.getDayVotes().put(alice, Set.of(0, 2));
		war.getDayVotes().put(bob, Set.of(2, 3));
		war.getDayVotes().put(carol, Set.of(2, 3));

		BuildupService.DayVoteResult result = BuildupService.closeDayVote(war, NOW, factions, accounts);

		LocalDate expected = BattleWindowService.scheduleDate(NOW).plusDays(9);
		assertEquals(new BuildupService.DayVoteResult(expected, true), result);
		assertEquals(expected, war.getBattleDay());
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		assertNull(war.getDayVoteFirstDay());
		assertNull(war.getDayVoteClosesAt());
		assertTrue(war.getDayVotes().isEmpty());
		assertTrue(war.isBuildupFrozen(), "The freeze holds until the first battle starts");
		assertFalse(BuildupService.isDayVoteOpen(war));
	}

	@Test
	void closeDayVote_fallsBackToTheFirstDayWithoutQuorumOrAnAgreedDay() {
		War noQuorum = buildupWar();
		noQuorum.getDayVotes().put(alice, Set.of(3));
		LocalDate first = noQuorum.getDayVoteFirstDay();

		BuildupService.DayVoteResult result = BuildupService.closeDayVote(noQuorum, NOW, factions, accounts);

		assertEquals(new BuildupService.DayVoteResult(first, false), result);

		War noOverlap = buildupWar();
		noOverlap.getDayVotes().put(alice, Set.of(1));
		noOverlap.getDayVotes().put(bob, Set.of(1));
		noOverlap.getDayVotes().put(carol, Set.of(4));

		BuildupService.DayVoteResult disagreed = BuildupService.closeDayVote(noOverlap, NOW, factions, accounts);

		assertTrue(disagreed.quorum());
		assertEquals(first, disagreed.day());
	}

	@Test
	void closeDayVoteIfDue_waitsForItsClosingTime() {
		War war = buildupWar();
		Instant closes = war.getDayVoteClosesAt();

		assertFalse(BuildupService.closeDayVoteIfDue(war, closes.minusSeconds(1)));
		assertFalse(BuildupService.closeDayVoteIfDue(war, null));
		assertFalse(BuildupService.closeDayVoteIfDue(null, closes));
		assertTrue(BuildupService.isDayVoteOpen(war));

		assertTrue(BuildupService.closeDayVoteIfDue(war, closes));

		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		assertFalse(BuildupService.closeDayVoteIfDue(war, closes.plusSeconds(60)));

		War untimed = buildupWar();
		untimed.setDayVoteClosesAt(null);
		assertTrue(BuildupService.closeDayVoteIfDue(untimed, NOW), "A vote without a closing time closes at once");
	}

	@Test
	void setFirstBattleDay_neverPicksAPastDayAndTellsBothSides() {
		War war = buildupWar();
		Player online = mock(Player.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);
			bukkit.when(() -> Bukkit.getPlayerExact("Carol")).thenReturn(online);

			BuildupService.DayVoteResult result =
					BuildupService.setFirstBattleDay(war, LocalDate.of(2020, 1, 1), NOW, true);

			assertEquals(BattleScheduleService.firstVotableBattleDay(null, NOW), result.day());
			assertEquals(result.day(), war.getBattleDay());
			verify(online).sendMessage(org.mockito.ArgumentMatchers.startsWith("§eThe first battle of "));
		}
	}

	@Test
	void end_liftsTheFreezeAndClosesAnOpenDayVoteOnTheNextBattleDay() {
		War voting = buildupWar();

		BuildupService.end(voting, NOW);

		assertFalse(voting.isBuildupFrozen());
		assertEquals(BattleSchedulePhase.VOTING, voting.getBattleSchedulePhase());
		assertEquals(BattleScheduleService.firstVotableBattleDay(BattleWindowService.scheduleDate(NOW), NOW),
				voting.getBattleDay());

		War chosen = buildupWar();
		BuildupService.closeDayVote(chosen, NOW, factions, accounts);
		LocalDate day = chosen.getBattleDay();

		BuildupService.end(chosen, NOW);

		assertFalse(chosen.isBuildupFrozen());
		assertEquals(day, chosen.getBattleDay(), "A chosen first battle day stays");
	}

	private War votingWar() {
		War war = new War(1, attacker, defender);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		return war;
	}

	private War buildupWar() {
		War war = votingWar();
		assertTrue(BuildupService.start(war, NOW));
		return war;
	}

	private static Faction faction(String id, List<String> members) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getName()).thenReturn(id);
		when(faction.getMembers()).thenReturn(members);
		return faction;
	}
}
