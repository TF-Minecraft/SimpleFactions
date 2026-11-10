package net.tfminecraft.simplefactions.war.campaign.vote;

public final class VoteResults {
	private VoteResults() {
	}

	public record BattleHourTally(int attackerCount, int defenderCount) {
	}

	public record QuorumResult(
			boolean passed,
			boolean passMinPlayers,
			boolean passSmallestSideFull,
			int distinctVoters,
			int attackerEligible,
			int defenderEligible) {
	}

	public enum BattleVoteToggleResult {
		ADDED,
		REMOVED,
		REJECTED_OFFLINE,
		REJECTED_NOT_PARTICIPANT,
		REJECTED_INVALID_HOUR,
		REJECTED_VOTE_CLOSED
	}

	public enum BattleScheduleCloseResult {
		SCHEDULED,
		POSTPONED,
		AUTORESOLVE_PENDING,
		AUTORESOLVED,
		SKIPPED,
		BLOCKED_DEFENDER_CHOICE,
		/** Out of postponements with no battle left to fight: the war ended in white peace. */
		ENDED_NO_NEXT_BATTLE,
		/** Settling the post-battle choice at its deadline ended the war, so there is no vote to close. */
		WAR_ENDED
	}

	public record CloseVoteOptions(boolean forceImmediate, boolean forceQuorum) {
		public static CloseVoteOptions scheduled() {
			return new CloseVoteOptions(false, false);
		}

		public static CloseVoteOptions admin(boolean forceQuorum) {
			return new CloseVoteOptions(true, forceQuorum);
		}
	}
}
