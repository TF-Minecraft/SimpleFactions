package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.Branch.Branch;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.upgrade.Upgrade;
import me.Plugins.SimpleFactions.Guild.upgrade.UpgradeExpansion;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelocationPrompt;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.REST.RestServer;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Utils.FactionRanker;
import me.Plugins.SimpleFactions.enums.MenuItemType;
import me.Plugins.SimpleFactions.enums.RankType;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.government.StabilityModifier;
import me.Plugins.SimpleFactions.keys.Keys;

public class GuildView {
	public InventoryManager inv;
	
	public GuildCreator creator = new GuildCreator();
	private ProvinceManager manager;

	public static HashMap<Player, RankType> currentRanking = new HashMap<>();
	public static HashMap<Player, Integer> currentPage = new HashMap<>();

	private static final int INVENTORY_SIZE = 54;

	private static final List<Integer> RESERVED_SLOTS = List.of(
		8, 17, 26, 35, 44,
		45, // prev page
		53  // next page
	);

	private static final int PREV_PAGE_SLOT = 45;
	private static final int NEXT_PAGE_SLOT = 53;

	/** Mercenary company entry. Slot 13 of the guild view already holds the trade breakdown. */
	public static final int COMPANY_SLOT = 18;
	public static final int HOST_FACTION_SLOT = 37;

	
	public GuildView(InventoryManager inv) {
        this.inv = inv;
    }

	public void setProvinceManager(ProvinceManager manager) {
		this.manager = manager;
	}

	public void guildList(Player p) {
		guildList(p, null);
	}

	public void guildList(Player p, Inventory inv) {
		currentRanking.putIfAbsent(p, RankType.WEALTH);
		currentPage.putIfAbsent(p, 0);
		boolean open = inv == null;
		if(open) inv = SimpleFactions.plugin.getServer()
				.createInventory(new SFInventoryHolder(null, SFGUI.GUILD_LIST), INVENTORY_SIZE, "§7Guild List");
		populateGuildList(inv, p);
		if(open) p.openInventory(inv);
	}

	public void populateGuildList(Inventory inv, Player p) {
		currentRanking.putIfAbsent(p, RankType.WEALTH);
		currentPage.putIfAbsent(p, 0);
		RankType rank = currentRanking.get(p);
		int page = currentPage.get(p);

		List<Guild> guilds = new FactionRanker().getRankedGuildList(rank);
		Collections.reverse(guilds);

		List<Integer> usableSlots = new ArrayList<>();
		for (int i = 0; i < INVENTORY_SIZE; i++) {
			if (!RESERVED_SLOTS.contains(i)) {
				usableSlots.add(i);
			}
		}

		inv.clear();
		int perPage = usableSlots.size();
		int start = page * perPage;
		int end = Math.min(start + perPage, guilds.size());

		for (int i = start; i < end; i++) {
			Guild g = guilds.get(i);
			inv.setItem(usableSlots.get(i - start), creator.createListItem(p, g));
		}

		inv.setItem(8, DefaultCreator.createRankButton(rank));
		if (page > 0) inv.setItem(PREV_PAGE_SLOT, DefaultCreator.createPreviousPageButton());
		if (end < guilds.size()) inv.setItem(NEXT_PAGE_SLOT, DefaultCreator.createNextPageButton());
	}

