package net.tfminecraft.simplefactions.war.campaign.availability;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityTally.HourCount;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleAutoresolveService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiTimeFormatter;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleVoteReminders;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.history.WarHistoryEvents;
import net.tfminecraft.simplefactions.war.notice.WarNotices;
import net.tfminecraft.simplefactions.war.resolution.WarResolutionService;

/**
 * Turns marks into battles, every minute and in date order, catching up after downtime:
 * <ul>
 * <li>Day lock ({@code day_lock_hours_before} the date's window opens): the date becomes a battle
 * day when, at one start hour, enough of each side ({@link AvailabilityTally#need}) marked it.
 * After {@code max_days_without_battle} dates in a row without one, the last becomes a battle day
 * anyway (forced). At most {@value #MAX_QUEUED} battle days wait to be fought at once.</li>
 * <li>Time lock ({@code time_lock_hours_before}): a battle day gets its start hour. A forced day one
 * side never marked is autoresolved instead.</li>
 * <li>The earliest battle day with its hour fixed is set on its province once no battle is being
 * fought.</li>
 * </ul>
 * A date whose battle window closed before its lock was processed (the server was down) is skipped.
 */
public final class AvailabilityScheduler {
	/** Battle days that may wait to be fought at once, e.g. Thursday with its time fixed and Friday. */
	static final int MAX_QUEUED = 2;

	private AvailabilityScheduler() {
	}

	/** Wars with a campaign schedule; legacy raid wars have none. */
	public static boolean isScheduling(War war) {
		return war != null && war.isActive() && war.getBattleSchedulePhase() != BattleSchedulePhase.IDLE;
	}

	/** One pass over the war's locks. Returns whether the war changed and must be saved. */
	public static boolean process(War war, Instant now) {
		if (!isScheduling(war) || now == null) {
			return false;
		}
		BattleAvailability availability = war.getAvailability();
		LocalDate today = BattleWindowService.scheduleDate(now);
		boolean changed = false;
		if (availability.getEarliestBattleDay() == null) {
			availability.setEarliestBattleDay(today);
			changed = true;
		}
		changed |= processDayLocks(war, now);
		changed |= processTimeLocks(war, now);
		changed |= dropMissedBattleDays(war, now);
		changed |= availability.pruneBefore(today);
		changed |= sync(war);
		if (war.getBattleSchedulePhase() == BattleSchedulePhase.VOTING
				&& BattleScheduleService.applyPostBattleChoiceDeadline(war, now)) {
			changed = true;
		}
		if (war.isActive()) {
			changed |= scheduleNext(war);
		}
		if (changed && war.isActive()) {
			WarHistoryEvents.votesChanged(war);
		}
		return changed;
	}

	static boolean processDayLocks(War war, Instant now) {
		BattleAvailability availability = war.getAvailability();
		LocalDate date = availability.getEarliestBattleDay();
		LocalDate decided = availability.getLastDayLockProcessed();
		if (decided != null && !decided.isBefore(date)) {
			date = decided.plusDays(1);
		}
		// A date two days back has closed its window whatever the config, so a long downtime is not walked day by day.
		LocalDate stale = BattleWindowService.scheduleDate(now).minusDays(2);
		if (date.isBefore(stale)) {
			date = stale;
		}
		boolean changed = false;
		while (!AvailabilityCalendar.dayLockAt(date).isAfter(now)) {
			if (now.isBefore(AvailabilityCalendar.windowClosesAt(date))) {
				decide(war, date, now);
			}
			availability.setLastDayLockProcessed(date);
			date = date.plusDays(1);
			changed = true;
		}
		return changed;
	}

