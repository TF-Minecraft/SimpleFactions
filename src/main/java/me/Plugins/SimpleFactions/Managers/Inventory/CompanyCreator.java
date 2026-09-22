package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Army.MilitaryExpansion;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.upgrade.Upgrade;
import me.Plugins.SimpleFactions.Guild.upgrade.UpgradeExpansion;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.Utils.HomeSettlementNames;
import me.Plugins.SimpleFactions.enums.GuildModifier;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import me.Plugins.TLibs.Utils.TimeFormatter;

public class CompanyCreator {
	/** Repeated on every upgrade item so nobody expects these stats outside a contract. */
	public static final String BUFF_SCOPE_WARNING =
			"#857e59§oOnly while fighting as a hired mercenary";

	public ItemStack createCompanyEntryItem(Guild guild) {
		MercenaryCompany company = guild.getCompany();
		ItemStack item = new ItemStack(Material.IRON_SWORD, 1);
		ItemMeta meta = item.getItemMeta();
		if (company == null) {
			meta.setDisplayName(StringFormatter.formatHex("#b7aae3Found a Mercenary Company"));
			meta.setLore(buildFoundingLore());
		} else if (company.isForming()) {
			meta.setDisplayName(StringFormatter.formatHex("#b7aae3" + company.getName()));
			meta.setLore(buildFormingLore(company));
		} else {
			meta.setDisplayName(StringFormatter.formatHex("#b7aae3" + company.getName()));
			meta.setLore(buildCompanyLore(guild, company));
		}
		item.setItemMeta(meta);
		return item;
	}

	public List<String> buildFoundingLore() {
		List<String> lore = new ArrayList<>();
		lore.add("§7Cost: §e" + Formatter.formatMoney(Cache.mercenaryFormationCost) + "d");
		lore.add("§7Charter time: §e" + TimeFormatter.formatTime(Cache.mercenaryFormationSeconds));
		lore.add(" ");
		lore.add(StringFormatter.formatHex("#877e7cA company starts with one slot"));
		lore.add(StringFormatter.formatHex("#877e7cUse /company found <name>"));
		return lore;
	}

	public List<String> buildFormingLore(MercenaryCompany company) {
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#baa875Founding"));
		lore.add("§7Ready in: §e" + TimeFormatter.formatTime(company.getFormationRemaining()));
		return lore;
	}

