package me.Plugins.SimpleFactions.War.campaign.raid.fight;


import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.TLibs.Utils.TimeFormatter;

public final class CampaignRaidMusterReminderService {
	private static final int REMINDER_WINDOW_SECONDS = 60;
	private static final Map<Integer, List<Integer>> scheduledReminderTasks = new ConcurrentHashMap<>();

	private CampaignRaidMusterReminderService() {
	}

	public static void resetForTests() {
		cancelAllScheduled();
	}

	static void cancelAllScheduled() {
		for (Integer warId : scheduledReminderTasks.keySet().toArray(Integer[]::new)) {
			cancelScheduled(warId);
		}
	}

	static void cancelForWar(int warId) {
		cancelScheduled(warId);
	}

	public static void processReminders(War war, Instant now) {
		if (war == null || !war.isActive() || now == null) {
			return;
		}
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null || raid.getState() != CampaignRaidState.MUSTER) {
			return;
		}
		if (raid.getMusterEndsAt() == null) {
			return;
		}
		tryFireDueReminders(war, now);
	}

	static void schedule(War war, Instant now) {
		if (war == null || now == null) {
			return;
		}
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null || raid.getState() != CampaignRaidState.MUSTER || raid.getMusterEndsAt() == null) {
			return;
		}
		cancelScheduled(war.getId());
		tryFireDueReminders(war, now);
		if (CampaignClock.isSpoofed() || !canScheduleTasks()) {
			return;
		}
		scheduleFutureReminders(war, now);
	}

	private static void tryFireDueReminders(War war, Instant now) {
		int offset = findNextDueReminderOffset(war, now);
		if (offset < 0) {
			return;
		}
		if (tryFireReminder(war, offset, now)) {
			WarManager.persist(war);
		}
	}

	static int findNextDueReminderOffset(War war, Instant now) {
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null || raid.getState() != CampaignRaidState.MUSTER || now == null) {
			return -1;
		}
		Instant musterEndsAt = raid.getMusterEndsAt();
		if (musterEndsAt == null || !now.isBefore(musterEndsAt)) {
			return -1;
		}
		List<Integer> offsets = Cache.campaignRaidMusterReminderSecondsBefore;
		if (offsets == null || offsets.isEmpty()) {
			return -1;
		}
		int nextOffset = -1;
		for (int offset : offsets) {
			if (raid.getMusterRemindersSent().contains(offset)) {
				continue;
			}
			Instant reminderAt = musterEndsAt.minusSeconds(offset);
			if (now.isBefore(reminderAt)) {
				continue;
			}
			if (!now.isBefore(reminderAt.plusSeconds(REMINDER_WINDOW_SECONDS))) {
				continue;
			}
			if (nextOffset < 0 || offset < nextOffset) {
				nextOffset = offset;
			}
		}
		return nextOffset;
	}

	static boolean tryFireReminder(War war, int offsetSeconds, Instant now) {
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null || raid.getState() != CampaignRaidState.MUSTER || now == null) {
			return false;
		}
		Instant musterEndsAt = raid.getMusterEndsAt();
		if (musterEndsAt == null || !now.isBefore(musterEndsAt)) {
			return false;
		}
		if (raid.getMusterRemindersSent().contains(offsetSeconds)) {
			return false;
		}
		Instant reminderAt = musterEndsAt.minusSeconds(offsetSeconds);
		if (now.isBefore(reminderAt) || !now.isBefore(reminderAt.plusSeconds(REMINDER_WINDOW_SECONDS))) {
			return false;
		}
		broadcastReminder(war, raid, offsetSeconds);
		raid.getMusterRemindersSent().add(offsetSeconds);
		return true;
	}

	private static void scheduleFutureReminders(War war, Instant now) {
		CampaignRaid raid = CampaignRaidService.getActive(war);
		if (raid == null || raid.getMusterEndsAt() == null) {
			return;
		}
		List<Integer> offsets = Cache.campaignRaidMusterReminderSecondsBefore;
		if (offsets == null || offsets.isEmpty()) {
			return;
		}
		int warId = war.getId();
		for (int offset : offsets) {
			if (raid.getMusterRemindersSent().contains(offset)) {
				continue;
			}
			Instant reminderAt = raid.getMusterEndsAt().minusSeconds(offset);
			long delayTicks = delayTicksUntil(reminderAt, now);
			if (delayTicks <= 0L) {
				continue;
			}
			int capturedOffset = offset;
			int taskId = new BukkitRunnable() {
				@Override
				public void run() {
					removeTask(warId, getTaskId());
					War current = WarManager.getById(warId);
					if (current != null) {
						if (tryFireReminder(current, capturedOffset, CampaignClock.now())) {
							WarManager.persist(current);
						}
					}
				}
			}.runTaskLater(SimpleFactions.plugin, delayTicks).getTaskId();
			trackTask(warId, taskId);
		}
	}

	private static void broadcastReminder(War war, CampaignRaid raid, int offsetSeconds) {
		String raidName = raid.getDisplayName() != null ? raid.getDisplayName() : raid.getId();
		String timeLabel = TimeFormatter.formatTime(offsetSeconds);
		String message = "§e" + raidName
				+ " §estarts in " + timeLabel
				+ ". Attackers: §a/raid join " + raid.getId();
		broadcastToAttackerCoalition(war, raid, message);
	}

	private static void broadcastToAttackerCoalition(War war, CampaignRaid raid, String message) {
		if (war == null || raid == null || raid.getAttackerCoalition() == null || message == null) {
			return;
		}
		Side side = raid.getAttackerCoalition() == CampaignCoalition.AGGRESSOR
				? war.getAttackers()
				: war.getDefenders();
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(side)) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendMessage(message);
			}
		}
	}

	private static long delayTicksUntil(Instant target, Instant now) {
		long seconds = target.getEpochSecond() - now.getEpochSecond();
		if (seconds <= 0L) {
			return 0L;
		}
		return seconds * 20L;
	}

	private static void trackTask(int warId, int taskId) {
		scheduledReminderTasks.computeIfAbsent(warId, ignored -> new ArrayList<>()).add(taskId);
	}

	private static void removeTask(int warId, int taskId) {
		List<Integer> tasks = scheduledReminderTasks.get(warId);
		if (tasks != null) {
			tasks.remove(Integer.valueOf(taskId));
			if (tasks.isEmpty()) {
				scheduledReminderTasks.remove(warId);
			}
		}
	}

	private static void cancelScheduled(int warId) {
		List<Integer> taskIds = scheduledReminderTasks.remove(warId);
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
