package net.tfminecraft.simplefactions.war.campaign.vote;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.identity.Members;
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
 * war remembers it, across restarts); when several fall due at once (after downtime) only the latest
 * is sent. A leader who starts a character while a warning applies is told then too.
 */
public final class InstallationPickReminders {
	static final String TIME_LOCK_KEY = "picks_time_lock";
	static final String LOCK_SOON_KEY = "picks_lock_soon";

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
		List<Due> due = dueNow(war, day, now);
		if (due.isEmpty()) {
			return false;
		}
		Due latest = due.getLast();
		// Every warning that fell due counts as sent; only the latest is worth telling.
		boolean changed = false;
		for (Due each : due.subList(0, due.size() - 1)) {
			changed |= war.getAvailability().markReminderSent(each.key());
		}
		if (war.getAvailability().markReminderSent(latest.key())) {
			changed = true;
			for (Recipient recipient : recipients(war)) {
				Player player = Members.player(recipient.leaderKey());
				if (player != null && applies(latest, recipient)) {
					player.sendMessage(message(war, day, latest, recipient, now));
				}
			}
		}
		return changed;
	}

	/** When a character is played: a line for each war whose current warning applies to this leader. */
	public static void onCharacterStart(Player player, Instant now) {
		String key = Members.key(player);
		if (key == null) {
			return;
		}
		for (War war : WarManager.getActive()) {
			LocalDate day = coming(war, now);
			List<Due> due = day == null ? List.of() : dueNow(war, day, now);
			if (due.isEmpty()) {
				continue;
			}
			for (Recipient recipient : recipients(war)) {
				if (key.equals(recipient.leaderKey()) && applies(due.getLast(), recipient)) {
					player.sendMessage(message(war, day, due.getLast(), recipient, now));
				}
			}
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
		if (locked == null || locked.autoresolve() || !now.isBefore(locksAt(day))) {
			return null;
		}
		Battle battle = BattleManager.getByWarId(war.getId());
		return battle != null && battle.hasStarted() ? null : day;
	}

	/** The warnings for the day whose time has come, earliest first (the last is the one to send). */
	static List<Due> dueNow(War war, LocalDate day, Instant now) {
		InstallationPickWarnings settings = Cache.warInstallationWarnings;
		List<Due> due = new ArrayList<>();
		LockedBattleDay locked = war.getAvailability().battleDay(day);
		if (settings.atTimeLock() != null && locked != null && locked.hour() != null) {
			Instant timeLock = AvailabilityCalendar.timeLockAt(day);
			// Staff can set the hour before the time lock: then the line is due as soon as it is set.
			due.add(new Due(TIME_LOCK_KEY + "|" + day, timeLock.isAfter(now) ? now : timeLock,
					settings.atTimeLock(), true));
		}
		Instant lock = locksAt(day);
		for (Warning warning : settings.beforeLock()) {
			Instant from = lock.minus(warning.before());
			if (!now.isBefore(from)) {
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

	/** The leaders of both sides' factions that have an installation they could commit. */
	static List<Recipient> recipients(War war) {
		List<Recipient> recipients = new ArrayList<>();
		List<Faction> factions = new ArrayList<>(BattleSideMembers.collectParticipatingFactions(war.getAttackers()));
		factions.addAll(BattleSideMembers.collectParticipatingFactions(war.getDefenders()));
		for (Faction faction : factions) {
			String leader = faction.getLeader();
			// A faction that has not loaded its installations has nothing to commit.
			if (leader == null || leader.isBlank() || faction.getInstallationHandler() == null
					|| BattleInstallationPickEligibility.listPickableInstallations(war, faction).isEmpty()) {
				continue;
			}
			recipients.add(new Recipient(faction, leader, chosen(war, faction)));
		}
		return recipients;
	}

	/** Installations the leader chose: not the defender's automatic port, not ones staff forced in. */
	static int chosen(War war, Faction faction) {
		Set<String> picks = BattleInstallationPickService.getPicks(war, faction.getId());
		int chosen = 0;
		for (String installationId : picks) {
			if (!BattleInstallationPickService.isDefenderZocPort(war, faction, installationId)
					&& !BattleInstallationPickService.isForced(war, faction.getId(), installationId)) {
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
		LockedBattleDay locked = war.getAvailability().battleDay(day);
		String text = "§6⚔ §e" + war.getName() + "§6: §f";
		if (due.timeLock() && locked != null && locked.hour() != null) {
			text += "the battle on " + label + " starts at "
					+ CampaignUiTimeFormatter.formatUtcHour(day, locked.hour())
					+ "§f. Choose the installations you commit; your choices lock in " + when + "§f.";
		} else {
			text += "your installation choices for the battle on " + label + " lock in " + when + "§f.";
		}
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
