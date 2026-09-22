package me.Plugins.SimpleFactions.Managers.Inventory;

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
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;
import me.Plugins.SimpleFactions.mercenary.contract.ContractBook;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryContract;

/**
 * The company's contract ledger and one contract in detail. Accepting and
 * declining live on the detail screen, gated on the viewer being in the hiring
 * faction's government, so a company cannot sign on its customer's behalf.
 */
public class ContractView {
    private static final int DETAIL_SLOT = 13;
    private static final int ACCEPT_BUTTON = 11;
    private static final int DECLINE_BUTTON = 15;
    private static final int DRAFT_BUTTON = 4;

    public InventoryManager inv;
    public ContractCreator creator = new ContractCreator();

    public ContractView(InventoryManager inv) {
        this.inv = inv;
    }

    /* =====================================================
     * List
     * ===================================================== */

    public void listView(Player player, Guild guild) {
        Inventory i = SimpleFactions.plugin.getServer().createInventory(
                new SFInventoryHolder(guild.getId(), SFGUI.CONTRACT_LIST_VIEW), 54,
                "§7Company Contracts");
        listView(player, guild, i);
        player.openInventory(i);
    }

    public void listView(Player player, Guild guild, Inventory i) {
        i.clear();
        MercenaryCompany company = guild.getCompany();
        if (company == null) return;
        int index = 0;
        for (MercenaryContract contract : company.getContractHandler().getAll()) {
            if (index >= 36) break;
            i.setItem(9 + index, creator.createContractItem(contract));
            index++;
        }
        if (company.isLeader(player.getName())) {
            i.setItem(DRAFT_BUTTON, creator.createDraftButton(company));
        }
        i.setItem(53, inv.createBackButton(SFGUI.CONTRACT_LIST_VIEW));
    }

    /* =====================================================
     * Detail
     * ===================================================== */

    public void detailView(Player player, Guild guild, String contractId) {
        Inventory i = SimpleFactions.plugin.getServer().createInventory(
                new SFInventoryHolder(guild.getId(), SFGUI.CONTRACT_DETAIL_VIEW, contractId), 27,
                "§7Contract");
        detailView(player, guild, i, contractId);
        player.openInventory(i);
    }

    public void detailView(Player player, Guild guild, Inventory i, String contractId) {
        i.clear();
        MercenaryCompany company = guild.getCompany();
        if (company == null) return;
        MercenaryContract contract = company.getContractHandler().getById(contractId);
        if (contract == null) return;
        i.setItem(DETAIL_SLOT, creator.createDetailItem(contract));
        if (contract.isOffered() && canSign(player, contract)) {
            i.setItem(ACCEPT_BUTTON, creator.createAcceptButton(contract));
            i.setItem(DECLINE_BUTTON, creator.createDeclineButton());
        }
        i.setItem(26, inv.createBackButton(SFGUI.CONTRACT_DETAIL_VIEW));
    }

    /** Any government member of the hiring faction, so a siege is not a veto. */
    public static boolean canSign(Player player, MercenaryContract contract) {
        Faction hirer = contract.getHirer();
        if (hirer == null || hirer.getGovernment() == null) return false;
        return hirer.getGovernment().isCouncilMember(player.getName());
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
            case CONTRACT_LIST_VIEW -> clickList(e, p, guild, company);
            case CONTRACT_DETAIL_VIEW -> clickDetail(e, inventory, p, guild, company, h.getSecondaryId());
            default -> {
            }
        }
    }

    private void clickList(InventoryClickEvent e, Player p, Guild guild, MercenaryCompany company) {
        if (e.getSlot() == DRAFT_BUTTON) {
            if (!company.isLeader(p.getName())) return;
            p.getInventory().addItem(ContractBook.draftBook(company));
            report(p, MercenaryResult.ok("Draft written. Fill in the terms and sign."));
            return;
        }
        String id = contractId(e.getCurrentItem());
        if (id == null) return;
        detailView(p, guild, id);
        chirp(p);
    }

    private void clickDetail(
            InventoryClickEvent e, Inventory inventory, Player p, Guild guild,
            MercenaryCompany company, String contractId) {
        MercenaryContract contract = company.getContractHandler().getById(contractId);
        if (contract == null || !contract.isOffered()) return;
        if (!canSign(p, contract)) return;
        switch (e.getSlot()) {
            case ACCEPT_BUTTON -> report(p, company.getContractHandler()
                    .accept(contractId, contract.getHirer(), p.getName()));
            case DECLINE_BUTTON -> report(p, company.getContractHandler().decline(contractId));
            default -> {
                return;
            }
        }
        detailView(p, guild, inventory, contractId);
    }

    private static String contractId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(Keys.CONTRACT_ID, PersistentDataType.STRING);
    }

    private static void report(Player p, MercenaryResult result) {
        p.sendMessage((result.ok() ? "§a" : "§c") + result.message());
        p.playSound(p, result.ok() ? Sound.BLOCK_NOTE_BLOCK_BIT : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
    }

    private static void chirp(Player p) {
        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
    }
}
