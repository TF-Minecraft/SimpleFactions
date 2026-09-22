package me.Plugins.SimpleFactions.War.battle.campaign.warband;


import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleJoinService;
import java.time.Instant;
import java.util.UUID;

import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleJoinService.CampaignBattleContext;
import me.Plugins.SimpleFactions.War.core.WarDevMode;
import me.Plugins.SimpleFactions.War.battle.persistence.BattlePersistenceService;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandVehicleRules;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidWarbandService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.core.War;

public final class CampaignWarbandSignupService {
	public static final String SIGNUP_BLOCKED_DURING_RAID =
			"§cCampaign warband signup opens after the raid window (20:00).";

	private CampaignWarbandSignupService() {
	}

	public static boolean isSignupOpen(War war, Instant now) {
		if (war == null || now == null) {
			return false;
		}
		if (!BattleScheduleService.isOnBattleDay(war, now)) {
			return false;
		}
		int hour = BattleWindowService.scheduleHour(now);
		if (hour >= Cache.warRaidWindowStartHour && hour < Cache.warRaidWindowEndHour) {
			return false;
		}
		return hour >= Cache.warRaidWindowEndHour;
	}

	public static String signup(Player player, Warband warband, Faction playerFaction) {
		if (player == null || warband == null) {
			return "Invalid warband signup";
		}
		return signupMember(player.getUniqueId(), player.getName(), warband, playerFaction, player);
	}

	public static String signupMember(UUID playerId, String playerName, Warband warband, Faction playerFaction) {
		return signupMember(playerId, playerName, warband, playerFaction, null);
	}

	public static String signupMember(
			UUID playerId,
			String playerName,
			Warband warband,
			Faction playerFaction,
			Player onlinePlayer) {
		return signupMember(playerId, playerName, warband, playerFaction, onlinePlayer, CampaignClock.now());
	}

	public static String signupMember(
			UUID playerId,
			String playerName,
			Warband warband,
			Faction playerFaction,
			Player onlinePlayer,
			Instant now) {
		if (playerId == null || playerName == null || warband == null) {
			return "Invalid warband signup";
		}
		if (onlinePlayer != null) {
			String vehicleError = WarbandVehicleRules.joinBlockedReason(onlinePlayer);
			if (vehicleError != null) {
				return vehicleError;
			}
		}
		if (warband.hasMember(playerId)) {
			return null;
		}

		CampaignBattleContext ctx = CampaignBattleJoinService.findCampaignBattleForWarband(warband);
		if (!CampaignRaidWarbandService.isRaidWarband(warband)
				&& ctx != null
				&& !WarDevMode.isEnabled()
				&& !isSignupOpen(ctx.war(), now)) {
			return SIGNUP_BLOCKED_DURING_RAID;
		}
		if (ctx != null) {
			String joinError = CampaignBattleJoinService.validateWarbandMemberJoin(
					ctx.war(), ctx.battle(), ctx.sideId(), warband, playerName, playerId);
			if (joinError != null) {
				return joinError;
			}
		}

		boolean firstSignup = warband.getRealMemberCount() == 0;
		applyLeaderRules(ctx, warband, playerId, playerName);
		warband.addMember(playerId);

		if (ctx != null && firstSignup) {
			WarDevMode.seedDummyMembersOnFirstSignupIfEnabled(
					warband, ctx.war(), ctx.battle(), ctx.sideId());
		}
		if (ctx != null && ctx.battle().hasStarted() && onlinePlayer != null) {
			CampaignWarbandBattleService.onMemberJoined(onlinePlayer, warband, ctx);
		}
		BattlePersistenceService.persistWarband(warband);
		return null;
	}

	private static void applyLeaderRules(
			CampaignBattleContext ctx,
			Warband warband,
			UUID playerId,
			String playerName) {
		if (warband.isPendingLeader()) {
			warband.setLeaderId(playerId);
			return;
		}
		if (ctx != null && CampaignWarbandBattleService.isWarSideMainLeader(ctx.war(), warband, playerName)) {
			warband.setLeaderId(playerId);
		}
	}
}
