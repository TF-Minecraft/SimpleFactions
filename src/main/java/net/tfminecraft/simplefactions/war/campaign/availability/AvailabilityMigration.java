package net.tfminecraft.simplefactions.war.campaign.availability;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;

/**
 * Turns a war saved with the old day and hour votes into availability, so nobody loses a vote:
 * a buildup day pick becomes marks at every hour of that date, an hour vote marks that hour on
 * the battle day, and a war voting on or scheduled for a battle day keeps it as a locked battle
 * day (with its start hour when scheduled). The grid starts at the old vote's first day.
 */
public final class AvailabilityMigration {
	private AvailabilityMigration() {
	}

	/**
	 * {@code dayVotes} are offsets from {@code dayVoteFirstDay}; {@code hourVotes} are battle-day
	 * hours on the war's battle day. Picks outside the battle window are dropped.
	 */
	public static void migrate(War war, Map<UUID, Set<Integer>> dayVotes, LocalDate dayVoteFirstDay,
			Map<UUID, Set<Integer>> hourVotes) {
		BattleAvailability availability = new BattleAvailability();
		war.setAvailability(availability);
		if (dayVoteFirstDay != null) {
			availability.setEarliestBattleDay(dayVoteFirstDay);
			Set<Integer> everyHour = new TreeSet<>(BattleWindowService.listValidHours());
			dayVotes.forEach((account, offsets) -> {
				for (int offset : offsets) {
					LocalDate date = dayVoteFirstDay.plusDays(offset);
					Set<Integer> hours = new TreeSet<>(availability.marksOn(account, date));
					hours.addAll(everyHour);
					availability.setMarks(account, date, hours);
				}
			});
		}
		BattleSchedulePhase phase = war.getBattleSchedulePhase();
		LocalDate day = war.getBattleDay();
		if (day == null || (phase != BattleSchedulePhase.VOTING && phase != BattleSchedulePhase.SCHEDULED
				&& phase != BattleSchedulePhase.AUTORESOLVE_PENDING)) {
			war.setBattleDay(null);
			return;
		}
		int scheduledHour = war.getScheduledBattleHour();
		boolean scheduled = phase == BattleSchedulePhase.SCHEDULED && BattleWindowService.isValidHour(scheduledHour);
		availability.putBattleDay(scheduled
				? LockedBattleDay.open(day, false).withHour(scheduledHour, false)
				: LockedBattleDay.open(day, false));
		hourVotes.forEach((account, hours) -> {
			Set<Integer> valid = new TreeSet<>(availability.marksOn(account, day));
			for (int hour : hours) {
				if (BattleWindowService.isValidHour(hour)) {
					valid.add(hour);
				}
			}
			availability.setMarks(account, day, valid);
		});
		if (availability.getEarliestBattleDay() == null) {
			availability.setEarliestBattleDay(day);
		}
	}
}
