package me.Plugins.SimpleFactions.War.declare;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;

/**
 * Collects a war declare code from chat, on the {@code RelocationPrompt} pattern:
 * a pending map, a cancelled chat message, and a timeout that gives up quietly.
 * There is no anvil or sign input anywhere in the plugin, so chat is the only way
 * to take free text from a player mid-GUI.
 */
public class DeclareCodePrompt implements Listener {

	private static final long PROMPT_TIMEOUT_TICKS = 20L * 60;

	private static final Map<Player, Pending> pending = new HashMap<>();

	private static class Pending {
		private final String attackerId;
		private final String defenderId;

		private Pending(String attackerId, String defenderId) {
			this.attackerId = attackerId;
			this.defenderId = defenderId;
		}
	}

	/** Asks the leader for the code that staff handed them. */
	public static void begin(Player player, Faction attacker, Faction defender) {
		if (player == null || attacker == null || defender == null) {
			return;
		}
		player.closeInventory();
		pending.put(player, new Pending(attacker.getId(), defender.getId()));
		player.sendMessage("§eEnter your war declaration code in chat:");
		player.sendMessage("§7Staff mint one in Discord. Type §fcancel §7to back out.");
		new BukkitRunnable() {
			@Override
			public void run() {
				if (pending.remove(player) != null && player.isOnline()) {
					player.sendMessage("§cWar declaration timed out");
				}
			}
		}.runTaskLater(SimpleFactions.getInstance(), PROMPT_TIMEOUT_TICKS);
	}

	@EventHandler
	public void onPlayerChat(AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();
		Pending state = pending.remove(player);
		if (state == null) {
			return;
		}

		event.setCancelled(true);
		String code = event.getMessage().trim();
		if (code.isBlank()) {
			player.sendMessage("§cA war code is required to declare here");
			return;
		}
		if (code.equalsIgnoreCase("cancel")) {
			player.sendMessage("§7War declaration cancelled");
			return;
		}

		player.sendMessage("§7Checking your code...");
		check(player, state, code);
	}

	/**
	 * The gateway call is blocking HTTP, so it runs async and hops back before it
	 * touches a faction or a GUI, following {@code MapSystem.updateMap}.
	 */
	private void check(Player player, Pending state, String code) {
		SimpleFactions plugin = SimpleFactions.getInstance();
		// A hung backend must not leave the leader staring at nothing, so the giving-up
		// message is scheduled up front and the late reply is dropped if it fires.
		boolean[] answered = new boolean[1];
		new BukkitRunnable() {
			@Override
			public void run() {
				if (answered[0]) {
					return;
				}
				answered[0] = true;
				if (player.isOnline()) {
					player.sendMessage("§cThe war code service did not answer in time.");
				}
			}
		}.runTaskLater(plugin, 20L * Math.max(1, Cache.warDeclareCodeTimeoutSeconds));

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			WarDeclareCodeService.Result result = WarDeclareCodeService.validate(
					code, state.attackerId, state.defenderId);
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (answered[0]) {
					return;
				}
				answered[0] = true;
				apply(player, state, code, result);
			});
		});
	}

	private void apply(
			Player player,
			Pending state,
			String code,
			WarDeclareCodeService.Result result) {
		if (!player.isOnline()) {
			return;
		}
		if (!result.ok) {
			player.sendMessage(result.error);
			player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}

		Faction attacker = FactionManager.getByString(state.attackerId);
		Faction defender = FactionManager.getByString(state.defenderId);
		if (attacker == null || defender == null) {
			player.sendMessage("§cThat faction no longer exists.");
			return;
		}

		WarGoalType goal = result.goal;
		WarDeclareCodeService.openSession(player, new WarDeclareCodeService.Session(
				code, state.attackerId, state.defenderId, goal));
		player.sendMessage("§aCode accepted. War goal: §f" + goal.getDisplayName());
		player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		// Straight to the goal's own sub-picker: a pinned goal never sees the picker,
		// so the pin is real rather than a filtered list the player could work around.
		FactionManager.getInv().declareWarView.routeGoal(player, attacker, defender, goal);
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		pending.remove(event.getPlayer());
		WarDeclareCodeService.clearSession(event.getPlayer());
	}
}
