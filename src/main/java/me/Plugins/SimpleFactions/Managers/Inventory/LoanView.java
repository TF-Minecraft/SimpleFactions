package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.loans.CreditCalculator;
import me.Plugins.SimpleFactions.Guild.loans.Loan;
import me.Plugins.SimpleFactions.Guild.loans.LoanBook;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class LoanView {
    public InventoryManager inv;
    public LoanCreator creator = new LoanCreator();

    public LoanView(InventoryManager inv) {
        this.inv = inv;
    }

    public void loanMainView(Player player, Guild guild) {
        loanMainView(player, guild, null);
    }

    public void loanMainView(Player player, Guild guild, Inventory i) {
        boolean open = i == null;
        if(open) i = SimpleFactions.plugin.getServer().createInventory(
            new SFInventoryHolder(guild.getId(), SFGUI.LOAN_MAIN_VIEW),
            9,
            "§7Loans - " + guild.getName()
        );
        i.clear();
        i.setItem(2, creator.createLoansGivenButton(guild));
        i.setItem(4, creator.createLoansTakenButton(guild));
        if(guild.isLeader(player)) i.setItem(6, creator.createIssueNewLoanButton());
        i.setItem(8, inv.createBackButton(SFGUI.LOAN_MAIN_VIEW));
        if(open) player.openInventory(i);
    }

    public void loansGivenView(Player player, Guild guild) {
        loansGivenView(player, guild, null);
    }

    public void loansGivenView(Player player, Guild guild, Inventory i) {
        boolean open = i == null;
        if(open) i = SimpleFactions.plugin.getServer().createInventory(
            new SFInventoryHolder(guild.getId(), SFGUI.LOANS_GIVEN_VIEW),
            54,
            "§7Loans Given - " + guild.getName()
        );
        i.clear();
        List<Loan> loansGiven = guild.getLoanHandler().getLoansGiven();
        int slot = 0;
        for (Loan loan : loansGiven) {
            if (slot >= 45) break;
            i.setItem(slot, creator.createLoanItem(loan, true));
            slot++;
        }
        i.setItem(53, inv.createBackButton(SFGUI.LOANS_GIVEN_VIEW));
        if(open) player.openInventory(i);
    }

    public void loansTakenView(Player player, Guild guild) {
        loansTakenView(player, guild, null);
    }

    public void loansTakenView(Player player, Guild guild, Inventory i) {
        boolean open = i == null;
        if(open) i = SimpleFactions.plugin.getServer().createInventory(
            new SFInventoryHolder(guild.getId(), SFGUI.LOANS_TAKEN_VIEW),
            54,
            "§7Loans Taken - " + guild.getName()
        );
        i.clear();
        List<Loan> loansTaken = guild.getLoanHandler().getLoansTaken();
        int slot = 0;
        for (Loan loan : loansTaken) {
            if (slot >= 45) break;
            i.setItem(slot, creator.createLoanItem(loan, false));
            slot++;
        }
        i.setItem(53, inv.createBackButton(SFGUI.LOANS_TAKEN_VIEW));
        if(open) player.openInventory(i);
    }

    public void loanDetailView(Player player, Guild guild, Loan loan, boolean isTaken) {
        loanDetailView(player, guild, loan, isTaken, null);
    }

    public void loanDetailView(Player player, Guild guild, Loan loan, boolean isTaken, Inventory i) {
        boolean open = i == null;
        if(open) i = SimpleFactions.plugin.getServer().createInventory(
            new SFInventoryHolder(guild.getId(), isTaken ? SFGUI.TAKEN_LOAN_DETAIL_VIEW : SFGUI.ISSUED_LOAN_DETAIL_VIEW, loan.getId()),
            27,
            "§7Loan Details"
        );
        i.clear();
        i.setItem(15, creator.createLoanItem(loan, !isTaken));
        if(!loan.hasDefaulted() && !loan.isPaidOff()) {
            i.setItem(12, creator.createToggleAutoPayButton(loan, isTaken));
        } else {
            i.setItem(12, creator.createToggleAutoPayButton(loan, false));
        }
        if(isTaken) {
            if(!loan.isPaidOff() && !loan.hasDefaulted()) i.setItem(11, creator.createPayOffLoanButton(loan));
            if(!loan.isPaidOff()) i.setItem(14, creator.createDefaultItem(loan));
        } else {
            i.setItem(13, creator.createPauseInterestItem(loan));
            i.setItem(14, creator.createForgiveLoanItem(loan));
        }
        i.setItem(26, inv.createBackButton(isTaken ? SFGUI.LOANS_TAKEN_VIEW : SFGUI.LOANS_GIVEN_VIEW));
        if(open) player.openInventory(i);
    }

    public void click(InventoryClickEvent e, Inventory inventory, Player p) {
        if(!(inventory.getHolder() instanceof SFInventoryHolder)) return;
        SFInventoryHolder h = (SFInventoryHolder) inventory.getHolder();
        Guild guild = FactionManager.getGuildByString(h.getId());
        
        if(h.getType() == SFGUI.LOAN_MAIN_VIEW) {
            e.setCancelled(true);
            
            // Loans Given button
            if(e.getSlot() == 2) {
                loansGivenView(p, guild);
                p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }
            // Loans Taken button
            else if(e.getSlot() == 4) {
                loansTakenView(p, guild);
                p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }
            // Issue New Loan button
            else if(e.getSlot() == 6) {
                if(!p.getInventory().getItemInMainHand().getType().equals(Material.WRITABLE_BOOK)) {
                    p.sendMessage("§cYou must have a book and quill in your hand to issue a new loan!");
                    return;
                }
                p.getInventory().setItemInMainHand(LoanBook.getBaseBook(guild));
                p.closeInventory();
                p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }
        }
        else if(h.getType() == SFGUI.LOANS_GIVEN_VIEW) {
            e.setCancelled(true);
            
            // Click on a loan to view details
            ItemMeta meta = e.getCurrentItem().getItemMeta();
            if(meta.getPersistentDataContainer().has(Keys.STRING_KEY, PersistentDataType.STRING)) {
                String id = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
                Loan loan = guild.getLoanHandler().getLoanById(id);
                if(loan == null) return;
                loanDetailView(p, guild, loan, false);
                p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
            }
        }
        else if(h.getType() == SFGUI.LOANS_TAKEN_VIEW) {
            e.setCancelled(true);
            
            // Click on a loan to view details
            if(e.getCurrentItem() != null && e.getCurrentItem().hasItemMeta()) {
                ItemMeta meta = e.getCurrentItem().getItemMeta();
                if(meta.getPersistentDataContainer().has(Keys.STRING_KEY, PersistentDataType.STRING)) {
                    String id = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
                    String gid = meta.getPersistentDataContainer().get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING);
                    Guild issuer = FactionManager.getGuildByString(gid);
                    Loan loan = issuer.getLoanHandler().getLoanById(id);
                    if(loan == null) return;
                    loanDetailView(p, guild, loan, true);
                    p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                }
            }
        }
        else if(h.getType() == SFGUI.TAKEN_LOAN_DETAIL_VIEW) {
            e.setCancelled(true);
            // Pay off loan button
            if(e.getSlot() == 11) {
                // Find the loan by looking at the detail item
                ItemStack detailItem = e.getCurrentItem();
                if(detailItem != null && detailItem.hasItemMeta()) {
                    String id = detailItem.getItemMeta().getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
                    String gid = detailItem.getItemMeta().getPersistentDataContainer().get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING);
                    Guild issuer = FactionManager.getGuildByString(gid);
                    Loan loan = issuer.getLoanHandler().getLoanById(id);
                    if(loan == null) return;
                    inv.setPayingLoan(p, loan);
                    p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                    p.closeInventory();
                    p.sendTitle("", StringFormatter.formatHex("#d6cf69Enter the amount in chat."), 5, 80, 5);;
                }
            } else if(e.getSlot() == 12) {
                // Toggle auto-pay
                ItemStack detailItem = e.getCurrentItem();
                if(detailItem != null && detailItem.hasItemMeta()) {
                    String id = detailItem.getItemMeta().getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
                    String gid = detailItem.getItemMeta().getPersistentDataContainer().get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING);
                    Guild issuer = FactionManager.getGuildByString(gid);
                    Loan loan = issuer.getLoanHandler().getLoanById(id);
                    if(loan == null) return;
                    if(loan.hasDefaulted() || loan.isPaidOff()) return;
                    loan.setAutoPay(!loan.isAutoPay());
                    // Refresh the loan detail view
                    loanDetailView(p, guild, loan, true);
                    p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                }
            } else if(e.getSlot() == 14) {
                // Toggle defaulted
                ItemStack detailItem = e.getCurrentItem();
                if(detailItem != null && detailItem.hasItemMeta()) {
                    String id = detailItem.getItemMeta().getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
                    String gid = detailItem.getItemMeta().getPersistentDataContainer().get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING);
                    Guild issuer = FactionManager.getGuildByString(gid);
                    Loan loan = issuer.getLoanHandler().getLoanById(id);
                    if(loan == null) return;
                    loan.setDefaulted(!loan.hasDefaulted());
                    if(loan.hasDefaulted()) {
                        loan.setAutoPay(false);
                    }
                    // Refresh the loan detail view
                    loanDetailView(p, guild, loan, true);
                    p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                }
            }
        } else if(h.getType() == SFGUI.ISSUED_LOAN_DETAIL_VIEW) {
            e.setCancelled(true);
            // Pay off loan button
            if(e.getSlot() == 13) {
                // Toggle interest
                ItemStack detailItem = e.getCurrentItem();
                if(detailItem != null && detailItem.hasItemMeta()) {
                    String id = detailItem.getItemMeta().getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
                    String gid = detailItem.getItemMeta().getPersistentDataContainer().get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING);
                    Guild issuer = FactionManager.getGuildByString(gid);
                    Loan loan = issuer.getLoanHandler().getLoanById(id);
                    if(loan == null) return;
                    loan.setPausedInterest(!loan.isInterestPaused());
                    loanDetailView(p, guild, loan, false);
                    p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                }
            } else if(e.getSlot() == 14) {
                // Forgive/settle loan
                ItemStack detailItem = e.getCurrentItem();
                if(detailItem != null && detailItem.hasItemMeta()) {
                    String id = detailItem.getItemMeta().getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
                    String gid = detailItem.getItemMeta().getPersistentDataContainer().get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING);
                    Guild issuer = FactionManager.getGuildByString(gid);
                    Loan loan = issuer.getLoanHandler().getLoanById(id);
                    if(loan == null) return;
                    issuer.getLoanHandler().removeLoan(loan.getId());
                    // Refresh the loan detail view
                    loansGivenView(p, guild);
                    p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                }
            }
        }
    }
}