	private static void decide(War war, LocalDate date, Instant now) {
		BattleAvailability availability = war.getAvailability();
		if (availability.battleDay(date) != null || availability.battleDays().size() >= MAX_QUEUED) {
			// Staff locked it already, or two battle days are waiting: either way battles are coming.
			availability.setNoBattleStreak(0);
			return;
		}
		List<HourCount> counts = AvailabilityTally.count(war, date);
		if (AvailabilityTally.meetsNeed(counts, AvailabilityTally.need(war, BelligerentRole.ATTACKER),
				AvailabilityTally.need(war, BelligerentRole.DEFENDER))) {
			lock(war, date, false, now);
			return;
		}
		int streak = availability.getNoBattleStreak() + 1;
		if (Cache.warAvailabilityMaxDaysWithoutBattle > 0 && streak >= Cache.warAvailabilityMaxDaysWithoutBattle) {
			lock(war, date, true, now);
			return;
		}
		availability.setNoBattleStreak(streak);
	}

	/** Makes the date a battle day and tells everyone. */
	static void lock(War war, LocalDate date, boolean forced, Instant now) {
		BattleAvailability availability = war.getAvailability();
		availability.putBattleDay(LockedBattleDay.open(date, forced));
		availability.setNoBattleStreak(0);
		Instant timeLock = AvailabilityCalendar.timeLockAt(date);
		WarHistoryEvents.battleDayLocked(war, date, timeLock, forced);
		tellEveryone(war, "§6⚔ " + AvailabilityCalendar.label(date) + " §eis a battle day in " + war.getName()
				+ "§e. Its start time is decided " + CampaignUiTimeFormatter.formatDateTime(timeLock) + "§e.");
		BattleVoteReminders.battleDayLocked(war, date, now);
	}

	static boolean processTimeLocks(War war, Instant now) {
		BattleAvailability availability = war.getAvailability();
		boolean changed = false;
		for (LockedBattleDay day : new ArrayList<>(availability.battleDays())) {
			if (day.hour() != null || AvailabilityCalendar.timeLockAt(day.date()).isAfter(now)) {
				continue;
			}
			List<HourCount> counts = AvailabilityTally.count(war, day.date());
			if (counts.isEmpty()) {
				// A battle window with no start hour (window_start_hour = window_end_hour) has no time to fix.
				continue;
			}
			boolean resolve = day.forced() && AvailabilityTally.oneSideAbsent(counts);
			int hour = day.forced() ? AvailabilityTally.pickForcedHour(counts) : AvailabilityTally.pickHour(counts);
			availability.putBattleDay(day.withHour(hour, resolve));
			tellEveryone(war, resolve
					? "§6⚔ §eOne side of " + war.getName() + " §emarked no time on " + AvailabilityCalendar.label(day.date())
							+ "§e, so that battle is autoresolved."
					: "§6⚔ §eThe battle of " + war.getName() + " §eon " + AvailabilityCalendar.label(day.date())
							+ " §estarts at " + CampaignUiTimeFormatter.formatUtcHour(day.date(), hour) + "§e.");
			changed = true;
		}
		return changed;
	}

