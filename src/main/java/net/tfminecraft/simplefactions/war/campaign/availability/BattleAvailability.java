package net.tfminecraft.simplefactions.war.campaign.availability;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * A war's battle availability: which start hours each account can fight on each date, the
 * "none of these days work" answers, the locked battle days still to fight, and how far the day
 * locks got. Accounts, not characters, mark, as votes always were.
 */
public final class BattleAvailability {
	/** Account → date → battle-day start hours it can make. Never holds an empty set. */
	private final Map<UUID, Map<LocalDate, Set<Integer>>> marks = new HashMap<>();
	/** Account → the last date its "none of these days work" answer covered. */
	private final Map<UUID, LocalDate> none = new HashMap<>();
	/** Locked battle days not fought yet, earliest first. */
	private final List<LockedBattleDay> battleDays = new ArrayList<>();
	/** Dates decided "no battle" in a row since the last battle day. */
	private int noBattleStreak;
	/** The last date whose day lock was processed; null before the first. */
	private LocalDate lastDayLockProcessed;
	/** No date before this one can become a battle day (end of the buildup, or the day after the last battle). */
	private LocalDate earliestBattleDay;
	/** Reminders already sent, as {@code reason|date}, so a restart does not send them again. */
	private final Set<String> remindersSent = new LinkedHashSet<>();
	/** Why a date got no battle when it was not for lack of people. */
	private final Map<LocalDate, NoBattleReason> noBattleReasons = new TreeMap<>();

	/** Why a decided date has no battle, when it is not that too few could come at one time. */
	public enum NoBattleReason {
		/** Battle days were already waiting to be fought when the date was decided. */
		QUEUE_FULL,
		/** The date's lock came before the war could plan it, or no start time was left at its time lock. */
		TOO_LATE
	}

	public Set<Integer> marksOn(UUID account, LocalDate date) {
		Map<LocalDate, Set<Integer>> byDate = marks.get(account);
		Set<Integer> hours = byDate == null ? null : byDate.get(date);
		return hours == null ? Set.of() : Collections.unmodifiableSet(hours);
	}

	public boolean hasMarksOn(UUID account, LocalDate date) {
		return !marksOn(account, date).isEmpty();
	}

	/** Replaces the account's marks on one date; an empty set clears them. */
	public void setMarks(UUID account, LocalDate date, Set<Integer> hours) {
		if (hours == null || hours.isEmpty()) {
			Map<LocalDate, Set<Integer>> byDate = marks.get(account);
			if (byDate != null) {
				byDate.remove(date);
				if (byDate.isEmpty()) {
					marks.remove(account);
				}
			}
			return;
		}
		marks.computeIfAbsent(account, ignored -> new TreeMap<>()).put(date, new TreeSet<>(hours));
	}

	/** Every account's marks, read-only. */
	public Map<UUID, Map<LocalDate, Set<Integer>>> marks() {
		return Collections.unmodifiableMap(marks);
	}

	public LocalDate noneUntil(UUID account) {
		return none.get(account);
	}

	public void setNone(UUID account, LocalDate until) {
		if (until == null) {
			none.remove(account);
		} else {
			none.put(account, until);
		}
	}

	public Map<UUID, LocalDate> none() {
		return Collections.unmodifiableMap(none);
	}

	public List<LockedBattleDay> battleDays() {
		return Collections.unmodifiableList(battleDays);
	}

	public LockedBattleDay head() {
		return battleDays.isEmpty() ? null : battleDays.get(0);
	}

	public LockedBattleDay battleDay(LocalDate date) {
		for (LockedBattleDay day : battleDays) {
			if (day.date().equals(date)) {
				return day;
			}
		}
		return null;
	}

	/** Adds a battle day, or replaces the one on the same date, keeping the queue in date order. */
	public void putBattleDay(LockedBattleDay day) {
		removeBattleDay(day.date());
		battleDays.add(day);
		battleDays.sort(Comparator.comparing(LockedBattleDay::date));
	}

	public boolean removeBattleDay(LocalDate date) {
		return battleDays.removeIf(day -> day.date().equals(date));
	}

	public void clearBattleDays() {
		battleDays.clear();
	}

	public int getNoBattleStreak() {
		return noBattleStreak;
	}

	public void setNoBattleStreak(int noBattleStreak) {
		this.noBattleStreak = Math.max(0, noBattleStreak);
	}

	public LocalDate getLastDayLockProcessed() {
		return lastDayLockProcessed;
	}

	public void setLastDayLockProcessed(LocalDate lastDayLockProcessed) {
		this.lastDayLockProcessed = lastDayLockProcessed;
	}

	public LocalDate getEarliestBattleDay() {
		return earliestBattleDay;
	}

	public void setEarliestBattleDay(LocalDate earliestBattleDay) {
		this.earliestBattleDay = earliestBattleDay;
	}

	/** Why the date has no battle; null when too few could come at one time (or it is not decided). */
	public NoBattleReason noBattleReason(LocalDate date) {
		return noBattleReasons.get(date);
	}

	public void setNoBattleReason(LocalDate date, NoBattleReason reason) {
		if (reason == null) {
			noBattleReasons.remove(date);
		} else {
			noBattleReasons.put(date, reason);
		}
	}

