package net.tfminecraft.simplefactions.managers.inventory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.diplomacy.coalition.Coalition;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionCodePrompt;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionCost;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionRegistry;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionService;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionWars;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.holder.CoalitionHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.truce.TruceService;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/** {@code /coalition list} and {@code /coalition menu}. Anyone may look; only members act. */
public class CoalitionView {
	public static final String LIST_TITLE = "§7Coalitions";
	public static final String MENU_TITLE = "§7Coalition";
	static final int PAGE_SIZE = 45;
	static final int PREV_PAGE_SLOT = 45;
	static final int NEXT_PAGE_SLOT = 53;
	static final int BACK_SLOT = 53;
	static final int TARGET_SLOT = 2;
	static final int INFO_SLOT = 4;
	private static final long CONFIRM_MILLIS = 15_000L;

	private record PendingConfirm(String key, long until) {}

	private final InventoryManager inv;
	private final Map<UUID, PendingConfirm> confirms = new HashMap<>();

	public CoalitionView(InventoryManager inv) {
		this.inv = inv;
	}

	// ---- Rendering ----

	public void list(Player player, int page) {
		List<Coalition> coalitions = CoalitionRegistry.all();
		int pages = Math.max(1, (coalitions.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int shown = Math.max(0, Math.min(page, pages - 1));
		Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
				new CoalitionHolder(CoalitionHolder.Type.LIST, null, shown), 54, LIST_TITLE);
		int start = shown * PAGE_SIZE;
		for (int index = start; index < Math.min(coalitions.size(), start + PAGE_SIZE); index++) {
			inventory.setItem(index - start, coalitionItem(coalitions.get(index), true));
		}
		if (coalitions.isEmpty()) {
			inventory.setItem(22, item(new ItemStack(Material.PAPER), "§7No coalitions yet",
					List.of("§7Form one with §e/coalition create <target> <name>"), null, null));
		}
		if (shown > 0) inventory.setItem(PREV_PAGE_SLOT, DefaultCreator.createPreviousPageButton());
		if (shown < pages - 1) inventory.setItem(NEXT_PAGE_SLOT, DefaultCreator.createNextPageButton());
		player.openInventory(inventory);
	}

	public void menu(Player player, Coalition coalition) {
		Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
				new CoalitionHolder(CoalitionHolder.Type.MENU, coalition.getId(), 0), 54, MENU_TITLE);
		inventory.setItem(INFO_SLOT, coalitionItem(coalition, false));
		Faction target = CoalitionService.target(coalition);
		if (target != null) inventory.setItem(TARGET_SLOT, targetItem(coalition, target));
		Faction viewer = FactionManager.getByMember(Members.key(player));
		if (viewer != null && coalition.isMember(viewer.getId())) {
			inventory.setItem(6, seatItem(viewer));
		}
		int slot = 9;
		for (Faction member : CoalitionService.members(coalition)) {
			if (slot > 26) break;
			inventory.setItem(slot++, memberItem(coalition, member));
		}
		slot = 27;
		for (Map.Entry<War, Faction> entry : coalitionWars(coalition).entrySet()) {
			if (slot > 35) break;
			inventory.setItem(slot++, warItem(entry.getKey(), entry.getValue()));
		}
		addButtons(inventory, player, coalition);
		inventory.setItem(BACK_SLOT, inv.createBackButton(net.tfminecraft.simplefactions.enums.SFGUI.FACTION_VIEW));
		player.openInventory(inventory);
	}

	private void addButtons(Inventory inventory, Player player, Coalition coalition) {
		Faction led = FactionManager.getByLeader(Members.key(player));
		if (led == null || !coalition.isMember(led.getId())) return;
		if (!coalition.isLeader(led.getId())) {
			inventory.setItem(49, item(new ItemStack(Material.OAK_DOOR), "§cLeave the coalition",
					List.of("§7Click twice to leave.", "§7You cannot join or form another", "§7coalition for a while after."),
					"leave", coalition.getId()));
			return;
		}
		inventory.setItem(45, item(new ItemStack(Material.WHITE_BANNER), "§eUse the banner in your hand",
				List.of("§7Hold a banner and click."), "banner", coalition.getId()));
		inventory.setItem(46, item(new ItemStack(Material.BRUSH), "§eUse your faction's banner",
				List.of("§7The coalition shows its leader's banner."), "reset_banner", coalition.getId()));
		if (!coalition.isOffensive() && !CoalitionService.targetLost(coalition)) {
			inventory.setItem(47, item(new ItemStack(Material.IRON_SWORD), "§cMake the coalition offensive",
					List.of("§7Needs a code from staff.", "§7Click to enter it in chat."), "offensive", coalition.getId()));
		}
		inventory.setItem(48, item(new ItemStack(Material.WRITABLE_BOOK), "§aInvite a faction",
				List.of("§7Use §e/coalition invite <faction>"), null, null));
		if (CoalitionService.targetLost(coalition)) {
			inventory.setItem(50, item(new ItemStack(Material.TARGET), "§eChoose a new target",
					List.of("§7Use §e/coalition target <faction>"), null, null));
		}
		inventory.setItem(49, item(new ItemStack(Material.TNT), "§cDisband the coalition",
				List.of("§7Click twice to disband.", "§7Every member waits a while", "§7before joining another."),
				"disband", coalition.getId()));
	}

