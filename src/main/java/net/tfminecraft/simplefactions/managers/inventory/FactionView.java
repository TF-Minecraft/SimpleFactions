package net.tfminecraft.simplefactions.managers.inventory;

import net.tfminecraft.simplefactions.espionage.EspionageService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.rest.BannerFetcher;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates;
import net.tfminecraft.simplefactions.guild.hub.InfrastructureMenuCopy;
import net.tfminecraft.simplefactions.utils.FactionRanker;
import net.tfminecraft.simplefactions.enums.MenuItemType;
import net.tfminecraft.simplefactions.enums.RankType;
import net.tfminecraft.simplefactions.enums.SFGUI;

public class FactionView {
	public InventoryManager inv;
	
	public FactionCreator creator = new FactionCreator();
	public GuildCreator guildCreator = new GuildCreator();
	
	FactionRanker r = new FactionRanker();
	public static HashMap<Player, RankType> currentRanking = new HashMap<>();
	public static HashMap<Player, Integer> currentPage = new HashMap<>();

	private static final int INVENTORY_SIZE = 54;

	private static final List<Integer> RESERVED_SLOTS = List.of(
		8,   // Rank toggle
		17, 26, 35, 44, // Right-side column
		45,  // Previous page
		53   // Next page
	);

	private static final int PREV_PAGE_SLOT = 45;
	private static final int NEXT_PAGE_SLOT = 53;
	
	public FactionView(InventoryManager inv) {
		this.inv = inv;
	}
	
	public void factionList(Player player) {
		factionList(player, null);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void factionList(Player player, Inventory inv) {
		currentRanking.putIfAbsent(player, RankType.PRESTIGE);
		currentPage.putIfAbsent(player, 0);
		boolean open = inv == null;
		if(open) inv = SimpleFactions.plugin.getServer()
				.createInventory(new SFInventoryHolder(null, SFGUI.FACTION_LIST), INVENTORY_SIZE, "§7Faction List");
		populateFactionList(inv, player);
		if(open) player.openInventory(inv);
	}

	public void populateFactionList(Inventory inv, Player player) {
		currentRanking.putIfAbsent(player, RankType.PRESTIGE);
		currentPage.putIfAbsent(player, 0);
		int page = currentPage.get(player);

		List<Faction> factions = r.getVisibleRankedList(player, currentRanking.get(player));

		List<Integer> usableSlots = new ArrayList<>();
		for (int i = 0; i < INVENTORY_SIZE; i++) {
			if (!RESERVED_SLOTS.contains(i)) {
				usableSlots.add(i);
			}
		}

		inv.clear();
		int factionsPerPage = usableSlots.size();
		int startIndex = page * factionsPerPage;
		int endIndex = Math.min(startIndex + factionsPerPage, factions.size());

		for (int i = startIndex; i < endIndex; i++) {
			Faction f = factions.get(i);
			int slot = usableSlots.get(i - startIndex);
			inv.setItem(slot, creator.createListItem(player, f));
		}

		inv.setItem(8, DefaultCreator.createRankButton(currentRanking.get(player)));

		if (page > 0) {
			inv.setItem(PREV_PAGE_SLOT, DefaultCreator.createPreviousPageButton());
		}
		if (endIndex < factions.size()) {
			inv.setItem(NEXT_PAGE_SLOT, DefaultCreator.createNextPageButton());
		}
	}

	public void factionView(Player player, Faction f) {
		factionView(player, f, null);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void factionView(Player player, Faction f, Inventory i) {
		boolean open = i == null;
		if(open) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.FACTION_VIEW), 54, "§7Faction View");
		if (!EspionageService.canViewExact(player, f)) {
			EspionageView.foreign(i, player, f, inv);
			if (open) player.openInventory(i);
			return;
		}
		i.clear();
		i.setItem(20, EspionageService.isOwn(player, f) ? EspionageView.positionButton() : EspionageView.foreignPositionsItem(player, f));
		if(f.getMembers().contains(player.getName())) i.setItem(1, creator.createMenuItem(player, f, MenuItemType.BANNER_GET));
		i.setItem(10, creator.createMenuItem(player, f, MenuItemType.BANNER));
		if(f.getLeader().equalsIgnoreCase(player.getName())) i.setItem(19, creator.createMenuItem(player, f, MenuItemType.BANNER_RANDOM));
		i.setItem(11, creator.createMenuItem(player, f, MenuItemType.GOVERNMENT));
		i.setItem(12, creator.createMenuItem(player, f, MenuItemType.WEALTH));
		i.setItem(13, creator.createMenuItem(player, f, MenuItemType.PRESTIGE));
		if (Cache.provincesEnabled) {
			i.setItem(21, infrastructureHeadline(f));
		}
		i.setItem(14, creator.createMenuItem(player, f, MenuItemType.MEMBERS));
		i.setItem(23, creator.createMenuItem(player, f, MenuItemType.GUILDS));
		String hubOffers = net.tfminecraft.simplefactions.guild.hub.HubAgreementService.joinSummary(player.getName());
		Faction viewerFaction = FactionManager.getByMember(player.getName());
		if (hubOffers != null && viewerFaction != null && viewerFaction.getId() != null
				&& viewerFaction.getId().equalsIgnoreCase(f.getId())) {
			i.setItem(22, SupplyHubCreator.item(Material.WRITABLE_BOOK, "§eHub offers", List.of(hubOffers, "§eClick to answer")));
		}
		i.setItem(15, guildCreator.createLedgerItem(player, f.getOrCreateMainGuild()));
		i.setItem(16, creator.createMenuItem(player, f, MenuItemType.MODIFIERS));
		i.setItem(25, creator.createMenuItem(player, f, MenuItemType.TAX));
		i.setItem(28, creator.createMenuItem(player, f, MenuItemType.LAWS));
		i.setItem(29, creator.createMenuItem(player, f, MenuItemType.MILITARY));
		if(f.canDissolve() && f.isLeader(player.getName())) i.setItem(30, creator.createDissolveItem(f));
		i.setItem(31, creator.createMenuItem(player, f, MenuItemType.DIPLOMACY));
		i.setItem(32, creator.createMenuItem(player, f, MenuItemType.INSTALLATIONS));
		i.setItem(33, creator.createMenuItem(player, f, MenuItemType.TIER));
		i.setItem(34, creator.createMenuItem(player, f, MenuItemType.TITLES));
		i.setItem(53, this.inv.createBackButton(SFGUI.FACTION_VIEW));
		if(open) player.openInventory(i);
	}