	/** Records a reminder; false when it was already sent. */
	public boolean markReminderSent(String key) {
		return remindersSent.add(key);
	}

	public Set<String> remindersSent() {
		return Collections.unmodifiableSet(remindersSent);
	}

	/**
	 * Forgets what only concerned dates before {@code today}: marks on them, "none" answers that
	 * ended before it, and their reminders. Returns whether anything went.
	 */
	public boolean pruneBefore(LocalDate today) {
		boolean changed = false;
		for (Map<LocalDate, Set<Integer>> byDate : marks.values()) {
			changed |= byDate.keySet().removeIf(date -> date.isBefore(today));
		}
		changed |= marks.values().removeIf(Map::isEmpty);
		changed |= none.values().removeIf(until -> until.isBefore(today));
		changed |= noBattleReasons.keySet().removeIf(date -> date.isBefore(today));
		changed |= remindersSent.removeIf(key -> {
			String date = key.substring(key.indexOf('|') + 1);
			return date.compareTo(today.toString()) < 0;
		});
		return changed;
	}

	/** The saved form, as {@code availability} in the war's file. */
	public static final class Data {
		public Map<String, Map<String, List<Integer>>> marks;
		public Map<String, String> none;
		public List<DayData> battleDays;
		public int noBattleStreak;
		public String lastDayLockProcessed;
		public String earliestBattleDay;
		public List<String> remindersSent;
		/** ISO date → {@link NoBattleReason} name. */
		public Map<String, String> noBattleReasons;
	}

	public static final class DayData {
		public String date;
		public Integer hour;
		public boolean forced;
		public boolean autoresolve;
	}

	public Data toData() {
		Data data = new Data();
		data.marks = new LinkedHashMap<>();
		marks.forEach((account, byDate) -> {
			Map<String, List<Integer>> dates = new LinkedHashMap<>();
			byDate.forEach((date, hours) -> dates.put(date.toString(), new ArrayList<>(hours)));
			data.marks.put(account.toString(), dates);
		});
		data.none = new LinkedHashMap<>();
		none.forEach((account, until) -> data.none.put(account.toString(), until.toString()));
		data.battleDays = new ArrayList<>();
		for (LockedBattleDay day : battleDays) {
			DayData row = new DayData();
			row.date = day.date().toString();
			row.hour = day.hour();
			row.forced = day.forced();
			row.autoresolve = day.autoresolve();
			data.battleDays.add(row);
		}
		data.noBattleStreak = noBattleStreak;
		data.lastDayLockProcessed = lastDayLockProcessed == null ? null : lastDayLockProcessed.toString();
		data.earliestBattleDay = earliestBattleDay == null ? null : earliestBattleDay.toString();
		data.remindersSent = new ArrayList<>(remindersSent);
		data.noBattleReasons = new LinkedHashMap<>();
		noBattleReasons.forEach((date, reason) -> data.noBattleReasons.put(date.toString(), reason.name()));
		return data;
	}

	/** Rows that cannot be read are skipped, so one bad entry never loses the rest. */
	public static BattleAvailability fromData(Data data) {
		BattleAvailability availability = new BattleAvailability();
		if (data == null) {
			return availability;
		}
		if (data.marks != null) {
			data.marks.forEach((account, dates) -> {
				UUID id = uuid(account);
				if (id != null && dates != null) {
					dates.forEach((date, hours) -> {
						LocalDate day = date(date);
						if (day != null && hours != null) {
							Set<Integer> valid = new TreeSet<>();
							for (Integer hour : hours) {
								if (hour != null) {
									valid.add(hour);
								}
							}
							availability.setMarks(id, day, valid);
						}
					});
				}
			});
		}
		if (data.none != null) {
			data.none.forEach((account, until) -> {
				UUID id = uuid(account);
				LocalDate day = date(until);
				if (id != null && day != null) {
					availability.setNone(id, day);
				}
			});
		}
		if (data.battleDays != null) {
			for (DayData row : data.battleDays) {
				LocalDate day = row == null ? null : date(row.date);
				if (day != null) {
					availability.putBattleDay(new LockedBattleDay(day, row.hour, row.forced, row.autoresolve));
				}
			}
		}
		availability.setNoBattleStreak(data.noBattleStreak);
		availability.setLastDayLockProcessed(date(data.lastDayLockProcessed));
		availability.setEarliestBattleDay(date(data.earliestBattleDay));
		if (data.remindersSent != null) {
			availability.remindersSent.addAll(data.remindersSent);
		}
		if (data.noBattleReasons != null) {
			data.noBattleReasons.forEach((date, reason) -> {
				LocalDate day = date(date);
				NoBattleReason why = reason(reason);
				if (day != null && why != null) {
					availability.setNoBattleReason(day, why);
				}
			});
		}
		return availability;
	}

	private static UUID uuid(String text) {
		try {
			return text == null ? null : UUID.fromString(text);
		} catch (IllegalArgumentException unreadable) {
			return null;
		}
	}

	private static NoBattleReason reason(String text) {
		try {
			return text == null ? null : NoBattleReason.valueOf(text);
		} catch (IllegalArgumentException unreadable) {
			return null;
		}
	}

	private static LocalDate date(String text) {
		try {
			return text == null ? null : LocalDate.parse(text);
		} catch (RuntimeException unreadable) {
			return null;
		}
	}
}
