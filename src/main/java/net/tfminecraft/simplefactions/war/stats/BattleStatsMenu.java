package net.tfminecraft.simplefactions.war.stats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.war.battle.ui.BattlePermissions;

/**
 * The {@code /battle stats} menu: your record over every battle, your battles, one
 * battle's report with your ranks, and wars. Built only when opened, from the
 * in-memory summaries plus one record file for a battle report.
 */
public final class BattleStatsMenu implements Listener {
	static final int SIZE = 54;
	static final int FIRST_LIST_SLOT = 9;
	static final int PER_PAGE = 36;
	static final int BACK_SLOT = 45;
	static final int NEXT_SLOT = 53;

	/** Marks a stats menu and maps its slots to what a click does. */
	public static final class Holder implements InventoryHolder {
		final Map<Integer, Consumer<Player>> actions = new HashMap<>();
		private Inventory inventory;

		@Override
		public Inventory getInventory() {
			return inventory;
		}
	}

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder holder)) {
			return;
		}
		event.setCancelled(true);
		Consumer<Player> action = holder.actions.get(event.getRawSlot());
		if (action != null && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)
				&& event.getWhoClicked() instanceof Player player) {
			action.accept(player);
		}
	}

	@EventHandler
	public void onDrag(InventoryDragEvent event) {
		if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
			event.setCancelled(true);
		}
	}

	/** Your record over all battles and the list of battles (all battles for staff). */
	static void home(Player viewer, int page) {
		String key = Members.key(viewer);
		boolean staff = BattlePermissions.isAdmin(viewer);
		List<BattleStatsArchive.Summary> battles = BattleStatsArchive.list(staff ? null : key);
		Holder holder = new Holder();
		Inventory inventory = create(holder, staff ? "§8Battle stats (all battles)" : "§8Your battle stats");
		inventory.setItem(4, record(key));
		set(holder, 8, item(Material.FILLED_MAP, "§eWars", List.of("§7Your totals in each war")), p -> wars(p, 1));
		int pages = Math.max(1, (battles.size() + PER_PAGE - 1) / PER_PAGE);
		int shown = Math.max(1, Math.min(page, pages));
		List<BattleStatsArchive.Summary> slice = battles.subList((shown - 1) * PER_PAGE, Math.min(battles.size(), shown * PER_PAGE));
		for (int i = 0; i < slice.size(); i++) {
			BattleStatsArchive.Summary summary = slice.get(i);
			set(holder, FIRST_LIST_SLOT + i, battleItem(summary, key), p -> openBattle(p, summary.id(), shown));
		}
		if (battles.isEmpty()) {
			inventory.setItem(22, item(Material.PAPER, "§7No battles yet", List.of("§7Fight in a battle to see your stats here.")));
		}
		if (shown > 1) {
			set(holder, BACK_SLOT, item(Material.ARROW, "§ePrevious page", List.of()), p -> home(p, shown - 1));
		}
		if (shown < pages) {
			set(holder, NEXT_SLOT, item(Material.ARROW, "§eNext page", List.of("§7Page " + (shown + 1) + " of " + pages)), p -> home(p, shown + 1));
		}
		viewer.openInventory(inventory);
	}

	/** One battle: what happened, each side, and your numbers with where you ranked. */
	static void battle(Player viewer, BattleRecord record, int backPage) {
		String key = Members.key(viewer);
		Holder holder = new Holder();
		Inventory inventory = create(holder, "§8Battle report");
		List<String> info = new ArrayList<>();
		info.add("§7" + BattleStatsView.kind(record) + ", " + BattleStatsView.duration(record.endedAt - record.startedAt));
		info.add("§7" + BattleStatsView.DATE.format(Instant.ofEpochMilli(record.endedAt)));
		if (record.warName != null) info.add("§7War: §f" + record.warName);
		info.add(record.winner == null ? "§7No winner §8(" + BattleStatsView.reason(record.endReason) + ")"
				: "§aWinner: §e" + record.winner + " §8(" + BattleStatsView.reason(record.endReason) + ")");
		inventory.setItem(4, item(Material.BOOK, "§6" + record.name, info));
		int[] sideSlots = sideSlots(record.sides.size());
		for (int i = 0; i < sideSlots.length; i++) {
			BattleRecord.SideRecord side = record.sides.get(i);
			inventory.setItem(sideSlots[i], sideItem(record, side));
		}
		FighterStats own = key == null ? null : record.fighter(key);
		if (own != null) {
			inventory.setItem(31, ownItem(record.fighters, own, "§bYou §7(" + own.name + ", " + own.side + BattleStatsView.classSuffix(own) + ")"));
		}
		if (BattlePermissions.isAdmin(viewer)) {
			List<String> shares = new ArrayList<>(BattleStatsView.classShares(record));
			shares.add("§eClick for every fighter's line in chat");
			set(holder, 40, item(Material.WRITABLE_BOOK, "§6Staff: fighters and classes", shares), p -> {
				p.closeInventory();
				BattleStatsView.fighterTable(p, record);
			});
		}
		set(holder, BACK_SLOT, item(Material.ARROW, "§eBack", List.of()), p -> home(p, backPage));
		viewer.openInventory(inventory);
	}

	/** Wars you fought battles in (every war for staff). */
	static void wars(Player viewer, int page) {
		String key = Members.key(viewer);
		boolean staff = BattlePermissions.isAdmin(viewer);
		Map<String, List<BattleStatsArchive.Summary>> wars = new LinkedHashMap<>();
		for (BattleStatsArchive.Summary summary : BattleStatsArchive.list(staff ? null : key)) {
			if (summary.warKey() != null) {
				wars.computeIfAbsent(summary.warKey(), k -> new ArrayList<>()).add(summary);
			}
		}
		Holder holder = new Holder();
		Inventory inventory = create(holder, "§8Wars");
		List<Map.Entry<String, List<BattleStatsArchive.Summary>>> list = new ArrayList<>(wars.entrySet());
		int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
		int shown = Math.max(1, Math.min(page, pages));
		List<Map.Entry<String, List<BattleStatsArchive.Summary>>> slice =
				list.subList((shown - 1) * PER_PAGE, Math.min(list.size(), shown * PER_PAGE));
		for (int i = 0; i < slice.size(); i++) {
			Map.Entry<String, List<BattleStatsArchive.Summary>> war = slice.get(i);
			List<BattleStatsArchive.Summary> battles = war.getValue();
			set(holder, FIRST_LIST_SLOT + i, item(Material.FILLED_MAP, "§e" + battles.get(0).warName(),
					List.of("§7" + battles.size() + " battles", "§eClick for the war's totals")), p -> war(p, war.getKey()));
		}
		if (list.isEmpty()) {
			inventory.setItem(22, item(Material.PAPER, "§7No war battles yet", List.of()));
		}
		if (shown < pages) {
			set(holder, NEXT_SLOT, item(Material.ARROW, "§eNext page", List.of()), p -> wars(p, shown + 1));
		}
		set(holder, BACK_SLOT, item(Material.ARROW, "§eBack", List.of()), p -> home(p, 1));
		viewer.openInventory(inventory);
	}

	/** One war: its battles, wins per side and your totals with where you ranked. */
	static void war(Player viewer, String warKey) {
		String key = Members.key(viewer);
		boolean staff = BattlePermissions.isAdmin(viewer);
		List<BattleStatsArchive.Summary> battles = new ArrayList<>();
		for (BattleStatsArchive.Summary summary : BattleStatsArchive.list(staff ? null : key)) {
			if (warKey.equalsIgnoreCase(summary.warKey())) {
				battles.add(summary);
			}
		}
		if (battles.isEmpty()) {
			viewer.sendMessage("§cNo battle stats for that war that you fought in.");
			return;
		}
		Holder holder = new Holder();
		Inventory inventory = create(holder, "§8War report");
		Map<String, Integer> wins = new LinkedHashMap<>();
		Map<String, FighterStats> totals = new LinkedHashMap<>();
		for (BattleStatsArchive.Summary battle : battles) {
			if (battle.winner() != null) wins.merge(winnerLabel(battle), 1, Integer::sum);
			for (FighterStats f : battle.fighters().values()) {
				add(totals.computeIfAbsent(f.key, k -> new FighterStats()), f);
			}
		}
		List<String> info = new ArrayList<>();
		info.add("§7" + battles.size() + " battles");
		wins.forEach((side, count) -> info.add("§7Won by §e" + side + "§7: §f" + count));
		inventory.setItem(4, item(Material.FILLED_MAP, "§6" + battles.get(0).warName(), info));
		FighterStats own = key == null ? null : totals.get(key);
		if (own != null) {
			inventory.setItem(13, ownItem(new ArrayList<>(totals.values()), own, "§bYou in this war"));
		}
		for (int i = 0; i < Math.min(battles.size(), 27); i++) {
			BattleStatsArchive.Summary summary = battles.get(i);
			set(holder, 18 + i, battleItem(summary, key), p -> openBattle(p, summary.id(), 1));
		}
		set(holder, BACK_SLOT, item(Material.ARROW, "§eBack", List.of()), p -> wars(p, 1));
		viewer.openInventory(inventory);
	}

	/** Battle sides can swap between battles, so a war counts wins by the war's attackers and defenders. */
	static String winnerLabel(BattleStatsArchive.Summary battle) {
		String role = battle.winnerRole();
		return role == null ? battle.winner() : "ATTACKER".equals(role) ? "the war's attackers" : "the war's defenders";
	}

	private static void openBattle(Player viewer, String id, int backPage) {
		BattleRecord record = BattleStatsArchive.get(id);
		if (record == null) {
			viewer.sendMessage("§cThat battle's stats could not be read.");
			return;
		}
		battle(viewer, record, backPage);
	}

	/** Totals over every battle the character fought in. */
	private static ItemStack record(String key) {
		FighterStats sum = new FighterStats();
		int battles = 0;
		int won = 0;
		int lost = 0;
		for (BattleStatsArchive.Summary summary : BattleStatsArchive.list(key)) {
			FighterStats own = key == null ? null : summary.fighters().get(key);
			if (own == null) continue;
			battles++;
			if (summary.winner() != null && summary.winner().equalsIgnoreCase(own.side)) won++;
			else if (summary.winner() != null) lost++;
			add(sum, own);
		}
		List<String> lore = new ArrayList<>();
		lore.add("§7Battles: §f" + battles + " §8(§a" + won + " won§8, §c" + lost + " lost§8, §7" + (battles - won - lost) + " no result§8)");
		lore.addAll(lines(sum));
		return item(Material.NETHER_STAR, "§6Your battle record", lore);
	}

	private static ItemStack battleItem(BattleStatsArchive.Summary summary, String key) {
		FighterStats own = key == null ? null : summary.fighters().get(key);
		Material icon = own == null || summary.winner() == null ? Material.STONE_SWORD
				: summary.winner().equalsIgnoreCase(own.side) ? Material.GOLDEN_SWORD : Material.IRON_SWORD;
		List<String> lore = new ArrayList<>();
		lore.add("§7" + BattleStatsView.DATE.format(Instant.ofEpochMilli(summary.endedAt())));
		if (summary.warName() != null) lore.add("§7War: §f" + summary.warName());
		lore.add(summary.winner() == null ? "§7No winner" : "§7Won by §e" + summary.winner());
		if (own != null) {
			lore.add("§bYou: §f" + own.kills + " kills, " + own.assists + " assists, " + own.deaths + " deaths");
			lore.add("§f" + BattleStatsView.round(own.damage) + " damage, " + BattleStatsView.round(own.healing) + " healing, "
					+ BattleStatsView.round(own.cc) + "s CC");
		}
		lore.add("§eClick for the report");
		return item(icon, "§e" + summary.name(), lore);
	}

	private static ItemStack sideItem(BattleRecord record, BattleRecord.SideRecord side) {
		FighterStats sum = new FighterStats();
		int fighters = 0;
		java.util.TreeSet<String> factions = new java.util.TreeSet<>();
		for (FighterStats f : record.fighters) {
			if (side.id.equalsIgnoreCase(f.side)) {
				fighters++;
				add(sum, f);
				if (f.faction != null) factions.add(f.faction);
			}
		}
		List<String> lore = new ArrayList<>();
		lore.add("§7" + fighters + " fighters" + (factions.isEmpty() ? "" : " §8(" + String.join("§8, ", factions) + "§8)"));
		if (side.maxLives > 0) lore.add("§7Lives left: §f" + side.lives + "/" + side.maxLives);
		lore.add("§f" + sum.kills + " kills, " + sum.deaths + " deaths");
		lore.add("§f" + BattleStatsView.round(sum.damage) + " damage, " + BattleStatsView.round(sum.healing) + " healing, "
				+ BattleStatsView.round(sum.cc) + "s CC");
		boolean winner = side.id.equalsIgnoreCase(record.winner);
		Material banner = side.id.equalsIgnoreCase("attacker") ? Material.RED_BANNER
				: side.id.equalsIgnoreCase("defender") ? Material.BLUE_BANNER : Material.WHITE_BANNER;
		return item(banner, (winner ? "§a" : "§e") + side.id + (winner ? " §7(won)" : ""), lore);
	}

	/** Your numbers, each ranked among everyone in the same list. */
	private static ItemStack ownItem(List<FighterStats> all, FighterStats own, String title) {
		int of = all.size();
		List<String> lore = new ArrayList<>();
		lore.add("§fKills §e" + own.kills + rank(all, own, f -> f.kills, of) + " §8· §fAssists §e" + own.assists
				+ rank(all, own, f -> f.assists, of));
		lore.add("§fDeaths §e" + own.deaths);
		lore.add("§fDamage §e" + BattleStatsView.round(own.damage) + rank(all, own, f -> f.damage, of)
				+ " §8· §7taken " + BattleStatsView.round(own.damageTaken));
		lore.add("§fHealing §a" + BattleStatsView.round(own.healing) + rank(all, own, f -> f.healing, of)
				+ " §8· §7self " + BattleStatsView.round(own.selfHealing));
		lore.add("§fCrowd control §b" + BattleStatsView.round(own.cc) + "s" + rank(all, own, f -> f.cc, of)
				+ " §8· §7taken " + BattleStatsView.round(own.ccTaken) + "s");
		lore.add("§7Friendly fire: " + BattleStatsView.round(own.friendlyDamage) + " damage, "
				+ BattleStatsView.round(own.friendlyCc) + "s CC, " + own.teamKills + " team kills");
		return item(Material.PLAYER_HEAD, title, lore);
	}

	private static String rank(List<FighterStats> all, FighterStats own,
			java.util.function.ToDoubleFunction<FighterStats> value, int of) {
		return " §7(#" + BattleStatsView.rank(all, own, value) + " of " + of + ")";
	}

	private static List<String> lines(FighterStats sum) {
		return List.of(
				"§fKills §e" + sum.kills + " §8· §fAssists §e" + sum.assists + " §8· §fDeaths §e" + sum.deaths,
				"§fDamage §e" + BattleStatsView.round(sum.damage) + " §8· §fHealing §a" + BattleStatsView.round(sum.healing),
				"§fCrowd control §b" + BattleStatsView.round(sum.cc) + "s",
				"§7Team kills " + sum.teamKills + ", friendly damage " + BattleStatsView.round(sum.friendlyDamage));
	}

	private static void add(FighterStats sum, FighterStats f) {
		sum.key = f.key;
		sum.name = f.name;
		sum.kills += f.kills;
		sum.assists += f.assists;
		sum.deaths += f.deaths;
		sum.teamKills += f.teamKills;
		sum.damage += f.damage;
		sum.damageTaken += f.damageTaken;
		sum.friendlyDamage += f.friendlyDamage;
		sum.healing += f.healing;
		sum.selfHealing += f.selfHealing;
		sum.cc += f.cc;
		sum.ccTaken += f.ccTaken;
		sum.friendlyCc += f.friendlyCc;
	}

	/** Two sides sit left and right of your item; more sides fill the row. */
	static int[] sideSlots(int sides) {
		if (sides == 2) return new int[] {20, 24};
		int[] slots = new int[Math.min(sides, 7)];
		for (int i = 0; i < slots.length; i++) slots[i] = 19 + i;
		return slots;
	}

	private static Inventory create(Holder holder, String title) {
		Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
		holder.inventory = inventory;
		return inventory;
	}

	private static void set(Holder holder, int slot, ItemStack item, Consumer<Player> action) {
		holder.inventory.setItem(slot, item);
		holder.actions.put(slot, action);
	}

	// Keep the existing legacy text representation used by the plugin's other menus.
	@SuppressWarnings("deprecation")
	private static ItemStack item(Material material, String name, List<String> lore) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.setDisplayName(name);
			meta.setLore(lore);
			item.setItemMeta(meta);
		}
		return item;
	}
}