	public void ledgerView(Player player, Guild guild, Inventory i) {
		boolean open = i == null;
		if(i == null) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(guild.getId(), SFGUI.LEDGER_VIEW), 27, "§7Ledger for "+guild.getName());
		i.clear();
		if(guild.isBase()) {
			i.setItem(10, creator.createLedgerCitizensItem(guild));
			i.setItem(11, creator.createLedgerGuildsItem(guild));
			i.setItem(12, creator.createLedgerVassalsItem(guild));
			i.setItem(13, creator.createLedgerTributesItem(guild));
			i.setItem(14, creator.createLedgerTariffsItem(guild));
		}
		i.setItem(26, inv.createBackButton(SFGUI.LEDGER_VIEW));
		if(open) player.openInventory(i);
	}


	public void guildView(Player player, Guild guild) {
		guildView(player, guild, false);
	}

	public void guildView(Player player, Guild guild, boolean fromFaction) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(
				new SFInventoryHolder(guild.getId(), SFGUI.GUILD_VIEW, 0, fromFaction),
				54,
				"§7Guild View");
		player.openInventory(i);
		guildView(player, guild, i);
	}

    public void guildView(Player player, Guild guild, Inventory i) {
		if(manager == null) setProvinceManager(SimpleFactions.getInstance().getProvinceManager());
		if(guild.hasCapital()) {
			if(guild.getTradeBreakdown().getIncome() == 0) manager.recalculate();
			manager.getIncome(guild);
		}
		manager.recalculateIfNeeded();
		i.clear();
		if(guild.isMember(player)) i.setItem(1, creator.createMenuItem(player, guild, MenuItemType.BANNER_GET));
		i.setItem(10, creator.createMenuItem(player, guild, MenuItemType.BANNER));
		if(guild.isLeader(player) && !guild.isBase()) i.setItem(19, creator.createMenuItem(player, guild, MenuItemType.BANNER_RANDOM));
		i.setItem(11, creator.createMenuItem(player, guild, MenuItemType.LEADER));
		i.setItem(12, creator.createMenuItem(player, guild, MenuItemType.WEALTH));
		i.setItem(15, creator.createMenuItem(player, guild, MenuItemType.MEMBERS));
		if(guild.hasUpgrades()) i.setItem(16, creator.createUpgradesItem(player, guild));
		i.setItem(25, creator.createLoansItem(player, guild));
		Faction target = FactionManager.getMap().getRelocationTarget(player);
		if(target != null && Cache.provincesEnabled) {
			i.setItem(34, creator.createRelocateItem(player, target, guild));
		}
		if(guild.getFaction().isLeader(player.getName()) && !guild.isBase()) {
			if(guild.hasCapital()) {
				i.setItem(43, creator.createElevationItem(player, guild));
			}
			i.setItem(34, creator.createEvictionItem(player, guild));
		}
		int group = 0;
		while(guild.getBranch(group) != null || group > 10) {
			Branch b = guild.getBranch(group);
			group++;
			i.setItem(group+28, creator.createBranchItem(player, guild, b));
			if(guild.isLeader(player)) {
				i.setItem(group+19, creator.createBranchUpgradeItem(player, guild, b));
				i.setItem(group+37, creator.createBranchDowngradeItem(player, guild, b));
			}
		}
		i.setItem(13, creator.createMenuItem(player, guild, MenuItemType.TRADE_BREAKDOWN));
		i.setItem(14, creator.createLedgerItem(player, guild));
		if (!guild.isBase()) {
			i.setItem(17, creator.createDividendItem(player, guild));
		}
		i.setItem(HOST_FACTION_SLOT, creator.createHostFactionItem(guild));
		if (!guild.isBase()) {
			i.setItem(COMPANY_SLOT, inv.companyView.creator.createCompanyEntryItem(guild));
		}
		i.setItem(53, inv.createBackButton(SFGUI.GUILD_VIEW));
	}

	public void upgradeView(Player player, Guild guild) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(guild.getId(), SFGUI.UPGRADE_VIEW), 54, "§7Upgrade View");
		upgradeView(player, guild, i);
		player.openInventory(i);
	}

	public void upgradeView(Player player, Guild guild, Inventory i) {
		i.clear();
		List<Upgrade> upgrades = guild.getUpgrades();
		
		int index = 9;
		for (Upgrade upgrade : upgrades) {
			if (index > 17) break;
			i.setItem(index, creator.createUpgradeItem(player, guild, upgrade));
			
			if (guild.isLeader(player)) {
				i.setItem(index - 9, creator.createUpgradeUpgradeItem(player, guild, upgrade));
				i.setItem(index + 9, creator.createUpgradeDowngradeItem(player, guild, upgrade));
			}
			
			index++;
		}
		
		// Display upgrade queue (3 items at slots 39-41)
		int queueIndex = 0;
		for (var queueItem : guild.getUpgradeQueue()) {
			if (queueIndex >= 3) break;
			i.setItem(39 + queueIndex, creator.createUpgradeQueueItem(queueItem, queueIndex, guild));
			queueIndex++;
		}
		
		i.setItem(53, inv.createBackButton(SFGUI.UPGRADE_VIEW));
	}

	public void click(InventoryClickEvent e, Inventory inventory, Player p) {
		if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
		SFInventoryHolder h = (SFInventoryHolder) inventory.getHolder();
		if (e.getView().getTitle().equalsIgnoreCase("§7Guild List")) {
			e.setCancelled(true);

			// Next page
			if (e.getSlot() == NEXT_PAGE_SLOT) {
				currentPage.put(p, currentPage.getOrDefault(p, 0) + 1);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				guildList(p);
				return;
			}

			// Previous page
			if (e.getSlot() == PREV_PAGE_SLOT) {
				currentPage.put(p, Math.max(0, currentPage.getOrDefault(p, 0) - 1));
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				guildList(p);
				return;
			}

			// Rank toggle
			if (e.getSlot() == 8) {
				RankType r = currentRanking.get(p);
				switch (r) {
					case WEALTH -> currentRanking.put(p, RankType.MEMBERS);
					case MEMBERS -> currentRanking.put(p, RankType.TRADE_POWER);
					case TRADE_POWER -> currentRanking.put(p, RankType.INCOME);
					default -> currentRanking.put(p, RankType.WEALTH);
				}
				currentPage.put(p, 0);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				guildList(p);
				return;
			}

			// Open guild
			ItemStack item = e.getCurrentItem();
			if (item == null || !item.hasItemMeta()) return;

			String id = item.getItemMeta()
				.getPersistentDataContainer()
				.get(new NamespacedKey(SimpleFactions.plugin, "id"),
					PersistentDataType.STRING);

			if (id == null) return;

			Guild guild = FactionManager.getGuildByString(id);
			if (guild != null) {
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				guildView(p, guild);
			}
		}
		if(e.getView().getTitle().equalsIgnoreCase("§7Guild View")) {
			e.setCancelled(true);
			Guild guild = FactionManager.getGuildByString(h.getId());
			if (guild == null) {
				return;
			}
			if (e.getSlot() == HOST_FACTION_SLOT) {
				Faction faction = guild.getFaction();
				if (faction != null) {
					inv.factionView(p, faction);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				}
				return;
			}
			if(e.getSlot() == 19) {
				if(!guild.isLeader(p) || guild.isBase()) {
					p.sendMessage("§cYou must be the leader of a guild to change the banner!");
					return;
				}
				guild.setBannerPatterns(RestServer.fetchBannerList());
				inventory.setItem(10, creator.createMenuItem(p, guild, MenuItemType.BANNER));
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(e.getSlot() == 1) {
				if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
				ItemStack i = new ItemStack(guild.getBanner());
				p.getInventory().addItem(i);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(e.getSlot() == 16) {
				if(!guild.isLeader(p)) return;
				upgradeView(p, guild);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			} else if(e.getSlot() == 34) {
				if(guild.isLeader(p)) {
					if(guild.isBase()) return;
					if(!Cache.requireProvinces(p)) {
						return;
					}
					Faction target = FactionManager.getMap().getRelocationTarget(p);
					if(target == null)  return;
					int province = RestServer.getProvince(p);
					Province prov = SimpleFactions.getInstance().getProvinceManager().get(province);
					if(prov == null || !prov.isValid() || prov.isSea()) return;
					double cost = guild.getRelocationCost(province);
					if(guild.getBank().getWealth() < cost) {	
						p.sendMessage("§cCannot afford to relocate");
						p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
						return;
					}
					if(!target.getId().equalsIgnoreCase(guild.getFaction().getId())) {
						if(RelocationPrompt.begin(p, guild, target, province, true, cost)) {
							return;
						}
						FactionManager.requestRelocation(p, guild, target, province, null);
					} else {
						if(RelocationPrompt.begin(p, guild, target, province, false, cost)) {
							return;
						}
						if(!RelocationPrompt.completeIntraFactionRelocate(
								p, guild, target, province, null, cost)) {
							return;
						}
					}
					guildView(p, guild, inventory);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				} else if(guild.getFaction().isLeader(p.getName())) {
					if(!guild.canBeEvicted(p)) {
						p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
						return;
					}
					double cost = guild.getEvictionCost();
					if(guild.getFaction().getGovernment().getPower() < cost) {
						p.sendMessage("§cCannot afford to evict");
						p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
						return;
					}
					StabilityModifier modifier = new StabilityModifier("Evicted Guild", guild.getStabilityEffect(), 1); // stability effect lasts 6 hours
					guild.getFaction().getGovernment().addStabilityModifier(modifier);
					guild.toLandless(false);
					guild.getFaction().getGovernment().spendPower(cost);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					p.closeInventory();
				}
				return;
			} else if(e.getSlot() == 43) {
				if(!guild.getFaction().isLeader(p.getName())) return;
				if(!guild.canBeElevated(p)) {
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
					return;
				}
				double cost = guild.getElevationCost();
				if(guild.getFaction().getGovernment().getPower() < cost) {
					p.sendMessage("§cCannot afford to elevate");
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
					return;
				}
				guild.getFaction().getGovernment().spendPower(cost);
				FactionManager.requestElevation(p, guild);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				p.closeInventory();
				return;
			} else if(e.getSlot() == COMPANY_SLOT) {
				if(guild.isBase()) return;
				if(guild.getCompany() == null || guild.getCompany().isForming()) {
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
					return;
				}
				inv.companyView(p, guild);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			} else if(e.getSlot() == 25) {
				inv.loanMainView(p, guild);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			} else if(e.getSlot() == 17) {
				if (guild.isBase() || !guild.isLeader(p)) {
					return;
				}
				inv.setChangingDividend(p, guild);
				return;
			}
			ItemStack item = e.getCurrentItem();
			ItemMeta meta = item.getItemMeta();
			String data = meta.getPersistentDataContainer().get(Keys.BRANCH_ID, PersistentDataType.STRING);
			if(data != null ) {
				Boolean upgrade = meta.getPersistentDataContainer().get(Keys.BOOLEAN_FLAG, PersistentDataType.BOOLEAN);
				if(upgrade == null) return;
				if(!guild.isLeader(p)) return;
				Branch b = guild.getBranch(data);
				if(!upgrade) {
					if(b.getLevel() == 0) return;
					guild.getBank().deposit(guild.getRefund());
					b.levelDown();
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					p.sendMessage("§cDowngraded "+b.getName()+ "§c to level §e"+b.getLevel());
				} else {
					double cost = guild.getExpansionCost();
					if(guild.getBank().getWealth() < cost) {
						p.sendMessage("§cCannot afford to upgrade");
						p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
						return;
					}
					b.levelUp();
					p.sendMessage("§aUpgraded "+b.getName()+ "§a to level §e"+b.getLevel());
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					guild.getBank().withdraw(cost);
				}
				manager.recalculateForSingleGuild(guild, true);
				guildView(p, guild, inventory);
			}
		}
		if(h.getType() == SFGUI.UPGRADE_VIEW) {
			e.setCancelled(true);
			Guild guild = FactionManager.getGuildByString(h.getId());
			if (guild == null) return;
			
			ItemStack item = e.getCurrentItem();
			if(item == null || !item.hasItemMeta()) return;
			
			ItemMeta meta = item.getItemMeta();
			String queuePayload = meta.getPersistentDataContainer().get(Keys.QUEUE_CANCEL, PersistentDataType.STRING);
			if (queuePayload != null) {
				if (!guild.isLeader(p)) return;
				inv.openQueueCancelConfirm(p, guild.getFaction(), queuePayload,
						"§eCancel queued upgrade?");
				return;
			}
			String data = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
			if(data != null) {
				Boolean upgrade = meta.getPersistentDataContainer().get(Keys.BOOLEAN_FLAG, PersistentDataType.BOOLEAN);
				if(upgrade == null) return;
				if(!guild.isLeader(p)) return;
				
				Upgrade u = guild.getUpgrade(data);
				if(u == null) return;
				
				if(!upgrade) {
					// Downgrade logic
					if(u.getLevel() == 0) return;
					u.levelDown();
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					p.sendMessage("§cDowngraded " + u.getName() + "§c to level §e" + u.getLevel());
				} else {
					// Upgrade logic - queue the upgrade
					if(guild.getUpgradeQueue().size() == 3) {
						p.sendMessage("§cUpgrade queue is full");
						p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
						return;
					}
					guild.enqueueUpgrade(u);
					p.sendMessage("§eQueued " + u.getName());
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				}
				upgradeView(p, guild, inventory);
			}
		}
	}
}
