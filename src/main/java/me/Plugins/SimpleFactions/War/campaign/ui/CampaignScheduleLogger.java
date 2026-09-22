package me.Plugins.SimpleFactions.War.campaign.ui;

import java.util.List;

import me.Plugins.SimpleFactions.Managers.LogManager;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.campaign.BattleNamingService;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleBuildContext;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;

/** War campaign schedule formatting helpers for {@link LogManager}. */
public final class CampaignScheduleLogger {
	private CampaignScheduleLogger() {
	}

	public static String formatSlot(ScheduledCampaignBattle slot, List<Integer> axis) {
		if (slot == null) {
			return "null";
		}
		int sortProvinceId = slot.sortProvinceId();
		int sortAxisIndex = axisIndex(axis, sortProvinceId);
		int homeAxisIndex = axisIndex(axis, slot.provinceId());
		return String.format(
				"province=%d homeAxis=%s kind=%s required=%s fort=%s port=%s chrono=%s sortProvince=%d sortAxis=%s",
				slot.provinceId(),
				axisLabel(homeAxisIndex),
				slot.kind(),
				slot.required(),
				nullOr(slot.fortInstallationId()),
				nullOr(slot.portInstallationId()),
				slot.chronologyProvinceId(),
				sortProvinceId,
				axisLabel(sortAxisIndex));
	}

	public static void logSchedule(
			String label,
			War war,
			ScheduleLeg leg,
			List<Integer> axis,
			List<ScheduledCampaignBattle> schedule) {
		if (!LogManager.isEnabled()) {
			return;
		}
		LogManager.section(label);
		if (schedule == null || schedule.isEmpty()) {
			LogManager.line("(empty)");
			return;
		}
		for (int index = 0; index < schedule.size(); index++) {
			ScheduledCampaignBattle slot = schedule.get(index);
			String displayName = BattleNamingService.resolveScheduledDisplayName(
					war,
					leg,
					index,
					slot,
					slot.provinceId());
			LogManager.war("[%d] warId=%s %s | %s", index, war.getId(), displayName, formatSlot(slot, axis));
			LogManager.line("[%d] %s | %s", index, displayName, formatSlot(slot, axis));
		}
	}

	public static void logSchedule(String label, War war, List<Integer> axis, List<ScheduledCampaignBattle> schedule) {
		logSchedule(label, war, ScheduleLeg.INVASION, axis, schedule);
	}

	public static String fightOrderSummary(
			CampaignScheduleBuildContext ctx,
			ScheduleLeg leg,
			ScheduledCampaignBattle slot) {
		if (ctx == null || slot == null) {
			return "n/a";
		}
		int sortProvinceId = slot.sortProvinceId();
		int axisIndex = ctx.axis().indexOf(sortProvinceId);
		if (axisIndex < 0) {
			axisIndex = Integer.MAX_VALUE;
		}
		int key = leg == ScheduleLeg.INVASION ? axisIndex : -axisIndex;
		return String.format("sortProvince=%d axisIndex=%s fightKey=%d", sortProvinceId, axisLabel(axisIndex), key);
	}

	private static int axisIndex(List<Integer> axis, int provinceId) {
		if (axis == null) {
			return -1;
		}
		return axis.indexOf(provinceId);
	}

	private static String axisLabel(int index) {
		return index >= 0 ? String.valueOf(index) : "off-axis";
	}

	private static String nullOr(String value) {
		return value == null ? "-" : value;
	}
}
