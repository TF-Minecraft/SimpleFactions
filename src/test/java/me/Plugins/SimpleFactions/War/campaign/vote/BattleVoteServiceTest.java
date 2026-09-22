package me.Plugins.SimpleFactions.War.campaign.vote;

import static me.Plugins.SimpleFactions.War.campaign.vote.VoteResults.BattleVoteToggleResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;

class BattleVoteServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);
	private Faction attacker;
	private Faction defender;
	private War war;
	private UUID attackerVoter;
	private UUID defenderVoter;

	@BeforeEach
	void setUp() {
		CampaignClock.reset();
		Cache.warVoteCloseHour = 16;
		Cache.warBattleWindowStartHour = 20;
		Cache.warBattleWindowEndHour = 24;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");

		war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);

		attackerVoter = UUID.randomUUID();
		defenderVoter = UUID.randomUUID();
	}

	@AfterEach
	void tearDown() {
		CampaignClock.reset();
	}

	@Test
	void toggleVote_allowsBeforeVoteClose() {
		votingWar();
		Instant beforeClose = BattleWindowService.atScheduleHour(BATTLE_DAY, 12);
		CampaignClock.add(Duration.between(Instant.now(), beforeClose));

		assertEquals(
				BattleVoteToggleResult.ADDED,
				BattleVoteService.toggleVote(war, attackerVoter, 21, attacker, true));
	}

	@Test
	void toggleVote_rejectsAfterVoteClose() {
		War votingWar = votingWar();
		Instant atClose = BattleWindowService.atScheduleHour(BATTLE_DAY, 16);
		CampaignClock.add(Duration.between(Instant.now(), atClose));

		assertEquals(
				BattleVoteToggleResult.REJECTED_VOTE_CLOSED,
				BattleVoteService.toggleVote(votingWar, attackerVoter, 21, attacker, true));
	}

	private War votingWar() {
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		return war;
	}

	@Test
	void toggleVote_addsAndRemovesHour() {
		assertEquals(
				BattleVoteToggleResult.ADDED,
				BattleVoteService.toggleVote(war, attackerVoter, 21, attacker, true));
		assertEquals(Set.of(21), BattleVoteService.getPlayerSelections(war, attackerVoter));

		assertEquals(
				BattleVoteToggleResult.REMOVED,
				BattleVoteService.toggleVote(war, attackerVoter, 21, attacker, true));
		assertTrue(BattleVoteService.getPlayerSelections(war, attackerVoter).isEmpty());
		assertFalse(war.getBattleVotes().containsKey(attackerVoter));
	}

	@Test
	void toggleVote_rejectsOfflineNonParticipantAndInvalidHour() {
		assertEquals(
				BattleVoteToggleResult.REJECTED_OFFLINE,
				BattleVoteService.toggleVote(war, attackerVoter, 21, attacker, false));
		assertEquals(
				BattleVoteToggleResult.REJECTED_NOT_PARTICIPANT,
				BattleVoteService.toggleVote(war, attackerVoter, 21, null, true));
		assertEquals(
				BattleVoteToggleResult.REJECTED_INVALID_HOUR,
				BattleVoteService.toggleVote(war, attackerVoter, 19, attacker, true));
	}

	@Test
	void pickHour_usesMinRuleAndEarliestTie() {
		addAttackerVotes(attackerVoter, 22, 23);
		addDefenderVotes(defenderVoter, 21, 22);

		Function<UUID, Faction> uuidToFaction = uuid -> {
			if (uuid.equals(attackerVoter)) {
				return attacker;
			}
			if (uuid.equals(defenderVoter)) {
				return defender;
			}
			return null;
		};

		assertEquals(OptionalInt.of(22), BattleVoteService.pickHour(war, uuidToFaction));
	}

	@Test
	void pickHour_prefersHigherMinScore() {
		UUID extraAttacker = UUID.randomUUID();
		addAttackerVotes(attackerVoter, 21);
		addAttackerVotes(extraAttacker, 22);
		addDefenderVotes(defenderVoter, 21);

		Function<UUID, Faction> uuidToFaction = uuid -> {
			if (uuid.equals(defenderVoter)) {
				return defender;
			}
			return attacker;
		};

		assertEquals(OptionalInt.of(21), BattleVoteService.pickHour(war, uuidToFaction));
	}

	@Test
	void pickHour_ignoresOutOfWindowStoredHours() {
		war.getBattleVotes().put(attackerVoter, Set.of(19, 21));
		war.getBattleVotes().put(defenderVoter, Set.of(19, 21));

		Function<UUID, Faction> uuidToFaction = uuid -> uuid.equals(attackerVoter) ? attacker : defender;

		assertEquals(OptionalInt.of(21), BattleVoteService.pickHour(war, uuidToFaction));
	}

	@Test
	void pickHour_emptyWhenNoCrossSideOverlap() {
		addAttackerVotes(attackerVoter, 21);
		Function<UUID, Faction> uuidToFaction = uuid -> attacker;
		assertTrue(BattleVoteService.pickHour(war, uuidToFaction).isEmpty());
	}

	private void addAttackerVotes(UUID uuid, int... hours) {
		war.getBattleVotes().put(uuid, hoursToSet(hours));
	}

	private void addDefenderVotes(UUID uuid, int... hours) {
		war.getBattleVotes().put(uuid, hoursToSet(hours));
	}

	private static Set<Integer> hoursToSet(int... hours) {
		Set<Integer> set = new HashSet<>();
		Arrays.stream(hours).forEach(set::add);
		return set;
	}
}
