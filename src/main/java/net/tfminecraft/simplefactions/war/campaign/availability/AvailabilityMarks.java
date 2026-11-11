package net.tfminecraft.simplefactions.war.campaign.availability;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.core.War;

/**
 * Changes one account's marks with the same checks in game and on the website. Each method returns
 * null when the change was made, otherwise the reason for the player. Callers save the war and send
 * {@code votes_changed}.
 */
public final class AvailabilityMarks {
	public static final String NOT_ELIGIBLE = "Only members of the warring factions and their hired mercenaries mark when they can fight.";
	public static final String NO_SCHEDULE = "This war has no battles to plan.";
	public static final String NOT_SHOWN = "That day is not open for marking.";
	public static final String LOCKED = "That day is decided and can no longer change.";
	public static final String BAD_HOUR = "That is not a battle start time.";

	private AvailabilityMarks() {
	}

	/** Replaces the account's marks on one date; no hours clears them. Marking anything withdraws a "none" answer. */
	public static String setDate(War war, UUID account, Faction faction, LocalDate date, Set<Integer> hours, Instant now) {
		String refusal = refusal(war, faction, date, now);
		if (refusal != null) {
			return refusal;
		}
		for (int hour : hours) {
			if (!BattleWindowService.isValidHour(hour)) {
				return BAD_HOUR;
			}
		}
		war.getAvailability().setMarks(account, date, hours);
		if (!hours.isEmpty()) {
			war.getAvailability().setNone(account, null);
		}
		return null;
	}

	/** Marks or unmarks one start hour on one date. */
	public static String toggleHour(War war, UUID account, Faction faction, LocalDate date, int hour, Instant now) {
		Set<Integer> hours = new TreeSet<>(war.getAvailability().marksOn(account, date));
		if (!hours.remove(hour)) {
			hours.add(hour);
		}
		return setDate(war, account, faction, date, hours, now);
	}

	/** Marks every start hour of the date, or clears it when every hour is already marked. */
	public static String toggleDate(War war, UUID account, Faction faction, LocalDate date, Instant now) {
		Set<Integer> all = new TreeSet<>(BattleWindowService.listValidHours());
		boolean full = war.getAvailability().marksOn(account, date).containsAll(all);
		return setDate(war, account, faction, date, full ? Set.of() : all, now);
	}

	/**
	 * "None of these days work for me": clears the account's marks on every date it can still change
	 * and stops "mark your availability" reminders until a date appears that the answer did not cover.
	 */
	public static String setNone(War war, UUID account, Faction faction, Instant now) {
		if (faction == null) {
			return NOT_ELIGIBLE;
		}
		if (!AvailabilityScheduler.isScheduling(war)) {
			return NO_SCHEDULE;
		}
		List<LocalDate> visible = AvailabilityCalendar.visibleDates(war, now);
		for (LocalDate date : visible) {
			if (AvailabilityCalendar.editable(war, date, now)) {
				war.getAvailability().setMarks(account, date, Set.of());
			}
		}
		war.getAvailability().setNone(account, visible.get(visible.size() - 1));
		return null;
	}

	/** Takes back a "none of these days" answer. */
	public static void clearNone(War war, UUID account) {
		war.getAvailability().setNone(account, null);
	}

	/** Whether the account's "none" answer covers this date. */
	public static boolean saidNone(War war, UUID account, LocalDate date) {
		LocalDate until = war.getAvailability().noneUntil(account);
		return until != null && !until.isBefore(date);
	}

	private static String refusal(War war, Faction faction, LocalDate date, Instant now) {
		if (faction == null) {
			return NOT_ELIGIBLE;
		}
		if (!AvailabilityScheduler.isScheduling(war)) {
			return NO_SCHEDULE;
		}
		if (!AvailabilityCalendar.visibleDates(war, now).contains(date)) {
			return NOT_SHOWN;
		}
		return AvailabilityCalendar.editable(war, date, now) ? null : LOCKED;
	}
}
