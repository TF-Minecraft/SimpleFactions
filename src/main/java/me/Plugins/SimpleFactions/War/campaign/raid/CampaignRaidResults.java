package me.Plugins.SimpleFactions.War.campaign.raid;

import me.Plugins.SimpleFactions.War.campaign.raid.RaidTargetService.RaidKind;

public final class CampaignRaidResults {
	private CampaignRaidResults() {
	}

	public enum LaunchResult {
		STARTED,
		REJECTED_WAR_INACTIVE,
		REJECTED_NOT_PARTICIPANT,
		REJECTED_OUTSIDE_WINDOW,
		REJECTED_QUOTA_SPENT,
		REJECTED_RAID_IN_PROGRESS,
		REJECTED_INVALID_INPUT
	}

	public enum ValidateLaunchResult {
		OK,
		REJECTED_WAR_INACTIVE,
		REJECTED_NOT_PARTICIPANT,
		REJECTED_OUTSIDE_WINDOW,
		REJECTED_QUOTA_SPENT,
		REJECTED_RAID_IN_PROGRESS,
		REJECTED_INVALID_SOURCE,
		REJECTED_INVALID_TARGET,
		REJECTED_KIND_MISMATCH
	}

	public record ValidateLaunchOutcome(ValidateLaunchResult result, RaidKind raidKind) {
		public static ValidateLaunchOutcome of(ValidateLaunchResult result) {
			return new ValidateLaunchOutcome(result, null);
		}

		public static ValidateLaunchOutcome ok(RaidKind raidKind) {
			return new ValidateLaunchOutcome(ValidateLaunchResult.OK, raidKind);
		}
	}

	public enum TransitionResult {
		OK,
		REJECTED_NO_ACTIVE_RAID,
		REJECTED_WRONG_STATE
	}

	public enum JoinResult {
		OK,
		REJECTED_RAID_NOT_FOUND,
		REJECTED_NOT_PARTICIPANT,
		REJECTED_NOT_ATTACKER_COALITION,
		REJECTED_NOT_MUSTER,
		REJECTED_IN_WARBAND,
		REJECTED_ALREADY_JOINED,
		REJECTED_MOUNTED_ON_VEHICLE
	}
}
