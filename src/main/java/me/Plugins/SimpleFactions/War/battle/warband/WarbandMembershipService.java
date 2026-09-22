package me.Plugins.SimpleFactions.War.battle.warband;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleJoinService;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleJoinService.CampaignBattleContext;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;

public class WarbandMembershipService {
	private static WarbandMembershipService instance;

	private final Map<UUID, WarbandRejoinState> pendingRejoin = new HashMap<>();

	public static WarbandMembershipService getInstance() {
		if (instance == null) {
			instance = new WarbandMembershipService();
		}
		return instance;
	}

	public static void resetForTests() {
		instance = new WarbandMembershipService();
	}

	public void handleQuit(UUID playerId) {
		Warband warband = WarbandManager.getByMemberId(playerId);
		if (warband == null) {
			return;
		}
		Faction faction = resolveFactionForWarband(warband, playerId);
		detachFromBattle(playerId, warband);
		pendingRejoin.put(playerId, new WarbandRejoinState(warband.getId(), faction));
		warband.removeMember(playerId);
	}

	public boolean handleJoin(Player player) {
		return attemptRejoin(player.getUniqueId(), player.getName(), player);
	}

	boolean attemptRejoin(UUID playerId, String playerName, Player onlinePlayer) {
		WarbandRejoinState state = pendingRejoin.remove(playerId);
		if (state == null) {
			return false;
		}
		Warband warband = WarbandManager.getByString(state.getWarbandId());
		if (warband == null) {
			return false;
		}
		Faction playerFaction = warband.isFaction() ? FactionManager.getByMember(playerName) : null;
		if (!evaluateRejoin(warband, playerId, playerFaction, state)) {
			return false;
		}
		warband.addMember(playerId);
		if (onlinePlayer != null) {
			restoreBattleState(onlinePlayer);
		}
		return true;
	}

	public WarbandRejoinState getPendingRejoin(UUID playerId) {
		return pendingRejoin.get(playerId);
	}

	public void clearPendingRejoin(UUID playerId) {
		pendingRejoin.remove(playerId);
	}

	public boolean evaluateRejoin(Warband warband, UUID playerId, Faction playerFaction, WarbandRejoinState state) {
		if (warband.isFaction()) {
			if (!state.hasFaction()) {
				return false;
			}
			Faction faction = FactionManager.getByString(state.getFactionId());
			if (faction == null) {
				return false;
			}
			if (playerFaction == null || !playerFaction.equals(faction)) {
				return false;
			}
			CampaignBattleContext ctx = CampaignBattleJoinService.findCampaignBattleForWarband(warband);
			if (ctx != null && ctx.battle().hasStarted()) {
				return CampaignBattleJoinService.validateWarbandMemberJoin(
						ctx.war(), ctx.battle(), ctx.sideId(), warband, playerNameFromId(playerId), playerId) == null;
			}
			if (ctx != null) {
				return CampaignBattleJoinService.validateRosterHasRoom(
						ctx.war(), ctx.battle(), ctx.sideId(), warband, 1) == null;
			}
			return true;
		}
		if (warband.isLocked()) {
			Player player = getOnlinePlayer(playerId);
			return player != null && warband.isInvited(player);
		}
		return true;
	}

	private String playerNameFromId(UUID playerId) {
		Player player = getOnlinePlayer(playerId);
		if (player != null && player.getName() != null) {
			return player.getName();
		}
		if (Bukkit.getOfflinePlayer(playerId).getName() != null) {
			return Bukkit.getOfflinePlayer(playerId).getName();
		}
		return null;
	}

	private Player getOnlinePlayer(UUID playerId) {
		if (Bukkit.getServer() == null) {
			return null;
		}
		return Bukkit.getPlayer(playerId);
	}

	private Faction resolveFactionForWarband(Warband warband, UUID playerId) {
		if (!warband.isFaction()) {
			return null;
		}
		Player player = getOnlinePlayer(playerId);
		if (player == null) {
			return null;
		}
		return FactionManager.getByMember(player.getName());
	}

	private void detachFromBattle(UUID playerId, Warband warband) {
		Player player = getOnlinePlayer(playerId);
		if (player == null) {
			return;
		}
		BattleManager.currentBattle.remove(player);
		BattleSide side = findSideForWarband(warband);
		if (side != null) {
			side.removeBossBarPlayer(player);
		}
	}

	private BattleSide findSideForWarband(Warband warband) {
		for (Battle battle : BattleManager.get()) {
			for (BattleSide side : battle.getSides()) {
				if (side.getBands().contains(warband)) {
					return side;
				}
			}
		}
		return null;
	}

	private void restoreBattleState(Player player) {
		Battle battle = BattleManager.getBattleByMemberId(player.getUniqueId());
		if (battle == null || !battle.hasStarted()) {
			return;
		}
		if (battle.isCampaignRaid()) {
			return;
		}
		BattleSide side = battle.getSideByMemberId(player.getUniqueId());
		if (side == null) {
			return;
		}
		side.addBossBarPlayer(player);
	}
}
