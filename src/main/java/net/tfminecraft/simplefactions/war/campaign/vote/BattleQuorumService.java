package net.tfminecraft.simplefactions.war.campaign.vote;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;

public final class BattleQuorumService {
	private BattleQuorumService() {}

	public static int countDistinctVoters(War war) {
		return war == null ? 0 : countDistinctVoters(war.getBattleVotes());
	}

	public static int countDistinctVoters(Map<UUID, Set<Integer>> votes) {
		int count = 0;
		for (Set<Integer> hours : votes.values()) {
			if (hours != null && !hours.isEmpty()) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Voters whose vote counts now, by the same lookup the tally uses: a vote left by someone who has
	 * since left the side, or whose mercenary contract ended, does not count toward quorum.
	 */
	static int countSideVoters(War war, Map<UUID, Set<Integer>> votes, Function<UUID, Faction> uuidToFaction) {
		int count = 0;
		for (Map.Entry<UUID, Set<Integer>> entry : votes.entrySet()) {
			if (entry.getValue() != null && !entry.getValue().isEmpty()
					&& BattleSideMembers.resolveSide(war, uuidToFaction.apply(entry.getKey())) != null) {
				count++;
			}
		}
		return count;
	}

	public static boolean isSmallestSideFullyRepresented(War war, Function<String, UUID> memberNameToUuid) {
		return war != null && isSmallestSideFullyRepresented(war, war.getBattleVotes(), memberNameToUuid);
	}

	public static boolean isSmallestSideFullyRepresented(
			War war, Map<UUID, Set<Integer>> votes, Function<String, UUID> memberNameToUuid) {
		if (war == null || memberNameToUuid == null) {
			return false;
		}
		Side attackers = war.getAttackers();
		Side defenders = war.getDefenders();

		int attackerEligible = BattleSideMembers.countEligibleMembers(attackers);
		int defenderEligible = BattleSideMembers.countEligibleMembers(defenders);
		Side smallerSide = attackerEligible <= defenderEligible ? attackers : defenders;
		Set<String> members = BattleSideMembers.collectEligibleMemberNames(smallerSide);
		if (members.isEmpty()) {
			return false;
		}

		for (String memberName : members) {
			UUID uuid = memberNameToUuid.apply(memberName);
			if (uuid == null) {
				return false;
			}
			Set<Integer> hours = votes.get(uuid);
			if (hours == null || hours.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public static int effectiveMinPlayers() {
		if (Cache.warBattleVotingDevMinPlayersEnabled
				&& Cache.warBattleVotingDevMinPlayers < Cache.warBattleVotingMinPlayers) {
			return Cache.warBattleVotingDevMinPlayers;
		}
		return Cache.warBattleVotingMinPlayers;
	}

	public static VoteResults.QuorumResult meetsQuorum(War war, Function<String, UUID> memberNameToUuid) {
		if (war == null) {
			return new VoteResults.QuorumResult(false, false, false, 0, 0, 0);
		}
		return meetsQuorum(war, war.getBattleVotes(), memberNameToUuid);
	}

	/** The hour vote's quorum applied to any vote of the war's members, such as the buildup's day vote. */
	public static VoteResults.QuorumResult meetsQuorum(
			War war, Map<UUID, Set<Integer>> votes, Function<String, UUID> memberNameToUuid) {
		return quorum(war, votes, memberNameToUuid, countDistinctVoters(votes));
	}

	/** Quorum counting only the voters the tally counts (see {@link #countSideVoters}). */
	public static VoteResults.QuorumResult meetsQuorum(
			War war,
			Map<UUID, Set<Integer>> votes,
			Function<String, UUID> memberNameToUuid,
			Function<UUID, Faction> uuidToFaction) {
		return quorum(war, votes, memberNameToUuid, countSideVoters(war, votes, uuidToFaction));
	}

	private static VoteResults.QuorumResult quorum(
			War war, Map<UUID, Set<Integer>> votes, Function<String, UUID> memberNameToUuid, int distinctVoters) {
		int attackerEligible = war.getAttackers() != null
				? BattleSideMembers.countEligibleMembers(war.getAttackers())
				: 0;
		int defenderEligible = war.getDefenders() != null
				? BattleSideMembers.countEligibleMembers(war.getDefenders())
				: 0;

		boolean passMinPlayers = distinctVoters >= effectiveMinPlayers();
		boolean passSmallestSideFull = Cache.warBattleVotingRequireSmallestSideFull
				&& isSmallestSideFullyRepresented(war, votes, memberNameToUuid);

		boolean passed;
		if (Cache.warBattleVotingPassIfEither) {
			passed = passMinPlayers || passSmallestSideFull;
		} else {
			passed = passMinPlayers && passSmallestSideFull;
		}

		return new VoteResults.QuorumResult(
				passed,
				passMinPlayers,
				passSmallestSideFull,
				distinctVoters,
				attackerEligible,
				defenderEligible);
	}
}
