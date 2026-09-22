package net.tfminecraft.simplefactions.war.campaign.vote;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;

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
