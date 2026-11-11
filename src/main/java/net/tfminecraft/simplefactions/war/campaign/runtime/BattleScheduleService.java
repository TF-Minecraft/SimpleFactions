package net.tfminecraft.simplefactions.war.campaign.runtime;


import java.time.Instant;
import java.time.LocalDate;
import java.util.OptionalInt;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleLaunchService;
import net.tfminecraft.simplefactions.war.campaign.availability.BattleAvailability;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCapabilityService;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarDevMode;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.history.WarHistoryEvents;

/**
 * The war's battle day and the battle set on it. Which dates become battle days, and when, is
 * {@link net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityScheduler}'s; this
 * keeps the battle-day clock, the windows of the day, and moves the war on after each battle.
 */
public final class BattleScheduleService {
	private BattleScheduleService() {}

	public static int battleDayHour(Instant now) {
		return BattleWindowService.scheduleHour(now);
	}

	public static LocalDate battleDayDate(Instant now) {
		return BattleWindowService.scheduleDate(now);
	}

	public static boolean isOnBattleDay(War war, Instant now) {
		if (war == null || war.getBattleDay() == null || now == null) {
			return false;
		}
		return war.getBattleDay().equals(battleDayDate(now));
	}

	/**
	 * The post-battle choice is due at {@code defender_choice_deadline_hour} on the day after the
	 * battle, however far away the next battle day is.
	 */
	public static boolean isPostBattleChoiceDeadlineDue(War war, Instant now) {
		if (war == null || now == null) {
			return false;
		}
		// After a battle the earliest battle day is the day after it, and the next battle day is never before that.
		LocalDate earliest = war.getAvailability().getEarliestBattleDay();
		LocalDate battleDay = war.getBattleDay();
		LocalDate dayAfter = earliest == null || (battleDay != null && battleDay.isBefore(earliest)) ? battleDay : earliest;
		if (dayAfter == null) {
			return false;
		}
		LocalDate today = battleDayDate(now);
		return today.isAfter(dayAfter)
				|| (today.equals(dayAfter) && battleDayHour(now) >= Cache.warDefenderChoiceDeadlineHour);
	}

	/** Installation picks lock and enemy picks show at {@code vote_close_hour} on the battle day. */
	public static boolean isVoteCloseDue(War war, Instant now) {
		return isOnBattleDay(war, now) && battleDayHour(now) >= Cache.warVoteCloseHour;
	}

	public static boolean isBeforeVoteClose(War war, Instant now) {
		return isOnBattleDay(war, now) && battleDayHour(now) < Cache.warVoteCloseHour;
	}

	public static boolean isRaidWindowOpen(War war, Instant now) {
		if (WarDevMode.isEnabled() && war != null && war.isActive()) {
			return true;
		}
		if (!isOnBattleDay(war, now)) {
			return false;
		}
		int hour = battleDayHour(now);
		return hour >= Cache.warRaidWindowStartHour && hour <= Cache.warRaidWindowEndHour;
	}

	public static boolean isBattleWindowOpen(War war, Instant now) {
		if (!isOnBattleDay(war, now)) {
			return false;
		}
		int hour = battleDayHour(now);
		return hour >= Cache.warBattleWindowStartHour && hour <= Cache.warBattleWindowEndHour;
	}

	public static boolean needsPostBattleChoice(War war) {
		return CampaignPostBattleChoiceService.needsAnyChoice(war);
	}

	public static boolean isPostBattleChoiceResolved(War war) {
		if (!needsPostBattleChoice(war)) {
			return true;
		}
		return war.isPostBattleChoiceResolved();
	}

	public static boolean applyPostBattleChoiceDeadline(War war, Instant now) {
		return CampaignPostBattleChoiceService.applyDeadlineIfDue(war, now);
	}

	public static void enterAutoresolvePending(War war) {
		if (war == null) {
			return;
		}
		clearScheduledTargets(war);
		war.setBattleSchedulePhase(BattleSchedulePhase.AUTORESOLVE_PENDING);
		war.setAutoresolveProposedByAttacker(false);
		war.setAutoresolveProposedByDefender(false);
	}

	public static void openVote(War war) {
		if (war == null) {
			return;
		}
		war.clearSignupRemindersSent();
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		clearScheduledTargets(war);
		war.setDefenderChoiceResolved(false);
		WarHistoryEvents.nextBattleDay(war);
		WarHistoryEvents.votesChanged(war);
	}

	/**
	 * True when no battle can ever follow on this war: none has started, no post-battle choice is
	 * open, and the side with the initiative has no fuel or no battle left on its route. A started
	 * battle still ends on its own, so it never counts.
	 */
	public static boolean hasNoNextBattle(War war) {
		var battle = net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.getByWarId(war.getId());
		if (battle != null && battle.hasStarted()) {
			return false;
		}
		if (needsPostBattleChoice(war)) {
			return false;
		}
		return resolveScheduledProvinceId(war) == null;
	}

