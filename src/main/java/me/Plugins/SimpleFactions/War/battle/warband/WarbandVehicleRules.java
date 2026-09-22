package me.Plugins.SimpleFactions.War.battle.warband;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleJoinService;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleJoinService.CampaignBattleContext;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidWarbandService;
import me.Plugins.SimpleFactions.War.core.War;
import net.tfminecraft.VehicleFramework.VehicleFramework;

public final class WarbandVehicleRules {
	public static final String JOIN_BLOCKED_MOUNTED =
			"You cannot join a warband while mounted on a vehicle.";
	public static final String VEHICLE_BLOCKED_PRE_BATTLE =
			"You cannot use vehicles while signed up for a battle that has not started yet.";

	private WarbandVehicleRules() {
	}

	public static boolean isMountedOnVehicle(Player player) {
		if (player == null) {
			return false;
		}
		if (isVehicleFrameworkEnabled()) {
			try {
				if (VehicleFramework.getVehicleManager().getByPassenger(player) != null) {
					return true;
				}
			} catch (Throwable ignored) {
				// Fall through to Bukkit check.
			}
		}
		return player.isInsideVehicle();
	}

	public static String joinBlockedReason(Player player) {
		if (isMountedOnVehicle(player)) {
			return JOIN_BLOCKED_MOUNTED;
		}
		return null;
	}

	public static boolean blocksVehicleEntry(Player player) {
		if (player == null) {
			return false;
		}
		Warband warband = WarbandManager.getByMemberId(player.getUniqueId());
		return blocksVehicleEntryForWarband(warband);
	}

	public static boolean blocksVehicleEntryForWarband(Warband warband) {
		if (!isCampaignAutoWarband(warband)) {
			return false;
		}
		if (CampaignRaidWarbandService.isRaidWarband(warband)) {
			return blocksRaidWarbandVehicleEntry(warband);
		}
		CampaignBattleContext ctx = CampaignBattleJoinService.findCampaignBattleForWarband(warband);
		return ctx != null && !ctx.battle().hasStarted();
	}

	private static boolean blocksRaidWarbandVehicleEntry(Warband warband) {
		for (War war : me.Plugins.SimpleFactions.Managers.WarManager.getActive()) {
			CampaignRaid raid = CampaignRaidService.getActive(war);
			if (raid == null) {
				continue;
			}
			Warband attacker = CampaignRaidWarbandService.getAttackerWarband(raid);
			Warband defender = CampaignRaidWarbandService.getDefenderWarband(raid);
			if (!warband.equals(attacker) && !warband.equals(defender)) {
				continue;
			}
			if (raid.getState() == CampaignRaidState.MUSTER) {
				return true;
			}
			if (raid.getState() == CampaignRaidState.FIGHTING) {
				String battleId = raid.getBattleId();
				if (battleId == null || battleId.isBlank()) {
					return true;
				}
				Battle battle = BattleManager.getByString(battleId);
				return battle == null || !battle.hasStarted();
			}
		}
		return false;
	}

	public static boolean isCampaignAutoWarband(Warband warband) {
		if (warband == null) {
			return false;
		}
		if (CampaignRaidWarbandService.isRaidWarband(warband)) {
			return true;
		}
		return warband.isFaction() && warband.getCampaignSideId() != null;
	}

	private static boolean isVehicleFrameworkEnabled() {
		SimpleFactions plugin = SimpleFactions.getInstance();
		return plugin != null
				&& Bukkit.getPluginManager() != null
				&& Bukkit.getPluginManager().isPluginEnabled("VehicleFramework");
	}
}
