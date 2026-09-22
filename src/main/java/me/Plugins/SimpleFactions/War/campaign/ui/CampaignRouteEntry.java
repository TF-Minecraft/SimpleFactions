package me.Plugins.SimpleFactions.War.campaign.ui;

import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService.ScheduleLeg;

public record CampaignRouteEntry(int provinceId, int axisIndex, int scheduleIndex, ScheduleLeg scheduleLeg) {
	public CampaignRouteEntry(int provinceId, int axisIndex, int scheduleIndex) {
		this(provinceId, axisIndex, scheduleIndex, ScheduleLeg.INVASION);
	}

	public boolean hasBattleSlot() {
		return scheduleIndex >= 0;
	}
}
