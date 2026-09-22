package me.Plugins.SimpleFactions.War.battle.campaign;

import java.time.Instant;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.TLibs.Utils.TimeFormatter;

public final class CampaignBattleSignupReminderService {
	private static final int REMINDER_WINDOW_SECONDS = 60;

	private CampaignBattleSignupReminderService() {
	}

	public static void processReminders(War war, Instant now) {
		if (war == null || !war.isActive() || now == null) {
			return;
		}
		if (war.getBattleSchedulePhase() != BattleSchedulePhase.SCHEDULED) {
			return;
		}
		Instant scheduledAt = war.getScheduledBattleAt();
		if (scheduledAt == null) {
			return;
		}
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle == null || battle.hasStarted()) {
			return;
		}
		List<Integer> offsets = Cache.battleSignupReminderSecondsBefore;
		if (offsets == null || offsets.isEmpty()) {
			return;
		}

		int offset = findNextDueReminderOffset(war, scheduledAt, now);
		if (offset < 0) {
			return;
		}
		broadcastReminder(war, battle, offset);
		war.getSignupRemindersSent().add(offset);
		WarManager.persist(war);
	}

	static int findNextDueReminderOffset(War war, Instant scheduledAt, Instant now) {
		if (war == null || scheduledAt == null || now == null) {
			return -1;
		}
		List<Integer> offsets = Cache.battleSignupReminderSecondsBefore;
		if (offsets == null || offsets.isEmpty()) {
			return -1;
		}
		int nextOffset = -1;
		for (int offset : offsets) {
			if (war.getSignupRemindersSent().contains(offset)) {
				continue;
			}
			Instant reminderAt = scheduledAt.minusSeconds(offset);
			if (now.isBefore(reminderAt) || !now.isBefore(reminderAt.plusSeconds(REMINDER_WINDOW_SECONDS))) {
				continue;
			}
			if (nextOffset < 0 || offset < nextOffset) {
				nextOffset = offset;
			}
		}
		return nextOffset;
	}

	private static void broadcastReminder(War war, Battle battle, int offsetSeconds) {
		String timeLabel = TimeFormatter.formatTime(offsetSeconds);
		String message = "§e" + battle.getDisplayName()
				+ " starts in " + timeLabel
				+ ". Join your faction warband: §a/warband list";
		broadcastToUnassignedBelligerents(war, message);
	}

	private static void broadcastToUnassignedBelligerents(War war, String message) {
		broadcastToUnassignedSide(war.getAttackers(), message);
		broadcastToUnassignedSide(war.getDefenders(), message);
	}

	private static void broadcastToUnassignedSide(
			me.Plugins.SimpleFactions.War.core.Side side,
			String message) {
		if (side == null || message == null) {
			return;
		}
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(side)) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player == null || !player.isOnline()) {
				continue;
			}
			if (WarbandManager.getByMemberId(player.getUniqueId()) != null) {
				continue;
			}
			player.sendMessage(message);
		}
	}
}
