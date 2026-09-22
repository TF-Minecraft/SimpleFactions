package me.Plugins.SimpleFactions.Managers.Inventory;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Army.MilitaryExpansion;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.upgrade.Upgrade;
import me.Plugins.SimpleFactions.Guild.upgrade.UpgradeExpansion;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompanyService;

public class CompanyView {
	private static final int SLOTS_BUTTON = 11;
	private static final int ROSTER_BUTTON = 13;
	private static final int UPGRADES_BUTTON = 15;
	private static final int CONTRACTS_BUTTON = 22;
	private static final int EXPAND_BUTTON = 4;

	public InventoryManager inv;
	public CompanyCreator creator = new CompanyCreator();

	public CompanyView(InventoryManager inv) {
		this.inv = inv;
	}

	/* =====================================================
	 * Company
	 * ===================================================== */

	public void companyView(Player player, Guild guild) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(
				new SFInventoryHolder(guild.getId(), SFGUI.COMPANY_VIEW), 27, "§7Company View");
		companyView(player, guild, i);
		player.openInventory(i);
	}

	public void companyView(Player player, Guild guild, Inventory i) {
		i.clear();
		MercenaryCompany company = guild.getCompany();
		if (company != null && company.isFormed()) {
			i.setItem(4, creator.createStandardItem(guild, company));
			i.setItem(SLOTS_BUTTON, creator.createSlotsButton(guild));
			i.setItem(ROSTER_BUTTON, creator.createRosterButton(guild));
			i.setItem(UPGRADES_BUTTON, creator.createUpgradesButton(guild));
			i.setItem(CONTRACTS_BUTTON, inv.contractView.creator.createContractsButton(company));
		} else {
			i.setItem(4, creator.createCompanyEntryItem(guild));
		}
		i.setItem(26, inv.createBackButton(SFGUI.COMPANY_VIEW));
	}

	/* =====================================================
	 * Slots
	 * ===================================================== */

	public void slotsView(Player player, Guild guild) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(
				new SFInventoryHolder(guild.getId(), SFGUI.COMPANY_SLOTS_VIEW), 54, "§7Company Slots");
		slotsView(player, guild, i);
		player.openInventory(i);
	}

	public void slotsView(Player player, Guild guild, Inventory i) {
		i.clear();
		MercenaryCompany company = guild.getCompany();
		if (company == null) return;
		for (int slot = 0; slot < company.getSlots() && slot < 36; slot++) {
			i.setItem(9 + slot, creator.createSlotItem(company, slot));
		}
		if (company.isLeader(player.getName())) {
			i.setItem(EXPAND_BUTTON, creator.createExpandButton(company));
		}
		for (int x = 0; x < company.getSlotQueue().size() && x < 3; x++) {
			MilitaryExpansion e = company.getSlotQueue().get(x);
			i.setItem(45 + x, creator.createSlotQueueItem(e, x, guild));
		}
		i.setItem(53, inv.createBackButton(SFGUI.COMPANY_SLOTS_VIEW));
	}

	/* =====================================================
	 * Roster
	 * ===================================================== */

	public void rosterView(Player player, Guild guild) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(
				new SFInventoryHolder(guild.getId(), SFGUI.COMPANY_ROSTER_VIEW), 54, "§7Company Roster");
		rosterView(player, guild, i);
		player.openInventory(i);
	}

	public void rosterView(Player player, Guild guild, Inventory i) {
		i.clear();
		MercenaryCompany company = guild.getCompany();
		if (company == null) return;
		boolean leader = company.isLeader(player.getName());
		for (int x = 0; x < company.getEnlisted().size() && x < 36; x++) {
			i.setItem(9 + x, creator.createRosterItem(company, company.getEnlisted().get(x), leader));
		}
		i.setItem(53, inv.createBackButton(SFGUI.COMPANY_ROSTER_VIEW));
	}

	/* =====================================================
	 * Upgrades
	 * ===================================================== */

	public void companyUpgradeView(Player player, Guild guild) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(
				new SFInventoryHolder(guild.getId(), SFGUI.COMPANY_UPGRADE_VIEW), 54,
				"§7Company Upgrades");
		companyUpgradeView(player, guild, i);
		player.openInventory(i);
	}

	public void companyUpgradeView(Player player, Guild guild, Inventory i) {
		i.clear();
		MercenaryCompany company = guild.getCompany();
		if (company == null) return;
		int index = 9;
		for (Upgrade upgrade : company.getUpgrades()) {
			if (index > 17) break;
			i.setItem(index, creator.createUpgradeItem(upgrade));
			index++;
		}
		int queueIndex = 0;
		for (UpgradeExpansion queued : company.getUpgradeQueue()) {
			if (queueIndex >= 3) break;
			i.setItem(39 + queueIndex, creator.createUpgradeQueueItem(queued, queueIndex, guild));
			queueIndex++;
		}
		i.setItem(53, inv.createBackButton(SFGUI.COMPANY_UPGRADE_VIEW));
	}

	/* =====================================================
	 * Clicks
	 * ===================================================== */

	public void click(InventoryClickEvent e, Inventory inventory, Player p) {
		if (!(inventory.getHolder() instanceof SFInventoryHolder h)) return;
		e.setCancelled(true);
		Guild guild = FactionManager.getGuildByString(h.getId());
		if (guild == null) return;
		MercenaryCompany company = guild.getCompany();
		if (company == null) return;
		switch (h.getType()) {
			case COMPANY_VIEW -> clickCompany(e, inventory, p, guild, company);
			case COMPANY_SLOTS_VIEW -> clickSlots(e, inventory, p, guild, company);
			case COMPANY_ROSTER_VIEW -> clickRoster(e, inventory, p, guild, company);
			case COMPANY_UPGRADE_VIEW -> clickUpgrade(e, inventory, p, guild, company);
			default -> {}
		}
	}

	private void clickCompany(
			InventoryClickEvent e, Inventory inventory, Player p, Guild guild, MercenaryCompany company) {
		if (!company.isFormed()) return;
		switch (e.getSlot()) {
			case SLOTS_BUTTON -> {
				slotsView(p, guild);
				chirp(p);
			}
			case ROSTER_BUTTON -> {
				rosterView(p, guild);
				chirp(p);
			}
			case UPGRADES_BUTTON -> {
				companyUpgradeView(p, guild);
				chirp(p);
			}
			case CONTRACTS_BUTTON -> {
				inv.contractView.listView(p, guild);
				chirp(p);
			}
			default -> {}
		}
	}

	private void clickSlots(
			InventoryClickEvent e, Inventory inventory, Player p, Guild guild, MercenaryCompany company) {
		if (e.getSlot() == EXPAND_BUTTON) {
			report(p, MercenaryCompanyService.expand(guild, p.getName()));
			slotsView(p, guild, inventory);
			return;
		}
		String queuePayload = queueCancelPayload(e.getCurrentItem());
		if (queuePayload != null) {
			if (!company.isLeader(p.getName())) return;
			inv.openQueueCancelConfirm(p, guild.getFaction(), queuePayload, "§eCancel queued slot expansion?");
			chirp(p);
		}
	}

	private void clickRoster(
			InventoryClickEvent e, Inventory inventory, Player p, Guild guild, MercenaryCompany company) {
		String member = stringKey(e.getCurrentItem());
		if (member == null) return;
		report(p, MercenaryCompanyService.kick(guild, p.getName(), member));
		rosterView(p, guild, inventory);
	}

	private void clickUpgrade(
			InventoryClickEvent e, Inventory inventory, Player p, Guild guild, MercenaryCompany company) {
		String queuePayload = queueCancelPayload(e.getCurrentItem());
		if (queuePayload != null) {
			if (!company.isLeader(p.getName())) return;
			inv.openQueueCancelConfirm(p, guild.getFaction(), queuePayload, "§eCancel queued company upgrade?");
			chirp(p);
			return;
		}
		String id = stringKey(e.getCurrentItem());
		if (id == null) return;
		report(p, MercenaryCompanyService.upgrade(guild, p.getName(), id));
		companyUpgradeView(p, guild, inventory);
	}

	private static String queueCancelPayload(ItemStack item) {
		if (item == null || !item.hasItemMeta()) return null;
		return item.getItemMeta().getPersistentDataContainer().get(Keys.QUEUE_CANCEL, PersistentDataType.STRING);
	}

	private static String stringKey(ItemStack item) {
		if (item == null || !item.hasItemMeta()) return null;
		ItemMeta meta = item.getItemMeta();
		return meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
	}

	private static void report(Player p, MercenaryResult result) {
		p.sendMessage((result.ok() ? "§a" : "§c") + result.message());
		p.playSound(p, result.ok() ? Sound.BLOCK_NOTE_BLOCK_BIT : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
	}

	private static void chirp(Player p) {
		p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
	}
}