	public List<String> buildCompanyLore(Guild guild, MercenaryCompany company) {
		List<String> lore = new ArrayList<>();
		lore.add("§7Leader: §e" + company.getLeader());
		lore.add("§7Home: §e" + HomeSettlementNames.of(guild));
		lore.add("§7Slots: §e" + company.getFilledSlots() + "/" + company.getSlots());
		lore.add("§7Reputation: " + company.getReputationString());
		lore.add(" ");
		lore.add(StringFormatter.formatHex("#d4c9aeDaily burn: §e"
				+ Formatter.formatMoney(company.getDailyBurn()) + "d"));
		lore.add("§7- Slots: §e" + Formatter.formatMoney(company.getSlotUpkeep()) + "d");
		lore.add("§7- Upgrades: §e" + Formatter.formatMoney(company.getUpgradeUpkeep()) + "d");
		lore.add("§7- Wages: §e" + Formatter.formatMoney(company.getWageUpkeep()) + "d");
		lore.add(" ");
		lore.add("§7Contract income: §e"
				+ Formatter.formatMoney(company.getContractIncome()) + "d §7per day");
		double net = company.getNetPosition();
		lore.add(StringFormatter.formatHex((net < 0 ? "#c74d32" : "#7ba85f") + "Net position: "
				+ (net < 0 ? "" : "+") + Formatter.formatMoney(net) + "d"));
		if (burnExceedsIncome(guild, company)) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#c74d32Burn exceeds what this guild earns"));
			lore.add(StringFormatter.formatHex(
					"#877e7cA bankrupt host voids every contract it holds"));
			lore.add(StringFormatter.formatHex(
					"#877e7cand takes the reputation hit for each of them"));
		}
		String blocked = company.getExpansionBlockedReason();
		if (blocked != null) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#c74d32Cannot expand"));
			lore.add(StringFormatter.formatHex("#877e7c" + blocked));
		}
		return lore;
	}

	/**
	 * Everything the guild earns before it pays for its company. The burn is already
	 * a cost inside {@code getNetIncome}, so comparing burn against net income
	 * directly would be comparing a figure against itself.
	 */
	public static double incomeBeforeBurn(Guild guild, MercenaryCompany company) {
		if (guild == null || guild.getLedger() == null) return 0;
		return guild.getLedger().getNetIncome() + company.getDailyBurn();
	}

	/** A bankrupt host voids every contract it holds, so this warning has to be loud. */
	public static boolean burnExceedsIncome(Guild guild, MercenaryCompany company) {
		if (guild == null || guild.getLedger() == null) return false;
		return company.getDailyBurn() > incomeBeforeBurn(guild, company);
	}

	/**
	 * Header of the company screen. A company flies its guild's cloth, so the
	 * guild banner item is the standard rather than a second pattern build.
	 */
	public ItemStack createStandardItem(Guild guild, MercenaryCompany company) {
		ItemStack banner = guild.getBanner();
		ItemStack item = banner == null ? new ItemStack(Material.WHITE_BANNER, 1) : banner.clone();
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#b7aae3" + company.getName()));
		meta.setLore(buildCompanyLore(guild, company));
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createSlotsButton(Guild guild) {
		MercenaryCompany company = guild.getCompany();
		ItemStack item = new ItemStack(Material.IRON_HELMET, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#baa875Slots"));
		List<String> lore = new ArrayList<>();
		lore.add("§7Filled: §e" + company.getFilledSlots() + "/" + company.getSlots());
		lore.add("§7Upkeep: §e" + Formatter.formatMoney(company.getSlotUpkeep()) + "d");
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createRosterButton(Guild guild) {
		MercenaryCompany company = guild.getCompany();
		ItemStack item = new ItemStack(Material.PAPER, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#baa875Roster"));
		List<String> lore = new ArrayList<>();
		lore.add("§7Enlisted: §e" + company.getEnlisted().size());
		lore.add(StringFormatter.formatHex("#877e7cUse /company invite <player>"));
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createUpgradesButton(Guild guild) {
		MercenaryCompany company = guild.getCompany();
		ItemStack item = new ItemStack(Material.WRITABLE_BOOK, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#baa875Company Upgrades"));
		List<String> lore = new ArrayList<>();
		lore.add("§7Upkeep: §e" + Formatter.formatMoney(company.getUpgradeUpkeep()) + "d");
		lore.add(StringFormatter.formatHex(BUFF_SCOPE_WARNING));
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createSlotItem(MercenaryCompany company, int index) {
		String occupant = index < company.getEnlisted().size()
				? company.getEnlisted().get(index) : null;
		ItemStack item = new ItemStack(
				occupant != null ? Material.IRON_CHESTPLATE : Material.LEATHER_CHESTPLATE, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#baa875Slot " + (index + 1)));
		meta.setLore(buildSlotLore(occupant));
		item.setItemMeta(meta);
		return item;
	}

	public List<String> buildSlotLore(String occupant) {
		List<String> lore = new ArrayList<>();
		if (occupant == null) {
			lore.add(StringFormatter.formatHex("#877e7cEmpty"));
			lore.add(StringFormatter.formatHex("#877e7cAn empty slot blocks expansion"));
		} else {
			lore.add("§7Mercenary: §e" + occupant);
		}
		lore.add("§7Upkeep: §e" + Formatter.formatMoney(Cache.mercenarySlotUpkeep) + "d");
		return lore;
	}

	public ItemStack createSlotQueueItem(MilitaryExpansion expansion, int index, Guild guild) {
		ItemStack item = new ItemStack(Material.IRON_HELMET, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#baa875Expanding"));
		List<String> lore = new ArrayList<>();
		if (index == 0) {
			lore.add("§7Time Left: §e" + TimeFormatter.formatTime(expansion.getTimeLeft()));
		} else {
			lore.add(StringFormatter.formatHex("#857e59Queued..."));
		}
		lore.add("§cClick to cancel");
		meta.setLore(lore);
		meta.getPersistentDataContainer().set(
				Keys.QUEUE_CANCEL,
				PersistentDataType.STRING,
				QueueCancelPayload.companySlot(guild.getId(), index));
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createExpandButton(MercenaryCompany company) {
		String blocked = company.getExpansionBlockedReason();
		ItemStack item = new ItemStack(
				blocked == null ? Material.LIME_DYE : Material.GRAY_DYE, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(
				blocked == null ? "#baa875Add a Slot" : "#7a706aAdd a Slot"));
		List<String> lore = new ArrayList<>();
		lore.add("§7Time: §e" + TimeFormatter.formatTime(
				company.getRegiment() == null ? 0 : company.getRegiment().getExpansionTime()));
		lore.add("§7Upkeep: §e" + Formatter.formatMoney(Cache.mercenarySlotUpkeep) + "d per slot");
		if (blocked != null) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#c74d32" + blocked));
		}
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createRosterItem(MercenaryCompany company, String member, boolean leader) {
		ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§e" + member);
		List<String> lore = new ArrayList<>();
		lore.add("§7Company: §e" + company.getName());
		if (leader) {
			lore.add(" ");
			lore.add(StringFormatter.formatHex("#c74d32Click to dismiss"));
		}
		meta.setLore(lore);
		meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, member);
		item.setItemMeta(meta);
		return item;
	}

	public ItemStack createUpgradeItem(Upgrade upgrade) {
		ItemStack item = upgrade.getIconItem();
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(upgrade.getName());
		meta.setLore(buildUpgradeLore(upgrade));
		meta.getPersistentDataContainer()
				.set(Keys.STRING_KEY, PersistentDataType.STRING, upgrade.getId());
		item.setItemMeta(meta);
		return item;
	}

	public List<String> buildUpgradeLore(Upgrade upgrade) {
		List<String> lore = new ArrayList<>();
		lore.add("§7Level: §e" + upgrade.getLevel()
				+ (upgrade.hasMaxLevel() ? "§7/§e" + upgrade.getMaxLevel() : ""));
		for (GuildModifier modifier : upgrade.getModifierKeys()) {
			lore.add(StringFormatter.formatHex(modifier.getName() + "§7: §e+"
					+ Formatter.formatMoney(upgrade.getModifier(modifier).getPerLevel())
					+ " §7per level"));
		}
		lore.add("§7Upkeep: §e" + Formatter.formatMoney(upgrade.getUpkeep()) + "d per level");
		for (String s : upgrade.getDescription()) {
			lore.add(s);
		}
		lore.add(" ");
		lore.add(StringFormatter.formatHex(BUFF_SCOPE_WARNING));
		if (upgrade.isMaxed()) {
			lore.add(StringFormatter.formatHex("#c74d32Maximum level reached"));
		}
		return lore;
	}

	public ItemStack createUpgradeQueueItem(UpgradeExpansion expansion, int index, Guild guild) {
		ItemStack item = expansion.getUpgrade().getIconItem();
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§eTraining " + expansion.getUpgrade().getName());
		List<String> lore = new ArrayList<>();
		if (index == 0) {
			lore.add("§7Time Left: §e" + TimeFormatter.formatTime(expansion.getTimeLeft()));
		} else {
			lore.add(StringFormatter.formatHex("#857e59Queued..."));
		}
		lore.add("§cClick to cancel");
		meta.setLore(lore);
		meta.getPersistentDataContainer().set(
				Keys.QUEUE_CANCEL,
				PersistentDataType.STRING,
				QueueCancelPayload.companyUpgrade(guild.getId(), index));
		item.setItemMeta(meta);
		return item;
	}
}
