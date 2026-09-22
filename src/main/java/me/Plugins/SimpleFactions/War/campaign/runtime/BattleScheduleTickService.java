package me.Plugins.SimpleFactions.War.campaign.runtime;

import java.time.Instant;
import java.time.LocalDate;

import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleLaunchService;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleRosterService;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleSignupReminderService;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignNavalAutoLossReminderService;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidFightScheduler;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidMusterScheduler;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidResumeService;
import me.Plugins.SimpleFactions.War.campaign.vote.VoteResults.BattleScheduleCloseResult;
import me.Plugins.SimpleFactions.War.campaign.vote.VoteResults.CloseVoteOptions;

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

	public static int tick(Instant now) {
		for (War war : WarManager.getActive()) {
			CampaignBattleSignupReminderService.processReminders(war, now);
			CampaignNavalAutoLossReminderService.processReminders(war, now);
			CampaignBattleRosterService.tryEnrollWhenSignupOpens(war, now);
			CampaignBattleLaunchService.tryStartScheduledBattle(war, now);
			CampaignRaidMusterScheduler.processOverdue(war, now);
			CampaignRaidFightScheduler.processOverdue(war, now);
		}

		if (!shouldRunForHour(now)) {
			return 0;
		}

		int persisted = 0;
		for (War war : WarManager.getActive()) {
			if (processWar(war, now)) {
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

	static boolean processWar(War war, Instant now) {
		if (war == null || !war.isActive()) {
			return false;
		}
		if (war.getBattleSchedulePhase() != BattleSchedulePhase.VOTING) {
			return false;
		}

		boolean changed = false;
		if (BattleScheduleService.applyPostBattleChoiceDeadline(war, now)) {
			changed = true;
		}

		BattleScheduleCloseResult result = BattleScheduleService.closeVote(
				war,
				now,
				BattleScheduleLookups.uuidToFactionForWar(war),
				BattleScheduleLookups.memberNameToUuid(),
				CloseVoteOptions.scheduled());
		if (result == BattleScheduleCloseResult.SCHEDULED
				|| result == BattleScheduleCloseResult.POSTPONED
				|| result == BattleScheduleCloseResult.AUTORESOLVE_PENDING) {
			changed = true;
		}
		return changed;
	}
}