	@SuppressWarnings("deprecation")
	private ItemStack infrastructureHeadline(Faction faction) {
		ItemStack item = new ItemStack(Material.RAIL);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("§6Infrastructure");
		String line = HubEstimates.hasInfrastructureWorth(faction)
				? InfrastructureMenuCopy.headline(HubEstimates.infrastructureWorth(faction))
				: InfrastructureMenuCopy.headlineUnknown();
		meta.setLore(List.of(line));
		item.setItemMeta(meta);
		return item;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void factionGuildsView(Player player, Faction f, Inventory i) {
		boolean open = i == null;
		int page = 0;
		if (i != null && i.getHolder() instanceof SFInventoryHolder existing) {
			page = existing.getPage();
		}
		if (open) {
			i = SimpleFactions.plugin.getServer().createInventory(
					new SFInventoryHolder(f.getId(), SFGUI.FACTION_GUILDS, page),
					INVENTORY_SIZE,
					"§7Faction Guilds");
		}
		populateFactionGuilds(i, player, f, page);
		if (open) {
			player.openInventory(i);
		}
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void factionGuildsView(Player player, Faction f, int page) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(
				new SFInventoryHolder(f.getId(), SFGUI.FACTION_GUILDS, page),
				INVENTORY_SIZE,
				"§7Faction Guilds");
		populateFactionGuilds(i, player, f, page);
		player.openInventory(i);
	}

	public void populateFactionGuilds(Inventory inv, Player player, Faction f, int page) {
		List<Guild> guilds = new ArrayList<>(f.getGuildHandler().getGuilds());
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
			inv.setItem(usableSlots.get(i - start), guildCreator.createListItem(player, guilds.get(i)));
		}
		if (page > 0) {
			inv.setItem(PREV_PAGE_SLOT, DefaultCreator.createPreviousPageButton());
		}
		if (end < guilds.size()) {
			inv.setItem(NEXT_PAGE_SLOT, DefaultCreator.createNextPageButton());
		}
		inv.setItem(8, this.inv.createBackButton(SFGUI.FACTION_GUILDS));
	}

