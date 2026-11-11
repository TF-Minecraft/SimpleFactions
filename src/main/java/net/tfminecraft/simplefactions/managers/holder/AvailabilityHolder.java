package net.tfminecraft.simplefactions.managers.holder;

import java.time.LocalDate;
import java.util.List;

import net.tfminecraft.simplefactions.enums.SFGUI;

/** The availability grid of one war, with the dates its columns showed when it was drawn. */
public class AvailabilityHolder extends CampaignInventoryHolder {
	private List<LocalDate> dates;

	public AvailabilityHolder(int warId, List<LocalDate> dates) {
		super(warId, SFGUI.CAMPAIGN_AVAILABILITY_VIEW);
		this.dates = List.copyOf(dates);
	}

	public List<LocalDate> getDates() {
		return dates;
	}

	public void setDates(List<LocalDate> dates) {
		this.dates = List.copyOf(dates);
	}
}
