package net.tfminecraft.simplefactions.war.campaign.vote;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityCalendar;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityCalendar.DateState;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityScheduler;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityTally;
import net.tfminecraft.simplefactions.war.campaign.availability.BattleAvailability;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiTimeFormatter;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.history.WarHistoryEvents;
import net.tfminecraft.simplefactions.war.history.WarHistoryJson;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/**
 * Reminds players who have not marked when they can fight, in game (a clickable line to online
 * players) and on Discord (a {@code vote_reminder} event the website turns into DMs):
 * <ul>
 * <li>{@code mark_availability}, 24 hours before each open date's day lock, to those with no marks on
 * any date shown and no "none of these days" answer covering that date;</li>
 * <li>{@code battle_day_locked}, when a date becomes a battle day, to those with no marks on it;</li>
 * <li>{@code time_lock_soon}, 3 hours before a battle day's time lock, to those with no marks on it.</li>
 * </ul>
 * Each goes out once (the war remembers it, across restarts). A player who starts a character
 * while one applies to them is reminded then too. Mercenaries are reminded like their hirer's members.
 */
public final class BattleVoteReminders {
	static final Duration MARK_BEFORE_DAY_LOCK = Duration.ofHours(24);
	static final Duration SOON_BEFORE_TIME_LOCK = Duration.ofHours(3);

	/** Why a reminder is sent; the website words its DM by this. */
	public enum Reason {
		MARK_AVAILABILITY("mark_availability"),
		BATTLE_DAY_LOCKED("battle_day_locked"),
		TIME_LOCK_SOON("time_lock_soon");

		private final String json;

		Reason(String json) {
			this.json = json;
		}

		public String toJson() {
			return json;
		}
	}

	/** One reminder: why, the date it is about, and when that date's next lock falls. */
	public record Reminder(Reason reason, LocalDate date, Instant locksAt) {
	}

	private BattleVoteReminders() {
	}

	/** Every minute: sends the reminders that fell due. Returns whether one was sent (the war must be saved). */
	public static boolean process(War war, Instant now) {
		if (!AvailabilityScheduler.isScheduling(war)) {
			return false;
		}
		boolean sent = false;
		for (LocalDate date : AvailabilityCalendar.visibleDates(war, now)) {
			Reminder due = dueOn(war, date, now);
			if (due != null) {
				sent |= send(war, due, now);
			}
		}
		return sent;
	}

	/** The timed reminder for a date when its time has come and its lock is still ahead; null otherwise. */
	static Reminder dueOn(War war, LocalDate date, Instant now) {
		DateState state = AvailabilityCalendar.state(war, date);
		if (state == DateState.OPEN) {
			Instant lock = AvailabilityCalendar.dayLockAt(date);
			return within(now, lock, MARK_BEFORE_DAY_LOCK) ? new Reminder(Reason.MARK_AVAILABILITY, date, lock) : null;
		}
		Instant lock = AvailabilityCalendar.timeLockAt(date);
		return state == DateState.BATTLE && AvailabilityCalendar.editable(war, date, now)
				&& within(now, lock, SOON_BEFORE_TIME_LOCK) ? new Reminder(Reason.TIME_LOCK_SOON, date, lock) : null;
	}

	private static boolean within(Instant now, Instant lock, Duration before) {
		return !now.isBefore(lock.minus(before)) && now.isBefore(lock);
	}

	/** At the day lock that made the date a battle day. */
	public static void battleDayLocked(War war, LocalDate date, Instant now) {
		send(war, new Reminder(Reason.BATTLE_DAY_LOCKED, date, AvailabilityCalendar.timeLockAt(date)), now);
	}

	/** Sends a reminder once: to the website for every account it concerns, and in game to those online. */
	static boolean send(War war, Reminder reminder, Instant now) {
		if (!war.getAvailability().markReminderSent(reminder.reason().toJson() + "|" + reminder.date())) {
			return false;
		}
		List<LocalDate> visible = AvailabilityCalendar.visibleDates(war, now);
		List<UUID> accounts = new ArrayList<>();
		for (UUID account : AvailabilityTally.eligibleAccounts(war).keySet()) {
			if (applies(war, account, reminder, visible)) {
				accounts.add(account);
			}
		}
		if (!accounts.isEmpty()) {
			WarHistoryEvents.voteReminder(war, reminder.reason().toJson(),
					reminder.reason() == Reason.MARK_AVAILABILITY ? null : reminder.date(), reminder.locksAt(), accounts);
		}
		// No server (staff tools in tests, shutdown): nobody is online to tell.
		List<Player> online = Bukkit.getServer() == null ? List.of() : new ArrayList<>(Bukkit.getOnlinePlayers());
		for (Player player : online) {
			if (BattleScheduleLookups.voterFaction(war, player) != null
					&& applies(war, player.getUniqueId(), reminder, visible)) {
				player.sendMessage(message(war, reminder, now));
			}
		}
		return true;
	}

