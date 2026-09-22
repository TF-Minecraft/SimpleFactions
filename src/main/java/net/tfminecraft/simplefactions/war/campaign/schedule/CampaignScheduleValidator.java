package net.tfminecraft.simplefactions.war.campaign.schedule;

import java.util.List;

import net.tfminecraft.simplefactions.war.core.War;

public final class CampaignScheduleValidator {
	private CampaignScheduleValidator() {
	}

	public static boolean isValidInvasionSchedule(
			War war,
			List<Integer> axis,
			List<ScheduledCampaignBattle> invasion) {
		if (war == null || axis == null || axis.isEmpty() || invasion == null || invasion.isEmpty()) {
			return false;
		}
		Integer objectiveId = war.getObjectiveProvinceId();
		if (objectiveId == null || objectiveId <= 0) {
			return false;
		}
		int objectiveAxisIndex = axis.indexOf(objectiveId);
		if (objectiveAxisIndex < 0) {
			return false;
		}

		ScheduledCampaignBattle last = invasion.get(invasion.size() - 1);
		if (last.kind() != net.tfminecraft.simplefactions.war.enums.CampaignBattleKind.FIELD
				|| !last.required()
				|| last.provinceId() != objectiveId) {
			return false;
		}

		for (ScheduledCampaignBattle slot : invasion) {
			int sortIndex = axis.indexOf(slot.sortProvinceId());
			if (sortIndex < 0 || sortIndex > objectiveAxisIndex) {
				return false;
			}
		}
		return true;
	}
}
