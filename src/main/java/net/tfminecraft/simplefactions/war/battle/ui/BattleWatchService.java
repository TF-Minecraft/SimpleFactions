package net.tfminecraft.simplefactions.war.battle.ui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;

/**
 * Staff who watch a battle see every side's lives bar without fighting in it. The bars keep
 * them until the battle ends, which removes everyone from its bars.
 */
public final class BattleWatchService {
	private static final Map<UUID, String> WATCHING = new ConcurrentHashMap<>();

	private BattleWatchService() {}

	/** Starts watching {@code battle}, or stops when the player already watches it. */
	public static String toggle(Player player, Battle battle) {
		String current = WATCHING.get(player.getUniqueId());
		if (battle.getId().equalsIgnoreCase(current)) {
			return stop(player);
		}
		if (!battle.hasStarted()) {
			return "§c" + battle.getDisplayName() + " §cis not running.";
		}
		if (battle.getBattleType() == BattleType.RAID) {
			return "§cRaids show their own bars to the raiders. Use §e/battle info " + battle.getId() + "§c.";
		}
		stop(player);
		for (BattleSide side : battle.getSides()) {
			side.addBossBarPlayer(player);
		}
		WATCHING.put(player.getUniqueId(), battle.getId());
		return "§aWatching " + battle.getDisplayName() + "§a: you now see every side's lives."
				+ " §7Run the same command again to stop.";
	}

	/** Stops watching; quiet when the player watched nothing. */
	public static String stop(Player player) {
		String battleId = WATCHING.remove(player.getUniqueId());
		if (battleId == null) {
			return "§7You are not watching a battle.";
		}
		Battle battle = BattleManager.getByString(battleId);
		if (battle != null && battle.getSideByPlayer(player) == null) {
			for (BattleSide side : battle.getSides()) {
				side.removeBossBarPlayer(player);
			}
		}
		return "§7Stopped watching " + (battle == null ? battleId : battle.getDisplayName()) + ".";
	}

	public static String watching(UUID playerId) {
		return WATCHING.get(playerId);
	}

	/** Forgets everyone watching a battle that ended; its bars already dropped them. */
	public static void clearBattle(String battleId) {
		WATCHING.values().removeIf(id -> id.equalsIgnoreCase(battleId));
	}

	public static void resetForTests() {
		WATCHING.clear();
	}
}
