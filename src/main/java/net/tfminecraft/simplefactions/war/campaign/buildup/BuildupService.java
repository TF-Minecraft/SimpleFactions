package net.tfminecraft.simplefactions.war.campaign.buildup;

import java.time.Instant;
import java.time.LocalDate;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityScheduler;
import net.tfminecraft.simplefactions.war.campaign.availability.BattleAvailability;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;

/**
 * A declared war waits before its first battle. It is frozen (see
 * {@link net.tfminecraft.simplefactions.war.freeze.PreparationFreeze}) until that battle starts, and
 * no date before {@code first_option_day} days after the declaration's battle day can be a battle
 * day: the availability grid starts there, so players mark a week ahead. The war stays in
 * {@link BattleSchedulePhase#BUILDUP} until its first battle day is locked.
 */
public final class BuildupService {

	private BuildupService() {
	}

	/**
	 * Puts a newly declared war into its buildup. Does nothing when buildups are off or the war has no
	 * campaign schedule (legacy raid wars), and returns whether it started.
	 */
	public static boolean start(War war, Instant now) {
		if (!Cache.warBuildupEnabled || war == null || now == null
				|| war.getBattleSchedulePhase() != BattleSchedulePhase.VOTING) {
			return false;
		}
		war.setBuildup(true);
		BattleAvailability availability = new BattleAvailability();
		availability.setEarliestBattleDay(BattleWindowService.scheduleDate(now).plusDays(Cache.warBuildupFirstOptionDay));
		war.setAvailability(availability);
		war.setBattleDay(null);
		war.setBattleSchedulePhase(BattleSchedulePhase.BUILDUP);
		return true;
	}

	/**
	 * Staff: ends the buildup now. The freeze lifts, and a war with no battle day yet can get one
	 * from today on.
	 */
	public static void end(War war, Instant now) {
		war.setBuildup(false);
		BattleAvailability availability = war.getAvailability();
		LocalDate today = BattleWindowService.scheduleDate(now);
		if (availability.getEarliestBattleDay() == null || availability.getEarliestBattleDay().isAfter(today)) {
			availability.setEarliestBattleDay(today);
		}
		AvailabilityScheduler.sync(war);
	}
}