	public static boolean applyScheduledInstant(War war, Instant scheduledAt) {
		if (war == null || scheduledAt == null) {
			return false;
		}

		Integer provinceId = resolveScheduledProvinceId(war);
		if (provinceId == null) {
			return false;
		}

		return scheduleBattleAtProvince(war, provinceId, scheduledAt);
	}

	/**
	 * After a battle (fought, autoresolved or settled without a fight): its battle day leaves the
	 * queue, the next battle day (if one is locked) becomes the war's, and no date before the day
	 * after the battle can become one. A battle that runs past midnight still belongs to the evening
	 * it started on. Picks and raids of the fought day are cleared.
	 */
	public static void advanceAfterBattle(War war, Instant now) {
		if (war == null) {
			return;
		}
		LocalDate fought = war.getBattleDay();
		BattleAvailability availability = war.getAvailability();
		if (fought != null) {
			availability.removeBattleDay(fought);
		}
		LocalDate dayAfter = (fought != null ? fought : battleDayDate(now != null ? now : Instant.now())).plusDays(1);
		LocalDate earliest = availability.getEarliestBattleDay();
		availability.setEarliestBattleDay(earliest == null || earliest.isBefore(dayAfter) ? dayAfter : earliest);
		LockedBattleDay next = availability.head();
		war.setBattleDay(next == null ? null : next.date());
		clearForNewBattleDay(war);
	}

	/** What belongs to one battle day: the staff hold, signup reminders, installation picks and raids. */
	public static void clearForNewBattleDay(War war) {
		releaseStaffHold(war);
		war.clearSignupRemindersSent();
		BattleInstallationPickService.clearForNewBattleDay(war);
		CampaignRaidService.clearForNewBattleDay(war);
	}

	/** A battle set on its province goes back to waiting: the war votes again and its start time is cleared. */
	public static void releaseScheduledBattle(War war) {
		releaseStaffHold(war);
		war.clearSignupRemindersSent();
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		clearScheduledTargets(war);
	}

	/** A hold belongs to one battle day; a new day schedules the battle afresh. */
	static void releaseStaffHold(War war) {
		var battle = net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.getByWarId(war.getId());
		if (battle != null && !battle.hasStarted() && battle.isStaffHold()) {
			battle.setStaffHold(false);
			net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService.persistBattle(battle);
		}
	}

	public static Integer resolveBattleProvinceId(War war) {
		if (war == null) {
			return null;
		}
		if (war.getScheduledBattleProvinceId() != null) {
			return war.getScheduledBattleProvinceId();
		}
		return resolveScheduledProvinceId(war);
	}

	public static boolean scheduleBattleAtProvince(War war, int provinceId, Instant scheduledAt) {
		if (war == null || scheduledAt == null) {
			return false;
		}
		LocalDate battleDay = war.getBattleDay();
		Integer scheduleHour = BattleWindowService.resolveScheduleHour(battleDay, scheduledAt);
		if (scheduleHour == null || !BattleWindowService.isValidHour(scheduleHour)) {
			return false;
		}
		war.clearSignupRemindersSent();
		war.setScheduledBattleHour(scheduleHour);
		war.setScheduledBattleAt(scheduledAt);
		war.setScheduledBattleProvinceId(provinceId);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setPostponementsThisCycle(0);
		CampaignBattleLaunchService.prepareScheduledBattle(war);
		// Preparing can settle the battle at once (forfeit, naval default), which clears the time.
		boolean scheduled = war.getScheduledBattleAt() != null;
		if (scheduled) {
			WarHistoryEvents.scheduled(war, provinceId);
		}
		return scheduled;
	}

	public static boolean markScheduledAtProvince(War war, int provinceId) {
		if (war == null) {
			return false;
		}
		war.clearSignupRemindersSent();
		war.setScheduledBattleProvinceId(provinceId);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setPostponementsThisCycle(0);
		boolean prepared = CampaignBattleLaunchService.prepareScheduledBattle(war) != null;
		if (prepared) {
			WarHistoryEvents.scheduled(war, provinceId);
		}
		return prepared;
	}

	public static Integer resolveScheduledProvinceId(War war) {
		if (needsPostBattleChoice(war) && !isPostBattleChoiceResolved(war)) {
			return null;
		}
		OptionalInt next = CampaignCapabilityService.nextBattleProvince(war);
		return next.isPresent() ? next.getAsInt() : null;
	}

	private static void clearScheduledTargets(War war) {
		war.setScheduledBattleAt(null);
		war.setScheduledBattleHour(0);
		war.setScheduledBattleProvinceId(null);
	}
}
