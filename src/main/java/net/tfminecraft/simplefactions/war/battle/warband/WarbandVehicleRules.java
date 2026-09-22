package net.tfminecraft.simplefactions.war.battle.warband;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService.CampaignBattleContext;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidState;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.vehicleframework.VehicleFramework;

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
		for (War war : net.tfminecraft.simplefactions.managers.WarManager.getActive()) {
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