	/** Whether the reminder concerns this account: it has not marked what the reminder asks about. */
	static boolean applies(War war, UUID account, Reminder reminder, List<LocalDate> visible) {
		BattleAvailability availability = war.getAvailability();
		if (reminder.reason() != Reason.MARK_AVAILABILITY) {
			return !availability.hasMarksOn(account, reminder.date());
		}
		for (LocalDate date : visible) {
			if (availability.hasMarksOn(account, date)) {
				return false;
			}
		}
		LocalDate none = availability.noneUntil(account);
		return none == null || none.isBefore(reminder.date());
	}

	/** When a character is played: one line for each war with a reminder that applies to this player now. */
	public static void onCharacterStart(Player player, Instant now) {
		for (War war : WarManager.getActive()) {
			Reminder pending = AvailabilityScheduler.isScheduling(war)
					&& BattleScheduleLookups.voterFaction(war, player) != null
					? pendingFor(war, player.getUniqueId(), now) : null;
			if (pending != null) {
				player.sendMessage(message(war, pending, now));
			}
		}
	}

	/**
	 * The reminder that applies to the account now: a battle day whose time is still open and that it
	 * has not marked first, otherwise the next open date when it has marked nothing at all.
	 */
	static Reminder pendingFor(War war, UUID account, Instant now) {
		List<LocalDate> visible = AvailabilityCalendar.visibleDates(war, now);
		Reminder firstOpen = null;
		for (LocalDate date : visible) {
			DateState state = AvailabilityCalendar.state(war, date);
			if (state == DateState.BATTLE && AvailabilityCalendar.editable(war, date, now)) {
				Instant lock = AvailabilityCalendar.timeLockAt(date);
				Reminder battle = new Reminder(within(now, lock, SOON_BEFORE_TIME_LOCK)
						? Reason.TIME_LOCK_SOON : Reason.BATTLE_DAY_LOCKED, date, lock);
				if (applies(war, account, battle, visible)) {
					return battle;
				}
			} else if (firstOpen == null && state == DateState.OPEN && AvailabilityCalendar.editable(war, date, now)) {
				firstOpen = new Reminder(Reason.MARK_AVAILABILITY, date, AvailabilityCalendar.dayLockAt(date));
			}
		}
		return firstOpen != null && applies(war, account, firstOpen, visible) ? firstOpen : null;
	}

	static Component message(War war, Reminder reminder, Instant now) {
		String when = formatRemaining(Duration.between(now, reminder.locksAt())) + " §7("
				+ CampaignUiTimeFormatter.formatDateTime(reminder.locksAt()) + "§7)";
		String day = AvailabilityCalendar.label(reminder.date());
		String text = "§6⚔ §e" + war.getName() + "§6: §f" + switch (reminder.reason()) {
			case MARK_AVAILABILITY -> "mark the days and times you can fight. §7The next day is decided in " + when + ".";
			case BATTLE_DAY_LOCKED -> day + " is a battle day. Mark the times you can make. §7The time is decided in "
					+ when + ".";
			case TIME_LOCK_SOON -> "the battle time for " + day + " is decided in " + when + "§f. Mark the times you can make.";
		};
		Component line = legacy(text)
				.append(Component.space())
				.append(legacy("§a§l[Vote in game]")
						.clickEvent(ClickEvent.runCommand("/war vote " + war.getId()))
						.hoverEvent(HoverEvent.showText(legacy("§7Open the availability grid"))));
		String url = voteUrl(war);
		if (url != null) {
			line = line.append(Component.space())
					.append(legacy("§b§l[Vote on the website]")
							.clickEvent(ClickEvent.openUrl(url))
							.hoverEvent(HoverEvent.showText(legacy("§7" + url))));
		}
		return line;
	}

	/** {@code vote_url} for this war, or null when none is configured. */
	public static String voteUrl(War war) {
		String url = Cache.warAvailabilityVoteUrl;
		return url == null || url.isBlank() ? null : url.replace("{war_key}", WarHistoryJson.warKey(war));
	}

	private static Component legacy(String text) {
		return LegacyComponentSerializer.legacySection().deserialize(StringFormatter.formatHex(text));
	}

	/** {@code 2d 4h}, {@code 3h 20m} or {@code 45m}. */
	static String formatRemaining(Duration remaining) {
		long minutes = Math.max(1, (remaining.toSeconds() + 59) / 60);
		long days = minutes / 1440;
		long hours = (minutes % 1440) / 60;
		long mins = minutes % 60;
		if (days > 0) {
			return days + "d " + hours + "h";
		}
		if (hours > 0) {
			return mins > 0 ? hours + "h " + mins + "m" : hours + "h";
		}
		return mins + "m";
	}
}