	/** The banner item standing for a coalition, in the list or at the top of its menu. */
	public static ItemStack coalitionItem(Coalition coalition, boolean clickable) {
		List<String> lore = new ArrayList<>();
		lore.add(coalition.isOffensive() ? hex("#c74c3fOffensive coalition") : hex("#7fbd73Defensive coalition"));
		Faction leader = CoalitionService.leader(coalition);
		Faction target = CoalitionService.target(coalition);
		lore.add(hex("#b8ae61Leader: #d4c9ae") + (leader == null ? "-" : leader.getName()));
		lore.add(hex("#b8ae61Target: #d4c9ae") + (target == null ? coalition.getTargetId() : target.getName()));
		lore.add(hex("#b8ae61Members: #d4c9ae") + coalition.getMembers().size());
		Duration remaining = CoalitionService.targetLossRemaining(coalition);
		if (remaining != null) {
			lore.add(" ");
			lore.add(hex("#c74c3fTarget lost. Disbands in ") + TruceService.formatRemaining(remaining));
		}
		if (clickable) {
			lore.add(" ");
			lore.add(hex("#28ed70Click to view"));
		}
		return item(CoalitionService.banner(coalition), CoalitionService.displayName(coalition), lore,
				clickable ? "open" : null, coalition.getId());
	}

	private ItemStack targetItem(Coalition coalition, Faction target) {
		List<String> lore = new ArrayList<>();
		lore.add(hex("#c74c3fTarget"));
		lore.add(hex("#4793bfRealm prestige: #6eafba") + Math.round(CoalitionCost.realmPrestige(target)));
		if (CoalitionService.targetLost(coalition)) lore.add(hex("#c74c3fNo longer a valid target"));
		return item(new ItemStack(target.getBanner()), target.getName(), lore, null, null);
	}

	private ItemStack seatItem(Faction viewer) {
		double cost = CoalitionCost.costFor(viewer);
		double free = viewer.getDiplomacyHandler().getAvailableCapacity();
		return item(new ItemStack(Material.PAPER), hex("#a89977Your seat"), List.of(
				hex("#a39ba8Diplomatic capacity used: #d4c9ae") + Math.round(cost),
				hex("#a39ba8Capacity left: #d4c9ae") + Math.round(free)), null, null);
	}

	private ItemStack memberItem(Coalition coalition, Faction member) {
		List<String> lore = new ArrayList<>();
		lore.add(coalition.isLeader(member.getId()) ? hex("#d4bb98Leader") : hex("#a39ba8Member"));
		lore.add(hex("#4793bfRealm prestige: #6eafba") + Math.round(CoalitionCost.realmPrestige(member)));
		lore.add(hex("#a39ba8Seat cost: #d4c9ae") + Math.round(CoalitionCost.costFor(member)) + hex(" #a39ba8DC"));
		return item(new ItemStack(member.getBanner()), member.getName(), lore, null, null);
	}

	private ItemStack warItem(War war, Faction caller) {
		int callable = CoalitionWars.callableMembers(war, caller).size();
		return item(new ItemStack(Material.IRON_SWORD), war.getName(), List.of(
				hex("#a39ba8Can be called by: ") + caller.getName(),
				hex("#a39ba8Members left to call: #d4c9ae") + callable,
				" ", hex("#28ed70Click to view")), "war", String.valueOf(war.getId()));
	}

	/** Each running war the coalition can be called into, with the member that can call it. */
	static Map<War, Faction> coalitionWars(Coalition coalition) {
		Map<War, Faction> wars = new LinkedHashMap<>();
		for (War war : WarManager.getActive()) {
			for (Faction member : CoalitionService.members(coalition)) {
				if (CoalitionWars.isCoalitionWar(war, coalition, member)) wars.putIfAbsent(war, member);
			}
		}
		return wars;
	}

	/** The coalition line in a faction's own menu, or null when it has nothing to do with one. */
	public static ItemStack factionItem(Faction faction) {
		Coalition coalition = CoalitionService.of(faction);
		List<Coalition> against = CoalitionService.targeting(faction);
		if (coalition == null && against.isEmpty()) return null;
		Coalition shown = coalition != null ? coalition : against.get(0);
		List<String> lore = new ArrayList<>(coalitionLore(faction));
		lore.add(" ");
		lore.add(hex("#28ed70Click to view"));
		return item(CoalitionService.banner(shown), hex("#c97a4aCoalitions"), lore, "open", shown.getId());
	}

