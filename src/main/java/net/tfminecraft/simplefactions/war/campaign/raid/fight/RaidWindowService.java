package net.tfminecraft.simplefactions.war.campaign.raid.fight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.tfminecraft.simplefactions.Cache;

public final class RaidWindowService {
	private RaidWindowService() {}

	public static List<Integer> listRaidHours() {
		int start = Cache.warRaidWindowStartHour;
		int end = Cache.warRaidWindowEndHour;
		List<Integer> hours = new ArrayList<>();
		for (int hour = start; hour <= end; hour++) {
			hours.add(hour);
		}
		return Collections.unmodifiableList(hours);
	}

	public static boolean isRaidHour(int hour) {
		return hour >= Cache.warRaidWindowStartHour && hour <= Cache.warRaidWindowEndHour;
	}
}
