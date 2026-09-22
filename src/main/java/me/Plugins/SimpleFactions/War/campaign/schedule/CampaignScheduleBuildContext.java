package me.Plugins.SimpleFactions.War.campaign.schedule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex;

public final class CampaignScheduleBuildContext {
	private final List<Integer> axis;
	private final List<ScheduledCampaignBattle> invasion = new ArrayList<>();
	private final List<ScheduledCampaignBattle> counter = new ArrayList<>();
	private final Set<String> scheduledFortIds = new HashSet<>();
	private final Set<String> invasionPortIds = new HashSet<>();
	private final Set<String> counterPortIds = new HashSet<>();
	private final int borderProvinceId;
	private final int cursorIndex;
	private final int objectiveAxisIndex;
	private final FortZocIndex fortIndex;

	public CampaignScheduleBuildContext(
			List<Integer> axis,
			int borderProvinceId,
			int cursorIndex,
			int objectiveAxisIndex,
			FortZocIndex fortIndex) {
		this.axis = axis == null ? List.of() : List.copyOf(axis);
		this.borderProvinceId = borderProvinceId;
		this.cursorIndex = cursorIndex;
		this.objectiveAxisIndex = objectiveAxisIndex;
		this.fortIndex = fortIndex;
	}

	public List<Integer> axis() {
		return axis;
	}

	public List<ScheduledCampaignBattle> invasion() {
		return invasion;
	}

	public List<ScheduledCampaignBattle> counter() {
		return counter;
	}

	public Set<String> scheduledFortIds() {
		return scheduledFortIds;
	}

	public Set<String> portIdsFor(CampaignScheduleService.ScheduleLeg leg) {
		return leg == CampaignScheduleService.ScheduleLeg.INVASION ? invasionPortIds : counterPortIds;
	}

	public int borderProvinceId() {
		return borderProvinceId;
	}

	public int cursorIndex() {
		return cursorIndex;
	}

	public int objectiveAxisIndex() {
		return objectiveAxisIndex;
	}

	public FortZocIndex fortIndex() {
		return fortIndex;
	}

	List<ScheduledCampaignBattle> scheduleFor(CampaignScheduleService.ScheduleLeg leg) {
		return leg == CampaignScheduleService.ScheduleLeg.INVASION ? invasion : counter;
	}
}
