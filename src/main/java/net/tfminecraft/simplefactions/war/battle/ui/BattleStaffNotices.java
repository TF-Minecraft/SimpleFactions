package net.tfminecraft.simplefactions.war.battle.ui;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.events.BattleStartedEvent;

/** Tells online staff when battles start and end, and what other staff did to them. */
public final class BattleStaffNotices implements Listener {

	/** Sends a line to every online staff member except {@code exceptName} (the one who acted). */
	public static void toStaff(String message, String exceptName) {
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (isStaff(player) && !player.getName().equalsIgnoreCase(exceptName)) {
				player.sendMessage(message);
			}
		}
	}

	static boolean isStaff(Player player) {
		return Permissions.isAdmin(player) || BattlePermissions.isAdmin(player);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onStarted(BattleStartedEvent event) {
		String id = event.getBattleId();
		toStaff("§6[Battle] §e" + name(id) + " §6started §7(" + type(event) + ")§6. §7Watch: §e/battle watch "
				+ id + "§7, details: §e/battle info " + id, null);
	}

	// Lowest so the campaign result, which deletes the battle, has not run yet and the name is known.
	@EventHandler(priority = EventPriority.LOWEST)
	public void onEnded(BattleEndedEvent event) {
		String id = event.getBattleId();
		BattleWatchService.clearBattle(id);
		String result = event.hasWinner() ? "§e" + event.getWinningSideId() + " §6won" : "§6ended with no winner";
		toStaff("§6[Battle] §e" + name(id) + " §6is over: " + result + casualties(event.getSideCasualties()) + "§6.",
				null);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		if (BattleWatchService.watching(event.getPlayer().getUniqueId()) != null) {
			BattleWatchService.stop(event.getPlayer());
		}
	}

	private static String name(String battleId) {
		Battle battle = BattleManager.getByString(battleId);
		return battle == null ? battleId : battle.getDisplayName();
	}

	private static String type(BattleStartedEvent event) {
		return event.getBattleType() == null ? "battle" : event.getBattleType().name().toLowerCase(Locale.ROOT);
	}

	private static String casualties(Map<String, Integer> sideCasualties) {
		if (sideCasualties == null || sideCasualties.isEmpty()) {
			return "";
		}
		java.util.List<String> parts = new java.util.ArrayList<>();
		sideCasualties.forEach((side, count) -> parts.add(side + " " + count));
		return "§6, casualties §7" + String.join(", ", parts);
	}
}
