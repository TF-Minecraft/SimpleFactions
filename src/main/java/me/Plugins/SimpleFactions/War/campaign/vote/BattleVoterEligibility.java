package me.Plugins.SimpleFactions.War.campaign.vote;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;

public final class BattleVoterEligibility {
	private static final int[] HOUR_TOGGLE_SLOTS = {28, 29, 30, 31, 32};

	private BattleVoterEligibility() {}

	public static boolean isEligibleVoter(War war, Faction faction) {
		return war != null
				&& war.isActive()
				&& war.getBattleSchedulePhase() == BattleSchedulePhase.VOTING
				&& faction != null
				&& war.getSide(faction) != null;
	}

	public static boolean canToggleVote(War war, Faction faction, Instant now) {
		return isEligibleVoter(war, faction)
				&& !BattleScheduleService.isVoteCloseDue(war, now);
	}

	public static boolean canProposeAutoresolve(War war, BelligerentRole side) {
		if (war == null || !war.isActive() || side == null) {
			return false;
		}
		if (war.getBattleSchedulePhase() != BattleSchedulePhase.VOTING) {
			return false;
		}
		return side == BelligerentRole.ATTACKER || side == BelligerentRole.DEFENDER;
	}

	public static int[] hourToggleSlots() {
		return HOUR_TOGGLE_SLOTS.clone();
	}

	public static List<HourSlotEntry> hourSlotLayout(List<Integer> hours) {
		List<Integer> validHours = hours != null ? hours : BattleWindowService.listValidHours();
		List<HourSlotEntry> layout = new ArrayList<>();
		for (int i = 0; i < HOUR_TOGGLE_SLOTS.length; i++) {
			Integer hour = i < validHours.size() ? validHours.get(i) : null;
			layout.add(new HourSlotEntry(HOUR_TOGGLE_SLOTS[i], hour));
		}
		return layout;
	}

	public record HourSlotEntry(int slot, Integer hour) {
	}
}