	/** Battle days whose window closed unfought, except the one a battle is set for, which the launcher owns. */
	static boolean dropMissedBattleDays(War war, Instant now) {
		BattleAvailability availability = war.getAvailability();
		boolean placed = war.getBattleSchedulePhase() == BattleSchedulePhase.SCHEDULED
				|| war.getBattleSchedulePhase() == BattleSchedulePhase.AUTORESOLVE_PENDING;
		boolean changed = false;
		for (LockedBattleDay day : new ArrayList<>(availability.battleDays())) {
			boolean owned = placed && day.date().equals(war.getBattleDay());
			if (!owned && !now.isBefore(AvailabilityCalendar.windowClosesAt(day.date()))) {
				availability.removeBattleDay(day.date());
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * Keeps the war's battle day on the earliest locked one, and its phase on BUILDUP (frozen, no
	 * battle day yet) or VOTING. A war with a battle set leaves both to that battle.
	 */
	public static boolean sync(War war) {
		BattleSchedulePhase phase = war.getBattleSchedulePhase();
		if (phase == BattleSchedulePhase.SCHEDULED || phase == BattleSchedulePhase.AUTORESOLVE_PENDING) {
			return false;
		}
		LockedBattleDay head = war.getAvailability().head();
		LocalDate day = head == null ? null : head.date();
		boolean changed = false;
		if (!Objects.equals(day, war.getBattleDay())) {
			boolean hadDay = war.getBattleDay() != null;
			war.setBattleDay(day);
			if (hadDay) {
				BattleScheduleService.clearForNewBattleDay(war);
			}
			changed = true;
		}
		BattleSchedulePhase wanted = day == null && war.isBuildupFrozen()
				? BattleSchedulePhase.BUILDUP
				: BattleSchedulePhase.VOTING;
		if (phase != wanted) {
			war.setBattleSchedulePhase(wanted);
			changed = true;
		}
		return changed;
	}

	/** Sets the earliest battle day on its province once its hour is fixed and nothing is being fought. */
	static boolean scheduleNext(War war) {
		LockedBattleDay head = war.getAvailability().head();
		if (war.getBattleSchedulePhase() != BattleSchedulePhase.VOTING || head == null || head.hour() == null) {
			return false;
		}
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle != null && battle.hasStarted()) {
			return false;
		}
		Integer provinceId = BattleScheduleService.resolveScheduledProvinceId(war);
		if (provinceId == null) {
			if (!BattleScheduleService.hasNoNextBattle(war)) {
				// The post-battle choice is still open; its deadline settles it.
				return false;
			}
			WarResolutionService.endWhitePeace(war);
			return true;
		}
		if (head.autoresolve()) {
			return BattleAutoresolveService.resolve(war);
		}
		return BattleScheduleService.scheduleBattleAtProvince(war, provinceId,
				BattleWindowService.atScheduleHour(head.date(), head.hour()));
	}

	/** Staff: makes a date a battle day now. Null when done, otherwise why not. */
	public static String lockDay(War war, LocalDate date, Instant now) {
		if (date.isBefore(BattleWindowService.scheduleDate(now))) {
			return "That day is in the past.";
		}
		if (war.getAvailability().battleDay(date) != null) {
			return "That day is already a battle day.";
		}
		if (!now.isBefore(AvailabilityCalendar.windowClosesAt(date))) {
			return "That day's battle window has closed.";
		}
		lock(war, date, false, now);
		sync(war);
		WarHistoryEvents.votesChanged(war);
		return null;
	}

	/** Staff: takes a battle day back. A day already decided stays "no battle"; a later one is decided at its day lock. */
	public static String unlockDay(War war, LocalDate date) {
		if (war.getAvailability().battleDay(date) == null) {
			return "That day is not a battle day.";
		}
		if (date.equals(war.getBattleDay())) {
			String busy = busy(war);
			if (busy != null) {
				return busy;
			}
			reopen(war);
		}
		war.getAvailability().removeBattleDay(date);
		sync(war);
		WarHistoryEvents.votesChanged(war);
		return null;
	}

	/** Staff: fixes a battle day's start hour (already normalised). Sets the battle at once when it is the next one. */
	public static String setHour(War war, LocalDate date, int hour, Instant now) {
		LockedBattleDay day = war.getAvailability().battleDay(date);
		if (day == null) {
			return "That day is not a battle day.";
		}
		if (!BattleWindowService.atScheduleHour(date, hour).isAfter(now)) {
			return "That time has already passed.";
		}
		boolean next = date.equals(war.getBattleDay());
		String busy = next ? busy(war) : null;
		if (busy != null) {
			return busy;
		}
		war.getAvailability().putBattleDay(day.withHour(hour, false));
		if (next) {
			reopen(war);
			scheduleNext(war);
		}
		WarHistoryEvents.votesChanged(war);
		return null;
	}

	/** Staff: drops the next battle day. Returns false when there is none. */
	public static boolean skip(War war) {
		LockedBattleDay head = war.getAvailability().head();
		if (head == null) {
			return false;
		}
		reopen(war);
		war.getAvailability().removeBattleDay(head.date());
		sync(war);
		WarHistoryEvents.postponed(war);
		WarHistoryEvents.votesChanged(war);
		return true;
	}

	/** Why the war's current battle day cannot be changed now; null when it can. */
	private static String busy(War war) {
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle != null && battle.hasStarted()) {
			return "That day's battle is being fought.";
		}
		return war.getBattleSchedulePhase() == BattleSchedulePhase.AUTORESOLVE_PENDING
				? "That day's battle is being autoresolved." : null;
	}