	// Keep blocking the legacy hotbar action when integrations or older API snapshots emit it.
	@SuppressWarnings("removal")
	public void clickPreventions(InventoryClickEvent e, Inventory inventory, Player p) {
		if(!(inventory != null && inventory.getHolder() instanceof SFInventoryHolder)) return;
		if (e.getClickedInventory() == null) return;

		if (e.getView().getTopInventory().equals(e.getClickedInventory())) {
			e.setCancelled(true);
		}
		if (e.getAction() == InventoryAction.HOTBAR_SWAP
			|| e.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
			e.setCancelled(true);
			return;
		}
		if (e.getClick().isShiftClick()) {
			e.setCancelled(true);
		}
	}
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void click(InventoryClickEvent e, Inventory inventory, Player p) {
		clickPreventions(e, inventory, p);
		if(e.getView().getTitle().equalsIgnoreCase("§7Faction List")) {
			e.setCancelled(true);
			// Next page
			if (e.getSlot() == NEXT_PAGE_SLOT) {
				currentPage.put(p, currentPage.getOrDefault(p, 0) + 1);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				factionList(p);
				return;
			}

			// Previous page
			if (e.getSlot() == PREV_PAGE_SLOT) {
				currentPage.put(p, Math.max(0, currentPage.getOrDefault(p, 0) - 1));
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				factionList(p);
				return;
			}
			if(e.getSlot() == 8) {
				RankType ranking = currentRanking.getOrDefault(p, RankType.PRESTIGE);
				currentRanking.put(p, ranking == RankType.PRESTIGE ? RankType.WEALTH
						: ranking == RankType.WEALTH ? RankType.MEMBERS : RankType.PRESTIGE);
				currentPage.put(p, 0);
				factionList(p);
				return;
			} else{
				ItemStack i = e.getCurrentItem();
				if(i == null) return;
				ItemMeta m = i.getItemMeta();
				NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
				String factionId = m.getPersistentDataContainer().get(id, PersistentDataType.STRING);
				if(factionId == null) return;
				Faction f = FactionManager.getByString(factionId);
				if(f != null) {
					factionView(p, f);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				}
			}
		} else if(e.getView().getTitle().equalsIgnoreCase("§7Faction View")) {
			e.setCancelled(true);
			if (!(inventory.getHolder() instanceof SFInventoryHolder holder)) return;
			Faction viewed = FactionManager.getByString(holder.getId());
			if (viewed == null) return;
			if (!EspionageService.canViewExact(p, viewed)
					&& e.getSlot() != 23 && e.getSlot() != 31 && e.getSlot() != 53 && e.getSlot() != 15 && e.getSlot() != 20) {
                switch (e.getSlot()) {
                    case 11 -> inv.governmentView(p, viewed, null);
                    case 25 -> inv.taxView(p, viewed);
                    case 28 -> inv.lawView(p, viewed, null);
                    case 29 -> inv.militaryView(null, p, viewed, true);
                    case 32 -> inv.installationsView(null, p, viewed, true);
                    default -> { }
                }
                return;
            }
			if (e.getSlot() == 22) {
				HubProposalMenu.openOffers(p);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			}
			if (e.getSlot() == 20) {
				EspionageView.positions(p, viewed, inv);
				return;
			}
			if(e.getSlot() == 28) {
				ItemStack item = e.getCurrentItem();
				ItemMeta m = item.getItemMeta();
				NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
				String factionId = m.getPersistentDataContainer().get(id, PersistentDataType.STRING);
				if(factionId == null) return;
				Faction f = FactionManager.getByString(factionId);
				if(f != null) {
					inv.lawView(p, f, null);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				}
			} else if(e.getSlot() == 15) {
				if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
				Faction f = FactionManager.getByString(((SFInventoryHolder) inventory.getHolder()).getId());
				if(f == null) return;
				inv.ledgerView(p, f.getOrCreateMainGuild(), null);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(e.getSlot() == 29) {
				ItemStack item = e.getCurrentItem();
				ItemMeta m = item.getItemMeta();
				NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
				String factionId = m.getPersistentDataContainer().get(id, PersistentDataType.STRING);
				if(factionId == null) return;
				Faction f = FactionManager.getByString(factionId);
				if(f != null) {
					inv.militaryView(null, p, f, true);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				}
			} else if(e.getSlot() == 30) {
				ItemStack item = e.getCurrentItem();
				ItemMeta m = item.getItemMeta();
				NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
				String factionId = m.getPersistentDataContainer().get(id, PersistentDataType.STRING);
				if(factionId == null) return;
				Faction f = FactionManager.getByString(factionId);
				if(f != null) {
					inv.confirming.put(p, f);
					inv.confirmView(p, f, "dissolve", "true");
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					return;
				}
			} else if(e.getSlot() == 31) {
				ItemStack item = e.getCurrentItem();
				ItemMeta m = item.getItemMeta();
				NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
				String factionId = m.getPersistentDataContainer().get(id, PersistentDataType.STRING);
				if(factionId == null) return;
				Faction f = FactionManager.getByString(factionId);
				if(f != null) {
					inv.diplomacyListView(null, p, f, true);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				}
			} else if(e.getSlot() == 32) {
				ItemStack item = e.getCurrentItem();
				ItemMeta m = item.getItemMeta();
				NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
				String factionId = m.getPersistentDataContainer().get(id, PersistentDataType.STRING);
				if(factionId == null) return;
				Faction f = FactionManager.getByString(factionId);
				if(f != null) {
					inv.installationsView(null, p, f, true);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				}
			} else if(e.getSlot() == 11) {
				ItemStack item = e.getCurrentItem();
				ItemMeta m = item.getItemMeta();
				NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
				String factionId = m.getPersistentDataContainer().get(id, PersistentDataType.STRING);
				if(factionId == null) return;
				Faction f = FactionManager.getByString(factionId);
				if(f != null) {
					inv.governmentView(p, f, null);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				}
			} else if(e.getSlot() == 33) {
				ItemStack item = e.getCurrentItem();
				ItemMeta m = item.getItemMeta();
				NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
				String factionId = m.getPersistentDataContainer().get(id, PersistentDataType.STRING);
				if(factionId == null) return;
				Faction f = FactionManager.getByString(factionId);
				if(f != null && f.getLeader().equalsIgnoreCase(p.getName())) {
					inv.tierView(null, p, f, true);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				}
			} else if(e.getSlot() == 34) {
				ItemStack item = e.getCurrentItem();
				ItemMeta m = item.getItemMeta();
				NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
				String factionId = m.getPersistentDataContainer().get(id, PersistentDataType.STRING);
				if(factionId == null) return;
				Faction f = FactionManager.getByString(factionId);
				if(f != null) {
					Faction pf = FactionManager.getByLeader(p.getName());
					if(pf == null) return;
					String overlord = RelationManager.getOverlord(f);
					if(f.getLeader().equalsIgnoreCase(p.getName()) || (overlord != null && overlord.equalsIgnoreCase(pf.getId()))) {
						inv.titleView(null, p, f, true);
						p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					}
				}
			} else if(e.getSlot() == 19) {
				if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
				SFInventoryHolder h = (SFInventoryHolder) inventory.getHolder();
				Faction f = FactionManager.getByString(h.getId());
				if(!f.isLeader(p.getName())) return;
				BannerFetcher.fetch("faction:" + f.getId(), patterns -> {
					if (patterns == null) {
						if (p.isOnline()) p.sendMessage("§cCould not generate a banner right now. Try again later.");
						return;
					}
					f.setBannerPatterns(patterns);
					BannerFetcher.refreshOpenView(p, SFGUI.FACTION_VIEW, f.getId(),
							() -> creator.createMenuItem(p, f, MenuItemType.BANNER));
					if (p.isOnline()) p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				});
			} else if(e.getSlot() == 1) {
				if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
				SFInventoryHolder h = (SFInventoryHolder) inventory.getHolder();
				Faction f = FactionManager.getByString(h.getId());
				ItemStack i = new ItemStack(f.getBanner());
				p.getInventory().addItem(i);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(e.getSlot() == 25) {
				if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
				SFInventoryHolder h = (SFInventoryHolder) inventory.getHolder();
				Faction f = FactionManager.getByString(h.getId());
				inv.taxView(p, f);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(e.getSlot() == 23) {
				ItemStack item = e.getCurrentItem();
				if (item == null || !item.hasItemMeta()) return;
				ItemMeta m = item.getItemMeta();
				NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
				String factionId = m.getPersistentDataContainer().get(id, PersistentDataType.STRING);
				if(factionId == null) return;
				Faction f = FactionManager.getByString(factionId);
				if(f != null) {
					factionGuildsView(p, f, null);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				}
			}
		} else if (inventory.getHolder() instanceof SFInventoryHolder holder
				&& holder.getType() == SFGUI.FACTION_GUILDS) {
			e.setCancelled(true);
			Faction f = FactionManager.getByString(holder.getId());
			if (f == null) {
				return;
			}
			if (e.getSlot() == NEXT_PAGE_SLOT) {
				factionGuildsView(p, f, holder.getPage() + 1);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			}
			if (e.getSlot() == PREV_PAGE_SLOT) {
				factionGuildsView(p, f, Math.max(0, holder.getPage() - 1));
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			}
			ItemStack item = e.getCurrentItem();
			if (item == null || !item.hasItemMeta()) {
				return;
			}
			String id = item.getItemMeta()
					.getPersistentDataContainer()
					.get(new NamespacedKey(SimpleFactions.plugin, "id"), PersistentDataType.STRING);
			if (id == null) {
				return;
			}
			Guild guild = FactionManager.getGuildByString(id);
			if (guild != null) {
				inv.guildView.guildView(p, guild, true);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			}
		}
	}
}
