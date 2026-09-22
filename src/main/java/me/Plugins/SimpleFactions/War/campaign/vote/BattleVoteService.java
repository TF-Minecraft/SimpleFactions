package me.Plugins.SimpleFactions.War.campaign.vote;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;

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
		if (BattleScheduleService.isVoteCloseDue(war, CampaignClock.now())) {
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
		if (war == null || side == null || uuidToFaction == null) {
			return Map.of();
		}
		Map<Integer, Integer> counts = new LinkedHashMap<>();
		for (int hour : BattleWindowService.listValidHours()) {
			counts.put(hour, 0);
		}
		for (Map.Entry<UUID, Set<Integer>> entry : war.getBattleVotes().entrySet()) {
			Faction faction = uuidToFaction.apply(entry.getKey());
			BelligerentRole voterSide = BattleSideMembers.resolveSide(war, faction);
			if (voterSide != side) {
				continue;
			}
			Set<Integer> hours = entry.getValue();
			if (hours == null || hours.isEmpty()) {
				continue;
			}
			for (int hour : hours) {
				if (counts.containsKey(hour)) {
					counts.put(hour, counts.get(hour) + 1);
				}
			}
		}
		return counts;
	}

	public static Map<Integer, VoteResults.BattleHourTally> buildHourTally(War war, Function<UUID, Faction> uuidToFaction) {
		if (war == null || uuidToFaction == null) {
			return Map.of();
		}
		Map<Integer, Integer> attackerCounts = countVotesByHour(war, BelligerentRole.ATTACKER, uuidToFaction);
		Map<Integer, Integer> defenderCounts = countVotesByHour(war, BelligerentRole.DEFENDER, uuidToFaction);
		Map<Integer, VoteResults.BattleHourTally> tally = new LinkedHashMap<>();
		for (int hour : BattleWindowService.listValidHours()) {
			tally.put(
					hour,
					new VoteResults.BattleHourTally(
							attackerCounts.getOrDefault(hour, 0),
							defenderCounts.getOrDefault(hour, 0)));
		}
		return tally;
	}

	public static OptionalInt pickHour(War war, Function<UUID, Faction> uuidToFaction) {
		if (war == null || uuidToFaction == null) {
			return OptionalInt.empty();
		}
		Map<Integer, VoteResults.BattleHourTally> tally = buildHourTally(war, uuidToFaction);
		int bestHour = -1;
		int bestScore = -1;
		for (Map.Entry<Integer, VoteResults.BattleHourTally> entry : tally.entrySet()) {
			int hour = entry.getKey();
			VoteResults.BattleHourTally counts = entry.getValue();
			int score = Math.min(counts.attackerCount(), counts.defenderCount());
			if (score <= 0) {
				continue;
			}
			if (score > bestScore || (score == bestScore && (bestHour < 0 || hour < bestHour))) {
				bestScore = score;
				bestHour = hour;
			}
		}
		if (bestHour < 0) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(bestHour);
	}
}
