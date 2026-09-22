package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryMarket;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

/**
 * The hiring hall: every formed company, best reputation first. Paged after
 * {@code GuildView.populateGuildList}, with a null holder id because the list
 * belongs to no one guild.
 */
public class MercenaryMarketView {
    private static final int INVENTORY_SIZE = 54;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final List<Integer> RESERVED_SLOTS = List.of(
            8, 17, 26, 35, 44, PREV_PAGE_SLOT, NEXT_PAGE_SLOT);

    public static final HashMap<Player, Integer> currentPage = new HashMap<>();

    public InventoryManager inv;

    public MercenaryMarketView(InventoryManager inv) {
        this.inv = inv;
    }

    public void marketList(Player p) {
        marketList(p, null);
    }

    public void marketList(Player p, Inventory inventory) {
        currentPage.putIfAbsent(p, 0);
        boolean open = inventory == null;
        if (open) {
            inventory = SimpleFactions.plugin.getServer().createInventory(
                    new SFInventoryHolder(null, SFGUI.MERCENARY_MARKET_LIST), INVENTORY_SIZE,
                    "§7Mercenary Market");
        }
        populateMarketList(inventory, p);
        if (open) p.openInventory(inventory);
    }

    public void populateMarketList(Inventory inventory, Player p) {
        currentPage.putIfAbsent(p, 0);
        int page = currentPage.get(p);
        List<MercenaryCompany> companies = MercenaryMarket.listing();

        List<Integer> usableSlots = new ArrayList<>();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (!RESERVED_SLOTS.contains(i)) usableSlots.add(i);
        }

        inventory.clear();
        int perPage = usableSlots.size();
        int start = page * perPage;
        int end = Math.min(start + perPage, companies.size());
        for (int i = start; i < end; i++) {
            inventory.setItem(usableSlots.get(i - start), createListItem(p, companies.get(i)));
        }
        if (page > 0) inventory.setItem(PREV_PAGE_SLOT, DefaultCreator.createPreviousPageButton());
        if (end < companies.size()) {
            inventory.setItem(NEXT_PAGE_SLOT, DefaultCreator.createNextPageButton());
        }
    }

    public ItemStack createListItem(Player viewer, MercenaryCompany company) {
        Guild guild = company.getGuild();
        ItemStack banner = guild == null ? null : guild.getBanner();
        ItemStack item = banner == null ? new ItemStack(Material.IRON_SWORD, 1) : banner.clone();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex("#b7aae3" + company.getName()));
        meta.setLore(buildListingLore(viewer, company));
        meta.getPersistentDataContainer().set(
                Keys.STRING_KEY, PersistentDataType.STRING,
                guild == null ? "" : guild.getId());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * States availability for the coming day rather than raw slot count, because a
     * company with every slot promised elsewhere is not for sale today.
     */
    public List<String> buildListingLore(Player viewer, MercenaryCompany company) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Reputation: " + company.getReputationString());
        lore.add("§7Home: §e" + MercenaryMarket.homeSettlement(company));
        lore.add("§7Slots: §e" + company.getFilledSlots() + "§7/§e" + company.getSlots());
        lore.add("§7Free today: §e" + MercenaryMarket.availableToday(company));
        lore.add(" ");
        lore.add("§7Per slot per day: §e"
                + Formatter.formatMoney(company.getSlotUpkeep() / Math.max(1, company.getSlots()))
                + "d §7upkeep");
        lore.add(StringFormatter.formatHex(ContractCreator.BOTH_PRICES_NOTE));
        lore.add(" ");
        if (viewer == null) return lore;
        MercenaryResult canSign = MercenaryMarket.canSign(company, viewer);
        lore.add(StringFormatter.formatHex(
                (canSign.ok() ? "#7ba85f" : "#c74d32") + canSign.message()));
        return lore;
    }

    public void click(InventoryClickEvent e, Inventory inventory, Player p) {
        if (!(inventory.getHolder() instanceof SFInventoryHolder h)) return;
        if (h.getType() != SFGUI.MERCENARY_MARKET_LIST) return;
        e.setCancelled(true);
        if (e.getSlot() == PREV_PAGE_SLOT) {
            currentPage.put(p, Math.max(0, currentPage.getOrDefault(p, 0) - 1));
            marketList(p, inventory);
            chirp(p);
            return;
        }
        if (e.getSlot() == NEXT_PAGE_SLOT) {
            currentPage.put(p, currentPage.getOrDefault(p, 0) + 1);
            marketList(p, inventory);
            chirp(p);
            return;
        }
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String guildId = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.STRING_KEY, PersistentDataType.STRING);
        if (guildId == null || guildId.isEmpty()) return;
        Guild guild = FactionManager.getGuildByString(guildId);
        if (guild == null || guild.getCompany() == null) return;
        inv.contractListView(p, guild);
        chirp(p);
    }

    private static void chirp(Player p) {
        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
    }
}
