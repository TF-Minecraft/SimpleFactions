package me.Plugins.SimpleFactions.Guild.loans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

public class LoanBook {

    public static ItemStack getLoanBook(Loan loan) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        // Page 1
        meta.addPage("§6§l[LOAN AGREEMENT]\n" + //
                        loan.getIssuer().getName()+"§0 hereby\n" + //
                    "§0issues a loan of §6"+loan.getAmount()+"d§0 to the guild who signs the document\n" + //
                        "\n" + //
                        "The loan shall be paid in full within "+loan.getDurationInDays()+" days including "+loan.getInterestRate()+"% interest per week on the remaining amount.");
        meta.addPage("§6§l[LOAN TERMS]\n" + //
                        "§0Issuer: "+loan.getIssuer().getName()+"§0\n" + //
                        "Amount (d): "+loan.getAmount()+"\n" + //
                        "Duration(days): "+loan.getDurationInDays()+"\n" + //
                        "Interest (%): "+loan.getInterestRate()+"\n" + //
                        "Daily Payments: \n"+(loan.isAutoPay() ? "Yes" : "No")+"\n" + //
                        "Overdue Fee (%): "+loan.getOverdueFee()+"\n" + //
                        "\n" + //
                        "Estimated Cost: "+loan.getEstimatedCost()+"d\n" + //
                        "\n" + //
                        "For further reading, see next pages.");
        meta.addPage("Duration and Amount:\n" + //
                        "Failure to pay the amount + interest by the due date, the issuer may use other means to aquire the funds.\n" + //
                        "\n" + //
                        "If daily payments is activated the loan will automatically be repaid without further action for your convenience.");
        meta.addPage("If daily payments are NOT activated it is the responsibility of the loan taker to ensure they repay the loan in time.\n" + //
                        "\n" + //
                        "The loan may be repaid in full or in part at any time, regardless of wether daily payments have been activated.");
        meta.addPage("Interest:\n" + //
                        "The given interest rate represent the weekly rate. However the interest is added daily by taking "+Formatter.formatDouble(loan.getDailyInterestRate())+"% to represent the daily rate. \n" + //
                        "\n" + //
                        "If daily payments is activated the interest is paid automatically alongside your daily payment.");
        meta.addPage("If daily payments are NOT activated interest will accrue on your total and will be subject to compound interest should the loan taker fail to keep up with interest payments.\n" + //
                        "\n" + //
                        "The borrower can enable or disable daily payments at will.");
        meta.addPage("Overdue Fees:\n" + //
                        "If the loan is not paid back by the due date an overdue fee may be applied as a percentage of the remaining loan every day.");
        meta.addPage("Signatures:\n" + //
                        "\n" + //
                        "Issuer:\n" + //
                        "-------------------\n" + //
                        loan.getIssuer().getLeader()+"\n" + //
                        "\n" + //
                        "-------------------\n" + //
                        "Loan Taker:\n" + //
                        "\n" + //
                        (loan.getBorrower() != null ? loan.getBorrower().getLeader() : "[SIGN BOOK TO ACCEPT]") + "\n" + //
                        "-------------------\n" + //
                        "This contract is binding upon signing.");
        meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, loan.getIssuer().getId());
        meta.getPersistentDataContainer().set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, meta.getPage(2));
        meta.getPersistentDataContainer().set(Keys.INT, PersistentDataType.INTEGER, 3);
        meta.getPersistentDataContainer().set(Keys.LONG, PersistentDataType.LONG, System.currentTimeMillis() + 86400000L); // 1 day in milliseconds
        book.setItemMeta(meta);
        return book;
    }

    public static ItemStack getBaseBook(Guild issuer) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        // Page 1 – Instructions
        meta.addPage(
                "§l[LOAN CREATION]\n"+
                "Fill in the required fields on the next page to set up the loan. When you are finished sign the book to get an estimated cost on the loan. If you accept the setup you will get a book the other party can sign."
        );

        // Page 2 – Editable Fields
        meta.addPage(
                "§6§l[LOAN TERMS]\n" + //
                "Amount (d): 1000\n" + //
                "Duration(days): 30\n" + //
                "Interest (%): 6\n" + //
                "Daily Payments: \nYes\n" + //
                "Overdue Fee (%): 2\n"
        );
        meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, issuer.getId());
        meta.getPersistentDataContainer().set(Keys.INT, PersistentDataType.INTEGER, 1);
        meta.getPersistentDataContainer().set(Keys.LONG, PersistentDataType.LONG, System.currentTimeMillis() + 86400000L); // 1 day in milliseconds
        book.setItemMeta(meta);
        return book;
    }

    public static ItemStack getEstimatedBook(Loan loan) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        // Page 1 – Instructions
        meta.addPage(
                "§l[LOAN CREATION]\n"+
                "Review that the details on the next page are correct. If you sign this book you will recieve a loan agreement the borrower can sign."
        );

        // Page 2 – Editable Fields
        meta.addPage(
                "§6§l[LOAN TERMS]\n" + //
                "Issuer: "+loan.getIssuer().getName()+"\n" + //
                "Amount (d): "+loan.getAmount()+"\n" + //
                "Duration(days): "+loan.getDurationInDays()+"\n" + //
                "Interest (%): "+loan.getInterestRate()+"\n" + //
                "Daily Payments: \n"+(loan.isAutoPay() ? "Yes" : "No")+"\n" + //
                "Overdue Fee (%): "+loan.getOverdueFee()+"\n" + //
                "\n" + //
                "Estimated Cost: "+loan.getEstimatedCost()+"d\n"
        );
        meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, loan.getIssuer().getId());
        meta.getPersistentDataContainer().set(Keys.INT, PersistentDataType.INTEGER, 2);
        meta.getPersistentDataContainer().set(Keys.LONG, PersistentDataType.LONG, System.currentTimeMillis() + 86400000L); // 1 day in milliseconds
        book.setItemMeta(meta);
        return book;
    }

    public static Loan createLoanFromBook(BookMeta meta, Guild borrower) {
        String id = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
        Guild issuer = FactionManager.getGuildByString(id);
        if (issuer == null) return null;
        if (meta.getPageCount() < 2)
            return null;

        String page = ChatColor.stripColor(meta.getPage(2));
        return createLoanFromString(page, issuer, borrower);
    }

    public static Loan createLoanFromString(String page, Guild issuer, Guild borrower) {
        String[] lines = page.split("\n");

        double amount = 0;
        int duration = 0;
        double interest = 0;
        boolean autoPay = false;
        double overdueFee = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.startsWith("Amount")) {
                amount = parseDoubleAfterColon(line);
            }

            else if (line.startsWith("Duration")) {
                duration = (int) parseDoubleAfterColon(line);
            }

            else if (line.startsWith("Interest")) {
                interest = parseDoubleAfterColon(line);
            }

            else if (line.startsWith("Daily Payments")) {
                // Value is expected on next line
                if (i + 1 < lines.length) {
                    String value = lines[i + 1].trim().toLowerCase();
                    autoPay = value.equals("yes") || value.equals("true");
                }
            }

            else if (line.startsWith("Overdue Fee")) {
                overdueFee = parseDoubleAfterColon(line);
            }
        }

        // ===== VALIDATION =====
        if (amount <= 0) return null;
        if (duration <= 0) return null;
        if (interest < 0) return null;
        if (overdueFee < 0) return null;

        return new Loan(
                amount,
                issuer,
                borrower,
                System.currentTimeMillis(),
                duration,
                interest,
                overdueFee,
                autoPay
        );
    }

    private static double parseDoubleAfterColon(String line) {
        try {
            String[] split = line.split(":");
            if (split.length < 2) return 0;
            return Double.parseDouble(split[1].trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
