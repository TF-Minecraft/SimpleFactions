package net.tfminecraft.simplefactions.war.campaign.vote;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityCalendar;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickEligibility;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiTimeFormatter;
import net.tfminecraft.simplefactions.war.campaign.vote.InstallationPickWarnings.Warning;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/**
 * Warns faction leaders, in game, before their installation choices for the coming battle lock
 * ({@code battle_schedule.vote_close_hour} on the battle day). Only leaders choose installations, and
 * only leaders whose faction has an installation it could commit are warned. When they are warned is
 * set in {@code war.installation_warnings} ({@link InstallationPickWarnings}): a line when the battle's
 * start time is decided, and one per {@code before_lock} entry. Each goes out once per battle day (the
 * war remembers it, across restarts; a later staff change of the hour, or unlocking and locking the
 * same date again, does not resend them); when several fall due at once (after downtime) only the latest is sent. A leader who starts a
 * character while a warning applies is told then too.
 */
public final class InstallationPickReminders {
	static final String TIME_LOCK_KEY = "picks_time_lock";
	static final String LOCK_SOON_KEY = "picks_lock_soon";
	private static final Logger LOGGER = Logger.getLogger(InstallationPickReminders.class.getName());

	/** A warning that has fallen due for a battle day; {@code dueAt} orders them. */
	record Due(String key, Instant dueAt, Warning warning, boolean timeLock) {
	}

	/** A leader to warn: their faction, their character and how many installations they chose. */
	record Recipient(Faction faction, String leaderKey, int chosen) {
	}

	private InstallationPickReminders() {
	}

	/** Every minute: sends the warning that fell due. Returns whether the war changed (it must be saved). */
	public static boolean process(War war, Instant now) {
		LocalDate day = coming(war, now);
		if (day == null) {
			return false;
		}
		List<Due> due = dueNow(war, day, now, false);
		if (due.isEmpty()) {
			return false;
		}
		// Every warning that fell due counts as sent. Only the latest is worth telling, and only when
		// no later before-lock warning went already. A sent time-lock line is left out of this list, so
		// a before-lock warning added to the config after it can still go once.
		int lastSent = -1;
		for (int i = 0; i < due.size(); i++) {
			if (war.getAvailability().remindersSent().contains(due.get(i).key())) {
				lastSent = i;
			}
		}
		boolean changed = false;
		for (Due each : due) {
			changed |= war.getAvailability().markReminderSent(each.key());
		}
		if (lastSent < due.size() - 1) {
			send(war, day, due.getLast(), now);
		}
		return changed;
	}

	/** Tells the online leaders it applies to; a failure for one faction does not stop the others. */
	private static void send(War war, LocalDate day, Due due, Instant now) {
		guarded(war, () -> {
			for (Faction faction : factions(war)) {
				guarded(war, () -> {
					Recipient recipient = recipient(war, faction);
					Player player = recipient == null ? null : Members.player(recipient.leaderKey());
					if (player != null && applies(due, recipient)) {
						player.sendMessage(message(war, day, due, recipient, now));
					}
				});
			}
		});
	}

	/** The warnings only inform, so a failure is logged and the tick or the login goes on. */
	private static void guarded(War war, Runnable action) {
		try {
			action.run();
		} catch (RuntimeException failure) {
			LOGGER.log(Level.WARNING, "[SimpleFactions] Could not send installation warnings for war " + war.getId(), failure);
		}
	}

	/** When a character is played: a line for each war whose current warning applies to this leader. */
	public static void onCharacterStart(Player player, Instant now) {
		String key = Members.key(player);
		if (key == null) {
			return;
		}
		for (War war : WarManager.getActive()) {
			guarded(war, () -> {
				LocalDate day = coming(war, now);
				List<Due> due = day == null ? List.of() : dueNow(war, day, now, true);
				// A warning the tick has not sent yet reaches this leader within the minute anyway.
				if (due.isEmpty() || !war.getAvailability().remindersSent().contains(due.getLast().key())) {
					return;
				}
				Faction led = factionLedBy(war, key);
				Recipient recipient = led == null ? null : recipient(war, led);
				if (recipient != null && applies(due.getLast(), recipient)) {
					player.sendMessage(message(war, day, due.getLast(), recipient, now));
				}
			});
		}
	}

	/**
	 * The war's battle day whose installation choices are still open, or null: the war must be
	 * planning or waiting for that battle, the day must be a locked battle day that is fought (not
	 * autoresolved), its battle not begun, and the choices not locked yet.
	 */
	static LocalDate coming(War war, Instant now) {
		InstallationPickWarnings settings = Cache.warInstallationWarnings;
		if (war == null || !war.isActive() || now == null || settings == null || !settings.enabled()) {
			return null;
		}
		BattleSchedulePhase phase = war.getBattleSchedulePhase();
		if (phase != BattleSchedulePhase.BUILDUP && phase != BattleSchedulePhase.VOTING
				&& phase != BattleSchedulePhase.SCHEDULED) {
			return null;
		}
		LocalDate day = war.getBattleDay();
		LockedBattleDay locked = day == null ? null : war.getAvailability().battleDay(day);
		Instant lock = locked == null ? null : locksAt(day);
		// No lock time: vote_close_hour is outside the battle-day clock, so nothing can be warned about.
		if (locked == null || locked.autoresolve() || lock == null || !now.isBefore(lock)) {
			return null;
		}
		Battle battle = BattleManager.getByWarId(war.getId());
		return battle != null && battle.hasStarted() ? null : day;
	}

