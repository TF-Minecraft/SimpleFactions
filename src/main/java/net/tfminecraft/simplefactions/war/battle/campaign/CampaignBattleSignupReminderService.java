package net.tfminecraft.simplefactions.war.battle.campaign;

import java.time.Instant;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.tlibs.utils.TimeFormatter;

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
			net.tfminecraft.simplefactions.war.core.Side side,
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
