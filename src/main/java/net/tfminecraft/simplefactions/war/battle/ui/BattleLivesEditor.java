package net.tfminecraft.simplefactions.war.battle.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleStaffService;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyLedger;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyService;

/**
 * The "Lives Edit" view of a running battle. Staff stage a change for each side (left +5,
 * right -5) and apply them together with a second click on the confirm item. A staged change
 * is kept as a difference, so deaths during the edit are not undone when it is applied.
 */
public final class BattleLivesEditor {
	public static final String TITLE = "§7Lives Edit";
	public static final int FIRST_SIDE_SLOT = 9;
	public static final int CONFIRM_SLOT = 22;
	public static final int BACK_SLOT = 26;
	static final int STEP = 5;

	private static final Map<UUID, Draft> DRAFTS = new HashMap<>();

	private static final class Draft {
		private final String battleId;
		private final Map<String, Integer> changes = new LinkedHashMap<>();
		private boolean armed;

		private Draft(String battleId) {
			this.battleId = battleId;
		}

		private int change(BattleSide side) {
			return changes.getOrDefault(side.getId(), 0);
		}
	}

	private BattleLivesEditor() {}

	static void resetForTests() {
		DRAFTS.clear();
	}

	public static void open(Player player, Battle battle) {
		Draft draft = new Draft(battle.getId());
		DRAFTS.put(player.getUniqueId(), draft);
		Inventory inventory = SimpleFactions.plugin.getServer().createInventory(null, 27, TITLE);
		render(inventory, battle, draft);
		player.openInventory(inventory);
	}

	/** Handles a left or right click in the view; true when the editor should return to the battle view. */
	public static boolean click(Player player, Battle battle, int slot, ClickType click, Inventory inventory) {
		Draft draft = DRAFTS.get(player.getUniqueId());
		if (slot == BACK_SLOT || draft == null || !draft.battleId.equalsIgnoreCase(battle.getId())) {
			DRAFTS.remove(player.getUniqueId());
			return true;
		}
		if (slot == CONFIRM_SLOT) {
			if (!draft.armed) {
				draft.armed = true;
				render(inventory, battle, draft);
				return false;
			}
			DRAFTS.remove(player.getUniqueId());
			apply(player, battle, draft);
			return true;
		}
		int index = slot - FIRST_SIDE_SLOT;
		if (index < 0 || index >= Math.min(battle.getSides().size(), 9)) {
			return false;
		}
		BattleSide side = battle.getSides().get(index);
		int change = draft.change(side) + (click == ClickType.RIGHT ? -STEP : STEP);
		// Never stage below 0 lives.
		change = Math.max(change, -side.getLives());
		draft.changes.put(side.getId(), change);
		draft.armed = false;
		render(inventory, battle, draft);
		return false;
	}

	private static void apply(Player player, Battle battle, Draft draft) {
		boolean changed = false;
		for (BattleSide side : battle.getSides()) {
			int change = draft.change(side);
			if (change == 0) {
				continue;
			}
			changed = true;
			BattleStaffService.Result result = BattleStaffService.adjustLives(
					battle, side.getId(), "set", Math.max(0, side.getLives() + change), player.getName());
			player.sendMessage(result.ok() ? result.message() : "§c" + result.message());
		}
		if (!changed) {
			player.sendMessage("§7No lives changed.");
		}
	}

	private static void render(Inventory inventory, Battle battle, Draft draft) {
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			inventory.setItem(slot, null);
		}
		List<BattleSide> sides = battle.getSides();
		for (int index = 0; index < Math.min(sides.size(), 9); index++) {
			inventory.setItem(FIRST_SIDE_SLOT + index, sideItem(sides.get(index), draft.change(sides.get(index))));
		}
		inventory.setItem(CONFIRM_SLOT, confirmItem(battle, draft));
		inventory.setItem(BACK_SLOT, new BattleInventoryManager().createBackButton());
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private static ItemStack sideItem(BattleSide side, int change) {
		ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§f" + side.getId());
		List<String> lore = new ArrayList<>();
		lore.add("§7Lives now: §e" + side.getLives() + "§7/§e" + side.getMaxLives());
		lore.add("§7New lives: §e" + Math.max(0, side.getLives() + change)
				+ (change == 0 ? "" : " §7(" + (change > 0 ? "+" : "") + change + ")"));
		lore.add(" ");
		lore.add("§7Left-click: §a+" + STEP);
		lore.add("§7Right-click: §c-" + STEP);
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private static ItemStack confirmItem(Battle battle, Draft draft) {
		ItemStack item = new ItemStack(draft.armed ? Material.ORANGE_CONCRETE : Material.LIME_CONCRETE, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§aApply lives");
		List<String> lore = new ArrayList<>();
		for (BattleSide side : battle.getSides()) {
			int change = draft.change(side);
			if (change != 0) {
				lore.add("§7" + side.getId() + ": §e" + side.getLives() + " §7to §e" + Math.max(0, side.getLives() + change));
			}
		}
		if (lore.isEmpty()) {
			lore.add("§7No changes staged.");
		}
		lore.add(" ");
		for (String line : wrap(regimentWarning(battle), 40)) {
			lore.add("§6" + line);
		}
		lore.add(" ");
		lore.add(draft.armed ? "§cClick again to apply" : "§eClick to apply");
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	/** What lives do and do not change, with each side's deaths so far. */
	public static String regimentWarning(Battle battle) {
		String zero = " At 0 lives a side loses once all its soldiers are dead or jailed.";
		if (!BattleCasualtyLedger.tracksCasualties(battle)) {
			return "Lives only limit respawns. Deaths in this battle cost no regiments." + zero;
		}
		Map<String, Integer> casualties = BattleCasualtyLedger.getSideCasualties(battle);
		List<String> deaths = new ArrayList<>();
		for (BattleSide side : battle.getSides()) {
			deaths.add(side.getId() + ": " + casualties.getOrDefault(side.getId().toLowerCase(Locale.ROOT), 0) + " so far");
		}
		return "Lives only limit respawns. Regiments are lost through deaths: every "
				+ BattleCasualtyService.deathsPerRegimentLoss() + " deaths costs a side 1 regiment when the battle ends ("
				+ String.join(", ", deaths) + "). More lives means more possible deaths and losses." + zero;
	}

	static List<String> wrap(String text, int width) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
				lines.add(line.toString());
				line.setLength(0);
			}
			if (!line.isEmpty()) {
				line.append(' ');
			}
			line.append(word);
		}
		lines.add(line.toString());
		return lines;
	}
}
