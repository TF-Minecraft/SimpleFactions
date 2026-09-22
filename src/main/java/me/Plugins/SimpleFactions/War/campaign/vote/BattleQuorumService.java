package me.Plugins.SimpleFactions.War.campaign.vote;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;

public final class BattleQuorumService {
	private BattleQuorumService() {}

	public static int countDistinctVoters(War war) {
		if (war == null) {
			return 0;
		}
		int count = 0;
		for (Set<Integer> hours : war.getBattleVotes().values()) {
			if (hours != null && !hours.isEmpty()) {
				count++;
			}
		}
		return count;
	}

	public static boolean isSmallestSideFullyRepresented(War war, Function<String, UUID> memberNameToUuid) {
		if (war == null || memberNameToUuid == null) {
			return false;
		}
		Side attackers = war.getAttackers();
		Side defenders = war.getDefenders();
		if (attackers == null || defenders == null) {
			return false;
		}

		int attackerEligible = BattleSideMembers.countEligibleMembers(attackers);
		int defenderEligible = BattleSideMembers.countEligibleMembers(defenders);
		Side smallerSide = attackerEligible <= defenderEligible ? attackers : defenders;
		Set<String> members = BattleSideMembers.collectEligibleMemberNames(smallerSide);
		if (members.isEmpty()) {
			return false;
		}

		Map<UUID, Set<Integer>> votes = war.getBattleVotes();
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

		int distinctVoters = countDistinctVoters(war);
		int attackerEligible = war.getAttackers() != null
				? BattleSideMembers.countEligibleMembers(war.getAttackers())
				: 0;
		int defenderEligible = war.getDefenders() != null
				? BattleSideMembers.countEligibleMembers(war.getDefenders())
				: 0;

		boolean passMinPlayers = distinctVoters >= effectiveMinPlayers();
		boolean passSmallestSideFull = Cache.warBattleVotingRequireSmallestSideFull
				&& isSmallestSideFullyRepresented(war, memberNameToUuid);

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
