package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;
import me.Plugins.SimpleFactions.mercenary.contract.ContractBook;
import me.Plugins.SimpleFactions.mercenary.contract.ContractStatus;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryContract;
import me.Plugins.SimpleFactions.mercenary.contract.SlotReservations;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import me.Plugins.TLibs.Utils.TimeFormatter;

public class ContractCreator {
    /** Stated on every contract item, because reading it as either/or is ruinous. */
    public static final String BOTH_PRICES_NOTE =
            "#857e59§oA battle day costs the day price AND the battle price";

    public ItemStack createContractsButton(MercenaryCompany company) {
        ItemStack item = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex("#baa875Contracts"));
        meta.setLore(buildContractsButtonLore(company));
        item.setItemMeta(meta);
        return item;
    }

    public List<String> buildContractsButtonLore(MercenaryCompany company) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Active: §e" + company.getContractHandler().getActive().size());
        lore.add("§7Offered: §e" + company.getContractHandler().getOffered().size());
        long now = System.currentTimeMillis();
        lore.add("§7Free today: §e"
                + SlotReservations.remaining(company, now, now + 86400000L)
                + "§7/§e" + company.getSlots());
        lore.add(" ");
        lore.add(StringFormatter.formatHex("#877e7cUse /company draft to write an offer"));
        return lore;
    }

    public ItemStack createContractItem(MercenaryContract contract) {
        ItemStack item = new ItemStack(material(contract.getStatus()), 1);
        ItemMeta meta = item.getItemMeta();
        Faction hirer = contract.getHirer();
        meta.setDisplayName(StringFormatter.formatHex("#b7aae3"
                + (hirer != null ? hirer.getName() : "Unknown faction")));
        meta.setLore(buildContractLore(contract));
        meta.getPersistentDataContainer()
                .set(Keys.CONTRACT_ID, PersistentDataType.STRING, contract.getId());
        item.setItemMeta(meta);
        return item;
    }

    public List<String> buildContractLore(MercenaryContract contract) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Status: §e" + label(contract.getStatus()));
        lore.add("§7Slots: §e" + contract.getSlots());
        lore.add("§7Per day: §e" + Formatter.formatMoney(contract.getDailyPrice()) + "d");
        lore.add("§7Per battle: §e" + Formatter.formatMoney(contract.getBattlePrice()) + "d");
        lore.add(StringFormatter.formatHex(BOTH_PRICES_NOTE));
        lore.add(" ");
        lore.add("§7Duration: §e" + contract.getDurationDays() + " days");
        if (contract.isOffered()) {
            lore.add("§7Lapses in: §e" + TimeFormatter.formatTime(
                    (int) Math.max(0, (contract.getOfferExpiry() - System.currentTimeMillis()) / 1000)));
        } else if (contract.isActive()) {
            lore.add("§7Days left: §e" + Math.max(0, contract.getDaysRemaining()));
            lore.add("§7Days served: §e" + contract.getDaysServed());
        }
        return lore;
    }

    public ItemStack createDetailItem(MercenaryContract contract) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex("#b7aae3"
                + contract.getKind().getDisplay()));
        meta.setLore(buildDetailLore(contract));
        item.setItemMeta(meta);
        return item;
    }

    public List<String> buildDetailLore(MercenaryContract contract) {
        List<String> lore = new ArrayList<>();
        Faction hirer = contract.getHirer();
        lore.add("§7Hirer: §e" + (hirer != null ? hirer.getName() : "Unknown"));
        lore.add("§7Company: §e" + contract.getCompany().getName());
        lore.add("§7Status: §e" + label(contract.getStatus()));
        lore.add(" ");
        lore.add("§7Slots hired: §e" + contract.getSlots());
        lore.add("§7Per slot per day: §e"
                + Formatter.formatMoney(contract.getPricePerSlotPerDay()) + "d");
        lore.add("§7Per slot per battle: §e"
                + Formatter.formatMoney(contract.getPricePerSlotPerBattle()) + "d");
        lore.add(StringFormatter.formatHex(BOTH_PRICES_NOTE));
        lore.add(" ");
        lore.add("§7Absence refund: §e"
                + Formatter.formatMoney(contract.getAbsenceRefundPerSlotPerBattle())
                + "d §7per slot per battle");
        lore.add("§7Breach refund: §e" + Formatter.formatMoney(contract.getBreachRefund()) + "d");
        lore.add("§7Owed for days served: §e"
                + Formatter.formatMoney(contract.getServedDaysOwed()) + "d");
        lore.add(" ");
        lore.add("§7Signed at reputation: §e" + contract.getReputationAtSigning());
        lore.add("§7Opened: §e" + Cache.getFantasyDate(contract.getIssueDate()));
        lore.add("§7Ends: §e" + Cache.getFantasyDate(contract.getDueDate()));
        lore.add(" ");
        lore.add(StringFormatter.formatHex(
                "#877e7cNames no war: the company serves in all of them"));
        return lore;
    }

    /** The offer's two answers, shown only to the hiring faction's government. */
    public ItemStack createAcceptButton(MercenaryContract contract) {
        ItemStack item = new ItemStack(Material.LIME_DYE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex("#7ba85fAccept the contract"));
        List<String> lore = new ArrayList<>();
        lore.add("§7Cost per day: §e" + Formatter.formatMoney(contract.getDailyPrice()) + "d");
        lore.add("§7Plus per battle: §e" + Formatter.formatMoney(contract.getBattlePrice()) + "d");
        lore.add(" ");
        lore.add(StringFormatter.formatHex("#877e7cAny government member may accept"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createDeclineButton() {
        ItemStack item = new ItemStack(Material.GRAY_DYE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex("#c74d32Decline the offer"));
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#877e7cReleases the slots the offer holds"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createDraftButton(MercenaryCompany company) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex("#baa875Draft a contract"));
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#877e7cEdit the terms page, then sign"));
        lore.add(StringFormatter.formatHex("#877e7c" + ContractBook.minimumsSummary()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static String label(ContractStatus status) {
        return switch (status) {
            case OFFERED -> "Offered";
            case ACTIVE -> "Active";
            case COMPLETED -> "Completed";
            case BREACHED -> "Breached";
            case TERMINATED -> "Terminated";
        };
    }

    private static Material material(ContractStatus status) {
        return switch (status) {
            case OFFERED -> Material.WRITABLE_BOOK;
            case ACTIVE -> Material.WRITTEN_BOOK;
            case COMPLETED -> Material.BOOK;
            case BREACHED -> Material.SHIELD;
            case TERMINATED -> Material.PAPER;
        };
    }
}
