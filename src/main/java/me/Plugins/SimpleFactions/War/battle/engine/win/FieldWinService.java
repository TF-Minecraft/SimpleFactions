package me.Plugins.SimpleFactions.War.battle.engine.win;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleEndSupport;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;

public final class FieldWinService {
	private static final double JAIL_RADIUS_BLOCKS = 5.0;
	private static final double JAIL_RADIUS_SQ = JAIL_RADIUS_BLOCKS * JAIL_RADIUS_BLOCKS;

	private FieldWinService() {
	}

	public static void checkFieldWin(Battle battle) {
		if (battle == null || !battle.hasStarted() || battle.getBattleType() != BattleType.FIELD) {
			return;
		}
		List<BattleSide> eliminated = new ArrayList<>();
		for (BattleSide side : battle.getSides()) {
			if (isSideEliminated(side)) {
				eliminated.add(side);
			}
		}
		if (eliminated.isEmpty()) {
			return;
		}
		String winnerId = null;
		if (eliminated.size() == 1 && battle.getSides().size() == 2) {
			BattleSide loser = eliminated.get(0);
			for (BattleSide side : battle.getSides()) {
				if (!side.getId().equalsIgnoreCase(loser.getId())) {
					winnerId = side.getId();
					break;
				}
			}
		}
		endBattle(battle, winnerId);
	}

	static void endBattle(Battle battle, String winningSideId) {
		BattleEndSupport.endBattle(battle, winningSideId);
	}

	public static boolean isSideEliminated(BattleSide side) {
		if (side == null || side.getLives() > 0) {
			return false;
		}
		List<Player> online = getOnlineParticipants(side);
		if (online.isEmpty()) {
			return true;
		}
		for (Player player : online) {
			if (!isAtJail(player, side)) {
				return false;
			}
		}
		return true;
	}

	public static boolean isAtJail(Player player, BattleSide side) {
		if (player == null || side == null) {
			return false;
		}
		return isNearJail(player.getLocation(), player.getWorld(), side);
	}

	static boolean isNearJail(Location playerLocation, org.bukkit.World world, BattleSide side) {
		if (playerLocation == null || side == null) {
			return false;
		}
		Location jail = side.getJail();
		if (jail == null || jail.getWorld() == null) {
			return false;
		}
		if (world == null || !world.equals(jail.getWorld())) {
			return false;
		}
		return playerLocation.distanceSquared(jail) <= JAIL_RADIUS_SQ;
	}

	private static List<Player> getOnlineParticipants(BattleSide side) {
		List<Player> online = new ArrayList<>();
		for (me.Plugins.SimpleFactions.War.battle.warband.Warband warband : side.getBands()) {
			for (Player player : warband.getPlayers()) {
				if (player != null && player.isOnline()) {
					online.add(player);
				}
			}
		}
		return online;
	}
}
