package net.tfminecraft.simplefactions.war.battle.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.battle.enums.BattleLootMode;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.events.BattleStartedEvent;
import net.tfminecraft.tlibs.TLibs;

/**
 * Pays every fighter who was online when a battle ended, win, lose, or draw, provided the
 * battle has loot enabled. ITEM and COMMAND pay one identical reward; TABLE rolls the active
 * drop table once per minutes_per_roll the battle lasted, separately for each fighter.
 */
public class BattleLootService implements Listener {

	static Random random = new Random();

	private final Set<String> paidBattleIds = Collections.synchronizedSet(new HashSet<>());
	private boolean warnedThisBattle;

	@EventHandler
	public void onBattleStarted(BattleStartedEvent event) {
		if (event != null && event.getBattleId() != null) {
			paidBattleIds.remove(event.getBattleId().toLowerCase(Locale.ROOT));
		}
	}

	@EventHandler
	public void onBattleEnded(BattleEndedEvent event) {
		if (event == null) {
			return;
		}
		if (!shouldPay(event.isCampaignRaid(), event.isLootEnabled())) {
			return;
		}
		String battleId = event.getBattleId();
		if (battleId == null || battleId.isBlank()) {
			return;
		}
		// endBattle can be reached from more than one polled win check, and it fires
		// this event every time even though battle.end() is itself idempotent.
		if (!paidBattleIds.add(battleId.toLowerCase(Locale.ROOT))) {
			return;
		}
		warnedThisBattle = false;
		for (UUID id : event.getParticipantIds()) {
			Player player = Bukkit.getPlayer(id);
			if (player == null || !player.isOnline()) {
				continue;
			}
			pay(player, event.getFoughtSeconds());
		}
	}

	public static boolean shouldPay(boolean campaignRaid, boolean lootEnabled) {
		return !campaignRaid && lootEnabled;
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

	private void pay(Player player, Long foughtSeconds) {
		if (Cache.battleLootMode == BattleLootMode.ITEM) {
			payItem(player);
		} else if (Cache.battleLootMode == BattleLootMode.TABLE) {
			payTable(player, foughtSeconds);
		} else {
			payCommands(player);
		}
	}

	private void payTable(Player player, Long foughtSeconds) {
		BattleLootTable table = Cache.battleLootTable;
		if (table == null || table.isEmpty()) {
			warnOnce("war.battle_loot.mode is TABLE but war.battle_loot.tables."
					+ (table == null ? "?" : table.getActiveTable()) + " has no entries; no loot was paid.");
			return;
		}
		BattleLootTable.Payout payout = table.roll(random, table.rollsFor(foughtSeconds));
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, Integer> drop : payout.goods().entrySet()) {
			ItemStack base = build(drop.getKey());
			if (base == null) {
				continue;
			}
			giveStacks(player, base, drop.getValue());
			parts.add(drop.getValue() + " " + itemName(base));
		}
		long denarsPaid = 0;
		for (BattleLootTable.Coin coin : table.getCoins()) {
			Integer count = payout.coins().get(coin.item());
			if (count == null) {
				continue;
			}
			ItemStack base = build(coin.item());
			if (base != null) {
				giveStacks(player, base, count);
				denarsPaid += (long) count * coin.value();
			}
		}
		if (denarsPaid > 0) {
			parts.add(denarsPaid + " denars");
		}
		if (!parts.isEmpty()) {
			player.sendMessage("§6Battle spoils: §f" + String.join("§7, §f", parts));
		}
	}

	private ItemStack build(String path) {
		ItemStack item;
		try {
			item = TLibs.getItemAPI().getCreator().getItemFromPath(path);
		} catch (Exception e) {
			item = null;
		}
		if (item == null || item.getType().isAir()) {
			warnOnce("war.battle_loot item '" + path + "' did not resolve to an item; it was not paid.");
			return null;
		}
		return item;
	}

	private static void giveStacks(Player player, ItemStack base, int amount) {
		int max = Math.max(1, base.getMaxStackSize());
		int left = amount;
		while (left > 0) {
			ItemStack stack = base.clone();
			stack.setAmount(Math.min(max, left));
			left -= stack.getAmount();
			giveOrDrop(player, stack);
		}
	}

	/** The item's display name without colour codes, else its material name in words. */
	static String itemName(ItemStack item) {
		ItemMeta meta = null;
		try {
			meta = item.getItemMeta();
		} catch (RuntimeException e) {
			meta = null;
		}
		if (meta != null && meta.hasDisplayName()) {
			@SuppressWarnings("deprecation")
			String display = meta.getDisplayName();
			String plain = display.replaceAll("(?i)§[0-9A-FK-ORX]", "").trim();
			if (!plain.isEmpty()) {
				return plain;
			}
		}
		String[] words = item.getType().name().toLowerCase(Locale.ROOT).split("_");
		StringBuilder name = new StringBuilder();
		for (String word : words) {
			if (name.length() > 0) {
				name.append(' ');
			}
			name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return name.toString();
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
