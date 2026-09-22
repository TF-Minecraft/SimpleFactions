package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.loans.CreditCalculator;
import me.Plugins.SimpleFactions.Guild.loans.Loan;
import me.Plugins.SimpleFactions.Guild.loans.LoanStatus;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LoanCreator {
    private static final String CHECK = "✔";
	private static final String CROSS = "✖";

	private static final String GREEN = "#87d65c";
	private static final String RED   = "#d65c5c";
	private static final String GRAY  = "#6f776a";
	private static final String LIGHT_GRAY  = "#9cb68c";

    public ItemStack createLoansGivenButton(Guild guild) {
        ItemStack i = IconGetter.getIconOrDefault("loans_given", Material.BLACK_DYE);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex("#7ad65eLoans Given"));
        
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#7a706aView all loans you have issued"));
        lore.add("");
        lore.add(StringFormatter.formatHex("#d6cf69Total Lent: #ccbb76" + 
            String.format("%.2f", guild.getLoanHandler().getTotalLent()) + "d"));
        lore.add("");
        lore.add(StringFormatter.formatHex("#50e846Click to View"));
        
        meta.setLore(lore);
        i.setItemMeta(meta);
        return i;
    }

    public ItemStack createLoansTakenButton(Guild guild) {
        ItemStack i = IconGetter.getIconOrDefault("loans_taken", Material.BLACK_DYE);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex("#d45b48Loans Taken"));
        
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#7a706aView all loans you have borrowed"));
        lore.add("");
        lore.add(StringFormatter.formatHex("#d6cf69Total Owed: #ccbb76" + 
            Formatter.formatDouble(guild.getLoanHandler().getTotalOwed()) + "d"));
        lore.add(StringFormatter.formatHex("#d6cf69Daily Interest: #ccbb76" + 
            Formatter.formatDouble(guild.getLoanHandler().getDailyInterestChange()) + "d"));
        lore.add("");
        lore.add(StringFormatter.formatHex("#50e846Click to View"));
        
        meta.setLore(lore);
        i.setItemMeta(meta);
        return i;
    }

    public ItemStack createIssueNewLoanButton() {
        ItemStack i = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex("#d6cf69Issue New Loan"));
        
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#7a706a§oCreate a new loan agreement"));
        
        meta.setLore(lore);
        i.setItemMeta(meta);
        return i;
    }

    public ItemStack createLoanItem(Loan loan, boolean asIssuerView) {

        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();

        // Determine perspective
        String otherPartyName = asIssuerView
                ? loan.getBorrower().getName()
                : loan.getIssuer().getName();

        String titlePrefix = asIssuerView ? "Loan to " : "Loan from ";
        String partyLabel = asIssuerView ? "Borrower: " : "Issuer: ";

        meta.setDisplayName(StringFormatter.formatHex(
                (asIssuerView ? "#7ad65e" : "#d45b48") + titlePrefix + otherPartyName
        ));

        List<String> lore = new ArrayList<>();

        lore.add(StringFormatter.formatHex("#7a706a" + partyLabel + "#c2bea7" + otherPartyName));

        // Status
        if (loan.hasDefaulted()) {
            lore.add(StringFormatter.formatHex("#d65c5c§l§oThis loan is in default"));
        } else if (loan.isPaidOff()) {
            lore.add(StringFormatter.formatHex("#87d65c§l§oThis loan is paid off"));
        } else if (loan.isOverdue()) {
            lore.add(StringFormatter.formatHex("#d65c5c§l§oThis loan is overdue"));
        }

        lore.add("");

        // Financials
        lore.add(StringFormatter.formatHex("#d6cf69Original Amount: #ccbb76"
                + Formatter.formatDouble(loan.getAmount()) + "d"));

        lore.add(StringFormatter.formatHex("#d6cf69Amount Paid: #4fd945"
                + Formatter.formatDouble(loan.getPaid()) + "d"));

        lore.add(StringFormatter.formatHex("#d6cf69Amount Owed: #cf493a"
                + Formatter.formatDouble(loan.getTotalOwed()) + "d"));

        if (loan.getUnpaidInterest() > 0) {
            lore.add(StringFormatter.formatHex("#5a5a53(#cf493a"
                    + Formatter.formatDouble(loan.getUnpaidInterest())
                    + "d #5a5a53in unpaid interest)"));
        }

        // Daily penalty (calculate once!)
        int dailyPenalty = CreditCalculator.calculateDailyOverduePenalty(loan);
        if (dailyPenalty < 0) {
            lore.add(StringFormatter.formatHex("#d65c5cDue to being overdue and not in default:"));
            lore.add(StringFormatter.formatHex("#d65c5cDaily Penalty: #b51717"
                    + dailyPenalty + " Credit Score"));
            lore.add(StringFormatter.formatHex("#454343(Currently "
                    + loan.getBorrower().getLoanHandler().getCreditScoreString()
                    + "#454343)"));
        }

        lore.add("");

        // Interest
        lore.add(StringFormatter.formatHex("#d6cf69Weekly Interest Rate: #ccbb76"
                + Formatter.formatDouble(loan.getInterestRate()) + "%"));

        lore.add(StringFormatter.formatHex("#d6cf69Daily Interest: #ccbb76"
                + Formatter.formatDouble(loan.getDailyInterestChange()) + "d"));

        lore.add(StringFormatter.formatHex("#d6cf69Automatic Payments: "
                + (loan.isAutoPay() ? (GREEN + CHECK) : (RED + CROSS))));

        lore.add("");

        // Dates
        lore.add(StringFormatter.formatHex("#d1bf92Issue Date: #d4c9ae"
                + formatDate(loan.getIssueDate())));

        lore.add(StringFormatter.formatHex("#d1bf92Due Date: #d4c9ae"
                + formatDate(loan.getDueDate())));

        // Persistent data
        meta.getPersistentDataContainer().set(
                Keys.STRING_KEY,
                PersistentDataType.STRING,
                loan.getId()
        );

        if (!asIssuerView) {
            meta.getPersistentDataContainer().set(
                    Keys.SECONDARY_STRING_KEY,
                    PersistentDataType.STRING,
                    loan.getIssuer().getId()
            );
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM")
                    .withZone(ZoneId.systemDefault());

    private String formatDate(long timestamp) {
        return DATE_FORMAT.format(Instant.ofEpochMilli(timestamp))
                + "/" + Cache.getFantasyYear(timestamp);
    }

    public ItemStack createPayOffLoanButton(Loan loan) {
        ItemStack i = TLibs.getItemAPI().getCreator().getItemFromPath("m.currency.pouch_of_coins");
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex("#87d65cPay Off Loan"));
        
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#7a706a§oMake a payment on this loan"));
        lore.add(StringFormatter.formatHex("#d6cf69Total Owed: #ccbb76" + 
            Formatter.formatDouble(loan.getTotalOwed()) + "d"));
        if(loan.getStatus() != LoanStatus.PAID_OFF) {
            lore.add("");
            int bonus = CreditCalculator.calculatePayoffBonus(loan);
            if(bonus > 0) {
                lore.add(StringFormatter.formatHex("#87d65cPaying off this loan now would give "));
                lore.add(StringFormatter.formatHex("#87d65cyou a credit score bonus of #19be2a" + bonus));
                lore.add(StringFormatter.formatHex("#454343(Currently "+loan.getBorrower().getLoanHandler().getCreditScoreString()+"#454343)"));
            } else if(bonus < 0) {
                lore.add(StringFormatter.formatHex("#87d65cPaying off this loan now would give "));
                lore.add(StringFormatter.formatHex("#87d65cyou a credit score penalty of #b51717" + bonus));
                lore.add(StringFormatter.formatHex("#454343(Currently "+loan.getBorrower().getLoanHandler().getCreditScoreString()+"#454343)"));
            } else {
                lore.add(StringFormatter.formatHex("#6f776aPaying off this loan now would"));
                lore.add(StringFormatter.formatHex("#6f776ahave no effect on your credit score"));
            }
        }
        lore.add("");
        lore.add(StringFormatter.formatHex("#50e846Click to Pay"));
        meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, loan.getId());
        meta.getPersistentDataContainer().set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, loan.getIssuer().getId());
        meta.setLore(lore);
        i.setItemMeta(meta);
        return i;
    }

    public ItemStack createToggleAutoPayButton(Loan loan, boolean button) {
        ItemStack i = TLibs.getItemAPI().getCreator().getItemsAdderItem(loan.isAutoPay() ? "mcicons:icon_confirm" : "mcicons:icon_cancel");
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex(button ? "#87d65cToggle Automatic Payments" : "#87d65cAutomatic Payments"));
        
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#ccc396Automatic Payments are currently " + (loan.isAutoPay() ? (GREEN + "ON") : (RED + "OFF"))));
        if(button) {
            lore.add(StringFormatter.formatHex("#7a706a§oToggle automatic daily payments for this loan"));
            lore.add("");
            lore.add(StringFormatter.formatHex("#50e846Click to Toggle"));
        }
        meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, loan.getId());
        meta.getPersistentDataContainer().set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, loan.getIssuer().getId());
        meta.setLore(lore);
        i.setItemMeta(meta);
        return i;
    }

    public ItemStack createDefaultItem(Loan loan) {
        ItemStack i = TLibs.getItemAPI().getCreator().getItemsAdderItem("iasurvival:sword_skin_bloodnite");
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex(loan.hasDefaulted() ? "#81e072Resume Payments" : "#a94141Default Loan"));
        
        List<String> lore = new ArrayList<>();
        if(loan.hasDefaulted()) {
            lore.add(StringFormatter.formatHex("#ccc396This loan is currently in default"));
            lore.add(StringFormatter.formatHex("#ccc396Clicking this will allow you to resume payments"));
            lore.add(StringFormatter.formatHex("#ccc396However, the credit score hit from defaulting"));
            lore.add(StringFormatter.formatHex("#ccc396will still apply."));
            lore.add("");
            lore.add(StringFormatter.formatHex("#454343(Current Credit Score "+loan.getBorrower().getLoanHandler().getCreditScoreString()+"#454343)"));
        } else {
            lore.add(StringFormatter.formatHex("#ccc396Defaulting is saying you will not pay this loan"));
            lore.add(StringFormatter.formatHex("#ccc396You can choose to restart payments later, however"));
            lore.add(StringFormatter.formatHex("#ccc396the credit score hit is permanent."));
            lore.add("");
            lore.add(StringFormatter.formatHex("#7a706aDefaulting on a loan has consequences"));
            lore.add(StringFormatter.formatHex("#7a706aThe issuer may take action to recover the owed amount"));
            lore.add(StringFormatter.formatHex("#7a706aYour credit score will suffer"));
            lore.add("");
            lore.add(StringFormatter.formatHex("#a94141Defaulting is usually the worst option"));
            lore.add(StringFormatter.formatHex("#a94141to handle a loan you can't pay."));
            lore.add(StringFormatter.formatHex("#a94141INTEREST WILL CONTINUE TO ACCRUE WHILE IN DEFAULT"));
            lore.add(StringFormatter.formatHex("#454343(Unless the issuer pauses interest)"));
            if(loan.getStatus() != LoanStatus.DEFAULTED) {
                lore.add("");
                int penalty = CreditCalculator.calculateDefaultPenalty(loan);
                if(penalty < 0) {
                    lore.add(StringFormatter.formatHex("#d88e8eCredit Score Penalty: #b51717" + (-penalty)));
                    lore.add(StringFormatter.formatHex("#454343(Currently "+loan.getBorrower().getLoanHandler().getCreditScoreString()+"#454343)"));
                } else {
                    lore.add(StringFormatter.formatHex("#6f776aDefaulting on this loan now would"));
                    lore.add(StringFormatter.formatHex("#6f776ahave no effect on your credit score"));
                }
            } else if(loan.getStatus() == LoanStatus.DEFAULTED) {
                lore.add("");
                lore.add(StringFormatter.formatHex("#921f1fSince you already defaulted before"));
                lore.add(StringFormatter.formatHex("#921f1fthere's no additional penalty for defaulting again"));
                lore.add("");
                lore.add(StringFormatter.formatHex("#454343(Current Credit Score "+loan.getBorrower().getLoanHandler().getCreditScoreString()+"#454343)"));
            }
            lore.add("");
        }
        lore.add(StringFormatter.formatHex("#50e846Click to " + (loan.hasDefaulted() ? "Resume Payments" : "Default")));
        meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, loan.getId());
        meta.getPersistentDataContainer().set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, loan.getIssuer().getId());
        meta.setLore(lore);
        i.setItemMeta(meta);
        return i;
    }

    public ItemStack createPauseInterestItem(Loan loan) {
        ItemStack i = TLibs.getItemAPI().getCreator().getItemFromPath("m.currency.handful_of_coins");
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex(loan.isInterestPaused() ? "#81e072Resume Interest" : "#e08172Pause Interest"));
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#ccc396Interest is currently "+(loan.isInterestPaused() ? (RED + "PAUSED") : (GREEN + "ENABLED"))));
        lore.add(StringFormatter.formatHex("#7a706a§oToggle interest accrual on this loan"));
        lore.add("");
        lore.add(StringFormatter.formatHex("#50e846Click to Toggle"));
        meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, loan.getId());
        meta.getPersistentDataContainer().set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, loan.getIssuer().getId());
        meta.setLore(lore);
        i.setItemMeta(meta);
        return i;
    }

    public ItemStack createForgiveLoanItem(Loan loan) {
        ItemStack i = TLibs.getItemAPI().getCreator().getItemsAdderItem("iasurvival:sword_skin_vyderlight");
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(StringFormatter.formatHex(loan.isPaidOff() ? "#87d65cSettle Loan" : "#d65c5cForgive Loan"));
        
        List<String> lore = new ArrayList<>();
        if(loan.isPaidOff()) {
            lore.add(StringFormatter.formatHex("#ccc396This loan is already paid off"));
            lore.add(StringFormatter.formatHex("#ccc396Clicking this will remove it from your records"));
            lore.add(StringFormatter.formatHex("#ccc396This has no consequences"));
        } else {
            lore.add(StringFormatter.formatHex("#ccc396Forgiving a loan means the borrower does"));
            lore.add(StringFormatter.formatHex("#ccc396not have to pay it back, but it also means"));
            lore.add(StringFormatter.formatHex("#ccc396you won't get the money back either."));
            lore.add(StringFormatter.formatHex("#ccc396The borrower's credit score will not take a hit."));
        }
        lore.add("");
        lore.add(StringFormatter.formatHex("#50e846Click to " + (loan.isPaidOff() ? "Settle" : "Forgive")));
        meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, loan.getId());
        meta.getPersistentDataContainer().set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, loan.getIssuer().getId());
        meta.setLore(lore);
        i.setItemMeta(meta);
        return i;
    }
} 
