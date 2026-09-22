package me.Plugins.SimpleFactions.War.campaign.schedule;

import me.Plugins.SimpleFactions.Managers.LogManager;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;

public final class CampaignScheduleTrimmer {
	private static final int DEFAULT_MAX_BATTLES = 4;
	private static final Logger LOGGER = Logger.getLogger(CampaignScheduleTrimmer.class.getName());

	private CampaignScheduleTrimmer() {
	}

	public static int maxBattlesPerLegForGoal(WarGoalType goal) {
		if (goal == null) {
			return DEFAULT_MAX_BATTLES;
		}
		Integer configured = Cache.warGoalMaxBattles.get(goal);
		return configured != null && configured > 0 ? configured : DEFAULT_MAX_BATTLES;
	}

	@Deprecated
	public static int maxBattlesForGoal(WarGoalType goal) {
		return maxBattlesPerLegForGoal(goal);
	}

	public static List<ScheduledCampaignBattle> trim(List<ScheduledCampaignBattle> schedule, int maxBattles) {
		return trimCounter(schedule, maxBattles);
	}

	/** Invasion leg: drop from objective side first; never drop schedule index 0 (border fight). */
	public static List<ScheduledCampaignBattle> trimInvasion(List<ScheduledCampaignBattle> schedule, int maxBattles) {
		return trim(schedule, maxBattles, TrimMode.INVASION);
	}

	/** Counter leg: drop wilderness fields nearest the border first. */
	public static List<ScheduledCampaignBattle> trimCounter(List<ScheduledCampaignBattle> schedule, int maxBattles) {
		return trim(schedule, maxBattles, TrimMode.COUNTER);
	}

	private enum TrimMode {
		INVASION,
		COUNTER
	}

	private static List<ScheduledCampaignBattle> trim(
			List<ScheduledCampaignBattle> schedule,
			int maxBattles,
			TrimMode mode) {
		if (schedule == null || schedule.isEmpty()) {
			return List.of();
		}
		if (maxBattles <= 0) {
			return List.of();
		}
		if (schedule.size() <= maxBattles) {
			return List.copyOf(schedule);
		}

		boolean fromStart = mode == TrimMode.COUNTER;
		int protectedPrefixEnd = invasionProtectedPrefixEnd(schedule, mode);
		List<ScheduledCampaignBattle> working = new ArrayList<>(schedule);
		while (working.size() > maxBattles) {
			int dropIndex = findDropIndex(working, fromStart, protectedPrefixEnd);
			if (dropIndex < 0) {
				LOGGER.warning("Campaign schedule trim could not drop further slots; size="
						+ working.size() + " max=" + maxBattles);
				LogManager.line(
						"TRIM stuck size=%d max=%d mode=%s",
						working.size(),
						maxBattles,
						mode);
				break;
			}
			ScheduledCampaignBattle dropped = working.get(dropIndex);
			LogManager.line(
					"TRIM drop index=%d mode=%s province=%d kind=%s required=%s fort=%s",
					dropIndex,
					mode,
					dropped.provinceId(),
					dropped.kind(),
					dropped.required(),
					dropped.fortInstallationId());
			working.remove(dropIndex);
		}
		return List.copyOf(working);
	}

	private static int invasionProtectedPrefixEnd(List<ScheduledCampaignBattle> schedule, TrimMode mode) {
		if (mode != TrimMode.INVASION || schedule == null || schedule.isEmpty()) {
			return -1;
		}
		if (schedule.get(0).kind() == CampaignBattleKind.NAVAL) {
			return 1;
		}
		return 0;
	}

	private static int findDropIndex(
			List<ScheduledCampaignBattle> schedule,
			boolean fromStart,
			int protectedPrefixEnd) {
		int fieldIndex = findDroppableFieldIndex(schedule, fromStart, protectedPrefixEnd);
		if (fieldIndex >= 0) {
			return fieldIndex;
		}
		int navalInvasionIndex = findKindIndex(schedule, CampaignBattleKind.NAVAL_INVASION, fromStart, protectedPrefixEnd);
		if (navalInvasionIndex >= 0) {
			return navalInvasionIndex;
		}
		int navalIndex = findKindIndex(schedule, CampaignBattleKind.NAVAL, fromStart, protectedPrefixEnd);
		if (navalIndex >= 0) {
			return navalIndex;
		}
		return findKindIndex(schedule, CampaignBattleKind.SIEGE, fromStart, protectedPrefixEnd);
	}

	private static int findDroppableFieldIndex(
			List<ScheduledCampaignBattle> schedule,
			boolean fromStart,
			int protectedPrefixEnd) {
		if (fromStart) {
			for (int index = 0; index < schedule.size(); index++) {
				if (isDroppableField(schedule.get(index))) {
					return index;
				}
			}
			return -1;
		}
		for (int index = schedule.size() - 1; index >= 0; index--) {
			if (protectedPrefixEnd >= 0 && index <= protectedPrefixEnd) {
				continue;
			}
			if (isDroppableField(schedule.get(index))) {
				return index;
			}
		}
		return -1;
	}

	private static boolean isDroppableField(ScheduledCampaignBattle slot) {
		return slot.kind() == CampaignBattleKind.FIELD && !slot.required();
	}

	private static int findKindIndex(
			List<ScheduledCampaignBattle> schedule,
			CampaignBattleKind kind,
			boolean fromStart,
			int protectedPrefixEnd) {
		if (fromStart) {
			for (int index = 0; index < schedule.size(); index++) {
				if (isDroppableKind(schedule.get(index), kind)) {
					return index;
				}
			}
			return -1;
		}
		for (int index = schedule.size() - 1; index >= 0; index--) {
			if (protectedPrefixEnd >= 0 && index <= protectedPrefixEnd) {
				continue;
			}
			if (isDroppableKind(schedule.get(index), kind)) {
				return index;
			}
		}
		return -1;
	}

	private static boolean isDroppableKind(ScheduledCampaignBattle slot, CampaignBattleKind kind) {
		return !slot.required() && slot.kind() == kind;
	}
}
