package net.tfminecraft.simplefactions.war.campaign.vote;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;

public final class BattleVoteService {
	private BattleVoteService() {}

	public static VoteResults.BattleVoteToggleResult toggleVote(
			War war,
			UUID playerId,
			int hour,
			Faction playerFaction,
			boolean playerOnline) {
		if (war == null || playerId == null) {
			return VoteResults.BattleVoteToggleResult.REJECTED_NOT_PARTICIPANT;
		}
		if (!playerOnline) {
			return VoteResults.BattleVoteToggleResult.REJECTED_OFFLINE;
		}
		if (playerFaction == null || war.getSide(playerFaction) == null) {
			return VoteResults.BattleVoteToggleResult.REJECTED_NOT_PARTICIPANT;
		}
		if (!war.isActive()
				|| war.getBattleSchedulePhase() != net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase.VOTING
				|| BattleScheduleService.isVoteCloseDue(war, CampaignClock.now())) {
			return VoteResults.BattleVoteToggleResult.REJECTED_VOTE_CLOSED;
		}
		if (!BattleWindowService.isValidHour(hour)) {
			return VoteResults.BattleVoteToggleResult.REJECTED_INVALID_HOUR;
		}

		Map<UUID, Set<Integer>> votes = war.getBattleVotes();
		Set<Integer> selections = votes.computeIfAbsent(playerId, ignored -> new HashSet<>());
		if (selections.contains(hour)) {
			selections.remove(hour);
			if (selections.isEmpty()) {
				votes.remove(playerId);
			}
			return VoteResults.BattleVoteToggleResult.REMOVED;
		}
		selections.add(hour);
		return VoteResults.BattleVoteToggleResult.ADDED;
	}

	public static Set<Integer> getPlayerSelections(War war, UUID playerId) {
		if (war == null || playerId == null) {
			return Set.of();
		}
		Set<Integer> selections = war.getBattleVotes().get(playerId);
		if (selections == null || selections.isEmpty()) {
			return Set.of();
		}
		Set<Integer> copy = new HashSet<>();
		for (int hour : selections) {
			if (BattleWindowService.isValidHour(hour)) {
				copy.add(hour);
			}
		}
		return Collections.unmodifiableSet(copy);
	}

	public static void clearVotes(War war) {
		if (war == null) {
			return;
		}
		war.getBattleVotes().clear();
	}

	public static Map<Integer, Integer> countVotesByHour(
			War war,
			BelligerentRole side,
			Function<UUID, Faction> uuidToFaction) {
		if (war == null) {
			return Map.of();
		}
		return countVotes(war, war.getBattleVotes(), BattleWindowService.listValidHours(), side, uuidToFaction);
	}

	/** One side's votes per option; picks outside {@code options} are ignored. Shared by the hour and day votes. */
	public static Map<Integer, Integer> countVotes(
			War war,
			Map<UUID, Set<Integer>> votes,
			List<Integer> options,
			BelligerentRole side,
			Function<UUID, Faction> uuidToFaction) {
		if (war == null || side == null || uuidToFaction == null) {
			return Map.of();
		}
		Map<Integer, Integer> counts = new LinkedHashMap<>();
		for (int option : options) {
			counts.put(option, 0);
		}
		for (Map.Entry<UUID, Set<Integer>> entry : votes.entrySet()) {
			Faction faction = uuidToFaction.apply(entry.getKey());
			BelligerentRole voterSide = BattleSideMembers.resolveSide(war, faction);
			if (voterSide != side) {
				continue;
			}
			Set<Integer> picks = entry.getValue();
			if (picks == null || picks.isEmpty()) {
				continue;
			}
			for (int pick : picks) {
				if (counts.containsKey(pick)) {
					counts.put(pick, counts.get(pick) + 1);
				}
			}
		}
		return counts;
	}

	/** Attacker and defender counts per option, in option order. */
	public static Map<Integer, VoteResults.BattleHourTally> buildTally(
			War war,
			Map<UUID, Set<Integer>> votes,
			List<Integer> options,
			Function<UUID, Faction> uuidToFaction) {
		if (war == null || uuidToFaction == null) {
			return Map.of();
		}
		Map<Integer, Integer> attackerCounts = countVotes(war, votes, options, BelligerentRole.ATTACKER, uuidToFaction);
		Map<Integer, Integer> defenderCounts = countVotes(war, votes, options, BelligerentRole.DEFENDER, uuidToFaction);
		Map<Integer, VoteResults.BattleHourTally> tally = new LinkedHashMap<>();
		for (int option : options) {
			tally.put(option, new VoteResults.BattleHourTally(
					attackerCounts.getOrDefault(option, 0),
					defenderCounts.getOrDefault(option, 0)));
		}
		return tally;
	}

	/**
	 * The option both sides can best make: the highest {@code min(attackers, defenders)}, ties to the
	 * earliest option. Empty when no option has a vote from each side.
	 */
	public static OptionalInt pickBest(Map<Integer, VoteResults.BattleHourTally> tally) {
		int best = -1;
		int bestScore = -1;
		for (Map.Entry<Integer, VoteResults.BattleHourTally> entry : tally.entrySet()) {
			int option = entry.getKey();
			VoteResults.BattleHourTally counts = entry.getValue();
			int score = Math.min(counts.attackerCount(), counts.defenderCount());
			if (score <= 0) {
				continue;
			}
			if (score > bestScore || (score == bestScore && (best < 0 || option < best))) {
				bestScore = score;
				best = option;
			}
		}
		return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
	}

	public static Map<Integer, VoteResults.BattleHourTally> buildHourTally(War war, Function<UUID, Faction> uuidToFaction) {
		if (war == null) {
			return Map.of();
		}
		return buildTally(war, war.getBattleVotes(), BattleWindowService.listValidHours(), uuidToFaction);
	}

	public static OptionalInt pickHour(War war, Function<UUID, Faction> uuidToFaction) {
		if (war == null || uuidToFaction == null) {
			return OptionalInt.empty();
		}
		return pickBest(buildHourTally(war, uuidToFaction));
	}
}
