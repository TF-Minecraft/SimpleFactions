package net.tfminecraft.simplefactions.war.campaign.runtime;

import java.time.Instant;
import java.time.LocalDate;

import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityScheduler;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleLaunchService;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleRosterService;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleSignupReminderService;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignNavalAutoLossReminderService;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidFightScheduler;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidMusterScheduler;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidResumeService;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleVoteReminders;
import net.tfminecraft.simplefactions.war.campaign.vote.InstallationPickReminders;

public final class BattleScheduleTickService {
	private static int lastProcessedUtcHour = -1;
	private static LocalDate lastProcessedUtcDate;

	private BattleScheduleTickService() {}

	public static void start() {
		new BukkitRunnable() {
			@Override
			public void run() {
				tick(CampaignClock.now());
			}
		}.runTaskTimer(SimpleFactions.plugin, 0L, 1200L);
	}

	public static void onClockOffsetChanged() {
		resetHourGateForTests();
		CampaignRaidMusterScheduler.cancelAllScheduled();
		CampaignRaidFightScheduler.cancelAllScheduled();
		CampaignRaidResumeService.resumeAll();
	}

	static void resetHourGateForTests() {
		lastProcessedUtcHour = -1;
		lastProcessedUtcDate = null;
	}

	/** Returns how many wars were saved because something fell due. */
	public static int tick(Instant now) {
		int persisted = 0;
		for (War war : WarManager.getActive()) {
			// Locks and reminders first, so a battle set this minute can be prepared and started below.
			boolean changed = AvailabilityScheduler.process(war, now);
			changed |= BattleVoteReminders.process(war, now);
			changed |= InstallationPickReminders.process(war, now);
			if (changed && war.isActive()) {
				WarManager.persist(war);
				persisted++;
			}
			if (!war.isActive()) {
				continue;
			}
			CampaignBattleSignupReminderService.processReminders(war, now);
			CampaignNavalAutoLossReminderService.processReminders(war, now);
			CampaignBattleRosterService.tryEnrollWhenSignupOpens(war, now);
			CampaignBattleLaunchService.tryStartScheduledBattle(war, now);
			CampaignRaidMusterScheduler.processOverdue(war, now);
			CampaignRaidFightScheduler.processOverdue(war, now);
		}

		if (!shouldRunForHour(now)) {
			return persisted;
		}

		for (War war : WarManager.getActive()) {
			// A war that ended here already deleted its file; saving it would bring the file back.
			if (processWar(war, now) && war.isActive()) {
				WarManager.persist(war);
				persisted++;
			}
		}
		return persisted;
	}

	static boolean shouldRunForHour(Instant now) {
		if (now == null) {
			return false;
		}
		int hour = BattleScheduleService.battleDayHour(now);
		LocalDate date = BattleScheduleService.battleDayDate(now);
		if (hour == lastProcessedUtcHour && date != null && date.equals(lastProcessedUtcDate)) {
			return false;
		}
		lastProcessedUtcHour = hour;
		lastProcessedUtcDate = date;
		return true;
	}

	/** Hourly: an autoresolve that staff or a battle left pending. Locks and scheduling run every minute. */
	static boolean processWar(War war, Instant now) {
		if (war == null || !war.isActive()) {
			return false;
		}
		return war.getBattleSchedulePhase() == BattleSchedulePhase.AUTORESOLVE_PENDING
				&& BattleAutoresolveService.resolve(war);
	}
}
