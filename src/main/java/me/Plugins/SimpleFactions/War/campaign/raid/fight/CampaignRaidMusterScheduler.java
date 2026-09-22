package me.Plugins.SimpleFactions.War.campaign.raid.fight;


import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.core.War;

public final class CampaignRaidMusterScheduler {
	private static final Map<Integer, List<Integer>> scheduledWarTasks = new ConcurrentHashMap<>();

	private CampaignRaidMusterScheduler() {}

	public static void resetForTests() {
		scheduledWarTasks.clear();
		CampaignRaidMusterReminderService.resetForTests();
	}

	public static void cancelAllScheduled() {
		for (Integer warId : scheduledWarTasks.keySet().toArray(Integer[]::new)) {
			cancelScheduled(warId);
		}
		CampaignRaidMusterReminderService.cancelAllScheduled();
	}

	public static void cancelForWar(int warId) {
		cancelScheduled(warId);
		CampaignRaidMusterReminderService.cancelForWar(warId);
	}

	public static void onMusterStarted(War war, Instant now) {
		if (war == null || now == null) {
			return;
		}
		CampaignRaid raid = war.getActiveCampaignRaid();
		if (raid == null || raid.getMusterEndsAt() == null) {
			return;
		}
		cancelScheduled(war.getId());
		CampaignRaidMusterReminderService.schedule(war, now);
		long delayTicks = delayTicksUntil(raid.getMusterEndsAt(), now);
		if (delayTicks <= 0L) {
			onMusterEnd(war, now);
			return;
		}
		if (!canScheduleTasks() || CampaignClock.isSpoofed()) {
			return;
		}
		int warId = war.getId();
		int taskId = new BukkitRunnable() {
			@Override
			public void run() {
				removeTask(warId, getTaskId());
				War current = WarManager.getById(warId);
				if (current != null) {
					onMusterEnd(current, CampaignClock.now());
				}
			}
		}.runTaskLater(SimpleFactions.plugin, delayTicks).getTaskId();
		trackTask(warId, taskId);
	}

	public static boolean processOverdue(War war, Instant now) {
		if (war == null || now == null) {
			return false;
		}
		CampaignRaidMusterReminderService.processReminders(war, now);
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null || raid.getState() != CampaignRaidState.MUSTER) {
			return false;
		}
		if (raid.getMusterEndsAt() == null || now.isBefore(raid.getMusterEndsAt())) {
			return false;
		}
		onMusterEnd(war, now);
		return true;
	}

	static void onMusterEnd(War war, Instant now) {
		if (war == null || now == null) {
			return;
		}
		cancelScheduled(war.getId());
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null || raid.getState() != CampaignRaidState.MUSTER) {
			return;
		}
		CampaignRaidLaunchService.startFight(war, now);
	}

	private static long delayTicksUntil(Instant musterEndsAt, Instant now) {
		long seconds = musterEndsAt.getEpochSecond() - now.getEpochSecond();
		if (seconds <= 0L) {
			return 0L;
		}
		return seconds * 20L;
	}

	private static void trackTask(int warId, int taskId) {
		scheduledWarTasks.computeIfAbsent(warId, ignored -> new ArrayList<>()).add(taskId);
	}

	private static void removeTask(int warId, int taskId) {
		List<Integer> tasks = scheduledWarTasks.get(warId);
		if (tasks != null) {
			tasks.remove(Integer.valueOf(taskId));
			if (tasks.isEmpty()) {
				scheduledWarTasks.remove(warId);
			}
		}
	}

	private static void cancelScheduled(int warId) {
		List<Integer> taskIds = scheduledWarTasks.remove(warId);
		if (taskIds == null || !canScheduleTasks()) {
			return;
		}
		for (int taskId : taskIds) {
			SimpleFactions.plugin.getServer().getScheduler().cancelTask(taskId);
		}
	}

	private static boolean canScheduleTasks() {
		return SimpleFactions.plugin != null && SimpleFactions.plugin.getServer() != null;
	}
}