	/** A battle set on its province goes back to waiting for its day; the prepared battle is kept for reuse. */
	private static void reopen(War war) {
		BattleSchedulePhase phase = war.getBattleSchedulePhase();
		if (phase == BattleSchedulePhase.SCHEDULED || phase == BattleSchedulePhase.AUTORESOLVE_PENDING) {
			BattleScheduleService.releaseScheduledBattle(war);
		}
	}

	/** Dev: marks the hour on the date for every member of the given sides, under test accounts. Returns marks added. */
	public static int castMarks(War war, LocalDate date, int hour, BelligerentRole... sides) {
		int added = 0;
		for (BelligerentRole role : sides) {
			Side side = role == BelligerentRole.ATTACKER ? war.getAttackers() : war.getDefenders();
			for (String member : BattleSideMembers.collectEligibleMemberNames(side)) {
				UUID account = BattleScheduleLookups.spoofMemberUuid(member);
				Set<Integer> hours = new TreeSet<>(war.getAvailability().marksOn(account, date));
				if (hours.add(hour)) {
					war.getAvailability().setMarks(account, date, hours);
					added++;
				}
			}
		}
		WarHistoryEvents.votesChanged(war);
		return added;
	}

	/** Staff summary: the battle days, then each visible date with its state and marks per hour. */
	public static List<String> describe(War war, Instant now) {
		BattleAvailability availability = war.getAvailability();
		List<String> lines = new ArrayList<>();
		lines.add("§eAvailability of war " + war.getId() + " §7(need " + AvailabilityTally.need(war, BelligerentRole.ATTACKER)
				+ " attackers and " + AvailabilityTally.need(war, BelligerentRole.DEFENDER) + " defenders at one time;"
				+ " " + availability.getNoBattleStreak() + " days in a row without a battle)");
		for (LockedBattleDay day : availability.battleDays()) {
			lines.add("§6Battle day " + day.date() + "§7: "
					+ (day.hour() == null ? "time open" : BattleWindowService.formatHourLabel(day.hour()))
					+ (day.forced() ? ", forced" : "") + (day.autoresolve() ? ", autoresolved" : ""));
		}
		for (LocalDate date : AvailabilityCalendar.visibleDates(war, now)) {
			StringBuilder line = new StringBuilder("§f" + date + " §7" + AvailabilityCalendar.state(war, date).toJson() + ":");
			for (HourCount count : AvailabilityTally.count(war, date)) {
				line.append(" ").append(BattleWindowService.formatHourLabel(count.hour()).replace(" CET", ""))
						.append(" ").append(count.attackers()).append("/").append(count.defenders());
			}
			lines.add(line.toString());
		}
		Instant next = AvailabilityCalendar.nextLockAt(war, now);
		lines.add("§7Next lock: §f" + (next == null ? "-" : CampaignUiTimeFormatter.formatDateTime(next)));
		return lines;
	}

	/** A line for every member of both sides and every mercenary they hired, now or at their next login. */
	static void tellEveryone(War war, String text) {
		Set<String> members = new LinkedHashSet<>();
		for (Side side : new Side[] {war.getAttackers(), war.getDefenders()}) {
			members.addAll(BattleSideMembers.collectEligibleMemberNames(side));
			for (MercenaryEngagements.Engagement engagement : MercenaryEngagements.on(war, side)) {
				members.addAll(engagement.company().getEnlisted());
			}
		}
		for (String member : members) {
			WarNotices.send(member, text);
		}
	}
}
