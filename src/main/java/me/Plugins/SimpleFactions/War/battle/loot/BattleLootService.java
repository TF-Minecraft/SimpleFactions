package me.Plugins.SimpleFactions.War.battle.loot;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.battle.enums.BattleLootMode;
import me.Plugins.SimpleFactions.War.battle.events.BattleEndedEvent;
import me.Plugins.TLibs.TLibs;

/**
 * Pays one identical reward to every fighter who was online when a battle ended,
 * on both sides, provided the battle produced a winner and has loot enabled.
 */
public class BattleLootService implements Listener {

	private final Set<String> paidBattleIds = Collections.synchronizedSet(new HashSet<>());
	private boolean warnedThisBattle;

	@EventHandler
	public void onBattleEnded(BattleEndedEvent event) {
		if (event == null) {
			return;
		}
		if (!shouldPay(event.hasWinner(), event.isCampaignRaid(), event.isLootEnabled())) {
			return;
		}
		String battleId = event.getBattleId();
		if (battleId == null || battleId.isBlank()) {
			return;
		}
		// endBattle can be reached from more than one polled win check, and it fires
		// this event every time even though battle.end() is itself idempotent.
		if (!paidBattleIds.add(battleId.toLowerCase())) {
			return;
		}
		warnedThisBattle = false;
		for (UUID id : event.getParticipantIds()) {
			if (id == null) {
				continue;
			}
			Player player = Bukkit.getPlayer(id);
			if (player == null || !player.isOnline()) {
				continue;
			}
			pay(player);
		}
	}

	public static boolean shouldPay(boolean hasWinner, boolean campaignRaid, boolean lootEnabled) {
		return hasWinner && !campaignRaid && lootEnabled;
	}

	public static String formatCommand(String template, String playerName) {
		if (template == null || template.isBlank()) {
			return null;
		}
		String name = playerName != null ? playerName : "";
		String command = template.replace("%player%", name).replace("#player#", name);
		if (command.startsWith("/")) {
			command = command.substring(1);
		}
		return command.isBlank() ? null : command;
	}

	private void pay(Player player) {
		if (Cache.battleLootMode == BattleLootMode.ITEM) {
			payItem(player);
		} else {
			payCommands(player);
		}
	}

	private void payCommands(Player player) {
		if (Cache.battleLootCommands == null || Cache.battleLootCommands.isEmpty()) {
			warnOnce("war.battle_loot.mode is COMMAND but war.battle_loot.commands is empty; no loot was paid.");
			return;
		}
		for (String template : Cache.battleLootCommands) {
			String command = formatCommand(template, player.getName());
			if (command == null) {
				continue;
			}
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
		}
	}

	private void payItem(Player player) {
		String path = Cache.battleLootItemPath;
		if (path == null || path.isBlank()) {
			warnOnce("war.battle_loot.mode is ITEM but war.battle_loot.item is blank; no loot was paid.");
			return;
		}
		ItemStack item;
		try {
			item = TLibs.getItemAPI().getCreator().getItemFromPath(path);
		} catch (Exception e) {
			item = null;
		}
		if (item == null || item.getType().isAir()) {
			warnOnce("war.battle_loot.item '" + path + "' did not resolve to an item; no loot was paid.");
			return;
		}
		item.setAmount(Math.max(1, Cache.battleLootItemAmount));
		giveOrDrop(player, item);
	}

	private static void giveOrDrop(Player player, ItemStack item) {
		Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
		for (ItemStack drop : leftover.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), drop);
		}
	}

	private void warnOnce(String message) {
		if (warnedThisBattle) {
			return;
		}
		warnedThisBattle = true;
		if (SimpleFactions.plugin != null) {
			SimpleFactions.plugin.getLogger().warning("[SimpleFactions] " + message);
		}
	}
}