	/**
	 * The warnings for the day whose time has come, earliest first (the last is the current one).
	 * The time-lock line is due once the start time is set, at once when staff set it early. Once it
	 * went, the per-minute pass leaves it out ({@code withSentTimeLock} false): staff setting the hour
	 * early would otherwise keep it the latest and silently use up the warnings after it. A login
	 * ({@code withSentTimeLock} true) keeps a sent time-lock line but ranks it first, so the latest
	 * before-lock warning that went is the one repeated.
	 */
	static List<Due> dueNow(War war, LocalDate day, Instant now, boolean withSentTimeLock) {
		InstallationPickWarnings settings = Cache.warInstallationWarnings;
		List<Due> due = new ArrayList<>();
		LockedBattleDay locked = war.getAvailability().battleDay(day);
		String timeLockKey = TIME_LOCK_KEY + "|" + day;
		if (settings.atTimeLock() != null && locked != null && locked.hour() != null
				&& (withSentTimeLock || !war.getAvailability().remindersSent().contains(timeLockKey))) {
			Instant timeLock = AvailabilityCalendar.timeLockAt(day);
			Instant at = war.getAvailability().remindersSent().contains(timeLockKey) ? Instant.MIN
					: timeLock.isAfter(now) ? now : timeLock;
			due.add(new Due(timeLockKey, at, settings.atTimeLock(), true));
		}
		Instant lock = locksAt(day);
		for (Warning warning : settings.beforeLock()) {
			Instant from = lock.minus(warning.before());
			if (!now.isBefore(from)) {
				// The sent-key pruner reads everything after the first '|' as the date; "date|minutes"
				// still sorts with that date, so these keys go the day after, like the others. The minutes
				// are part of the key, so changing hours_before after a warning went sends it again.
				due.add(new Due(LOCK_SOON_KEY + "|" + day + "|" + warning.before().toMinutes(), from, warning, false));
			}
		}
		// Stable: on a tie the before-lock warning (added later) is the latest.
		due.sort((a, b) -> a.dueAt().compareTo(b.dueAt()));
		return due;
	}

	/** When the installation choices for that battle day lock. */
	static Instant locksAt(LocalDate day) {
		return BattleWindowService.atScheduleHour(day, Cache.warVoteCloseHour);
	}

	/** Both sides' factions in the war. */
	private static List<Faction> factions(War war) {
		List<Faction> factions = new ArrayList<>(BattleSideMembers.collectParticipatingFactions(war.getAttackers()));
		factions.addAll(BattleSideMembers.collectParticipatingFactions(war.getDefenders()));
		return factions;
	}

	/** The leaders of both sides' factions that still have an installation they could choose. */
	static List<Recipient> recipients(War war) {
		List<Recipient> recipients = new ArrayList<>();
		for (Faction faction : factions(war)) {
			Recipient recipient = recipient(war, faction);
			if (recipient != null) {
				recipients.add(recipient);
			}
		}
		return recipients;
	}

	/** The faction in the war that this character leads, or null. {@code /war installations} uses it too. */
	public static Faction factionLedBy(War war, String leaderKey) {
		if (war == null || leaderKey == null) {
			return null;
		}
		for (Faction faction : factions(war)) {
			if (leaderKey.equals(faction.getLeader())) {
				return faction;
			}
		}
		return null;
	}

	/**
	 * The faction's leader as a recipient, or null when it has none or nothing the leader could
	 * choose: the defender's automatic port and installations staff forced in are not choices.
	 */
	private static Recipient recipient(War war, Faction faction) {
		String leader = faction.getLeader();
		if (leader == null || leader.isBlank()) {
			return null;
		}
		for (Installation installation : BattleInstallationPickEligibility.listPickableInstallations(war, faction)) {
			if (isChoice(war, faction, installation.getId())) {
				return new Recipient(faction, leader, chosen(war, faction));
			}
		}
		return null;
	}

	private static boolean isChoice(War war, Faction faction, String installationId) {
		return !BattleInstallationPickService.isDefenderZocPort(war, faction, installationId)
				&& !BattleInstallationPickService.isForced(war, faction.getId(), installationId);
	}

	/** Installations the leader chose: not the defender's automatic port, not ones staff forced in. */
	static int chosen(War war, Faction faction) {
		Set<String> picks = BattleInstallationPickService.getPicks(war, faction.getId());
		int chosen = 0;
		for (String installationId : picks) {
			if (isChoice(war, faction, installationId)) {
				chosen++;
			}
		}
		return chosen;
	}

	static boolean applies(Due due, Recipient recipient) {
		return recipient.chosen() == 0 || due.warning().includeCommitted();
	}

	static Component message(War war, LocalDate day, Due due, Recipient recipient, Instant now) {
		Instant lock = locksAt(day);
		String when = BattleVoteReminders.formatRemaining(Duration.between(now, lock)) + " §7("
				+ CampaignUiTimeFormatter.formatDateTime(lock) + "§7)";
		String label = AvailabilityCalendar.label(day);
		String text = "§6⚔ §e" + war.getName() + "§6: §f" + (due.timeLock()
				? "choose the installations you commit to the battle on " + label + "; your choices lock in " + when + "§f."
				: "your installation choices for the battle on " + label + " lock in " + when + "§f.");
		text += recipient.chosen() == 0
				? " §cYou have not chosen any yet."
				: " §7You have chosen " + recipient.chosen() + "; you can still change them.";
		return legacy(text)
				.append(Component.space())
				.append(legacy("§a§l[Choose installations]")
						.clickEvent(ClickEvent.runCommand("/war installations " + war.getId()))
						.hoverEvent(HoverEvent.showText(legacy("§7Open the installation choices"))));
	}

	private static Component legacy(String text) {
		return LegacyComponentSerializer.legacySection().deserialize(StringFormatter.formatHex(text));
	}
}