	/** {@code Part of X (Coalition)} and {@code Targeted by Y (Coalition)} lines. */
	public static List<String> coalitionLore(Faction faction) {
		List<String> lore = new ArrayList<>();
		Coalition coalition = CoalitionService.of(faction);
		if (coalition != null) {
			lore.add(hex("#c97a4aPart of ") + CoalitionService.displayName(coalition) + hex(" #a39ba8(Coalition)"));
		}
		for (Coalition against : CoalitionService.targeting(faction)) {
			lore.add(hex("#c74c3fTargeted by ") + CoalitionService.displayName(against) + hex(" #a39ba8(Coalition)"));
		}
		return lore;
	}

	// ---- Clicks ----

	public void click(InventoryClickEvent e, Inventory inventory, Player player) {
		e.setCancelled(true);
		CoalitionHolder holder = (CoalitionHolder) inventory.getHolder();
		ItemStack clicked = e.getCurrentItem();
		if (holder.getType() == CoalitionHolder.Type.LIST) {
			if (e.getSlot() == PREV_PAGE_SLOT && clicked != null) {
				list(player, holder.getPage() - 1);
				return;
			}
			if (e.getSlot() == NEXT_PAGE_SLOT && clicked != null) {
				list(player, holder.getPage() + 1);
				return;
			}
		} else if (e.getSlot() == BACK_SLOT) {
			list(player, 0);
			return;
		}
		String action = data(clicked, "coalition_action");
		String id = data(clicked, "id");
		if (action == null) return;
		player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		switch (action) {
			case "open" -> {
				Coalition coalition = CoalitionRegistry.get(id);
				if (coalition != null) menu(player, coalition);
			}
			case "war" -> {
				War war = WarManager.getById(Integer.parseInt(id));
				if (war != null) inv.warView(null, player, war, true);
			}
			default -> act(player, action, CoalitionRegistry.get(id));
		}
	}

	private void act(Player player, String action, Coalition coalition) {
		Faction led = FactionManager.getByLeader(Members.key(player));
		if (coalition == null || led == null) return;
		CoalitionService.Result result;
		switch (action) {
			case "banner" -> result = CoalitionService.setBanner(led, player.getInventory().getItemInMainHand());
			case "reset_banner" -> result = CoalitionService.resetBanner(led);
			case "offensive" -> {
				player.closeInventory();
				CoalitionCodePrompt.begin(player, led);
				return;
			}
			case "leave" -> {
				if (!confirmed(player, "leave|" + coalition.getId())) return;
				result = CoalitionService.leave(led);
			}
			default -> {
				if (!confirmed(player, "disband|" + coalition.getId())) return;
				result = CoalitionService.disband(led);
			}
		}
		player.sendMessage(result.message());
		if (CoalitionRegistry.get(coalition.getId()) != null && result.ok()) {
			menu(player, coalition);
		} else if (result.ok()) {
			player.closeInventory();
		}
	}

	/** The first click warns; a second click on the same button soon after goes through. */
	private boolean confirmed(Player player, String key) {
		long now = System.currentTimeMillis();
		PendingConfirm pending = confirms.get(player.getUniqueId());
		if (pending != null && pending.key().equals(key) && now < pending.until()) {
			confirms.remove(player.getUniqueId());
			return true;
		}
		confirms.put(player.getUniqueId(), new PendingConfirm(key, now + CONFIRM_MILLIS));
		player.sendMessage("§7Click again to confirm.");
		return false;
	}

	// ---- Items ----

	// Keep the existing legacy text representation used by every other menu.
	@SuppressWarnings("deprecation")
	private static ItemStack item(ItemStack base, String name, List<String> lore, String action, String id) {
		ItemMeta meta = base.getItemMeta();
		meta.setDisplayName(name);
		meta.setLore(new ArrayList<>(lore));
		meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
		if (action != null) {
			meta.getPersistentDataContainer().set(key("coalition_action"), PersistentDataType.STRING, action);
			meta.getPersistentDataContainer().set(key("id"), PersistentDataType.STRING, id);
		}
		base.setItemMeta(meta);
		return base;
	}

	private static String data(ItemStack item, String key) {
		if (item == null || !item.hasItemMeta()) return null;
		return item.getItemMeta().getPersistentDataContainer().get(key(key), PersistentDataType.STRING);
	}

	private static NamespacedKey key(String name) {
		return new NamespacedKey(SimpleFactions.plugin, name);
	}

	private static String hex(String text) {
		return StringFormatter.formatHex(text);
	}
}
