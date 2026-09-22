package me.Plugins.SimpleFactions.mercenary.contract;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryReputationCalculator;
import net.md_5.bungee.api.ChatColor;

/**
 * The three-stage negotiation book, cloned from
 * {@link me.Plugins.SimpleFactions.Guild.loans.LoanBook}: editing the terms page
 * <i>is</i> the counter-offer, and signing is the only way to move a stage on.
 *
 * <ol>
 *   <li><b>Draft</b> - the company leader fills the terms in and signs.</li>
 *   <li><b>Review</b> - the validated terms and what they cost, for the company.</li>
 *   <li><b>Agreement</b> - the book a government member of the hiring faction signs.</li>
 * </ol>
 *
 * <p>Every page is built by a plain {@code String} method so the wording can be
 * tested without a live {@code Bukkit.getItemFactory()}.
 */
public final class ContractBook {
    public static final int STAGE_DRAFT = 1;
    public static final int STAGE_REVIEW = 2;
    public static final int STAGE_AGREEMENT = 3;

    /** A book, like an offer, is good for a day. */
    public static final long BOOK_WINDOW_MS = 86400000L;

    /** The sentence that stops the two prices reading as either/or. */
    public static final String BOTH_PRICES_SENTENCE =
            "On a day the company fights, the hirer owes BOTH the day price AND the battle price. "
                    + "They are not alternatives.";

    private ContractBook() {
    }

    /* =====================================================
     * Page text
     * ===================================================== */

    public static String draftInstructionsPage(MercenaryCompany company) {
        return "§6§l[CONTRACT DRAFT]\n"
                + "§0Fill in the terms on the next page, then sign to check them against "
                + company.getName() + "'s minimums.\n"
                + "\n"
                + "You will then receive an agreement for the hiring faction to sign.";
    }

    public static String reviewInstructionsPage() {
        return "§6§l[CONTRACT REVIEW]\n"
                + "§0Check the terms on the next page. Sign to turn them into an offer the "
                + "hiring faction can accept.\n"
                + "\n"
                + "The offer holds the slots for one day, then lapses.";
    }

    public static String agreementInstructionsPage(MercenaryCompany company) {
        return "§6§l[MERCENARY CONTRACT]\n"
                + "§0" + company.getName() + "§0 offers its blades to the faction who signs "
                + "this document.\n"
                + "\n"
                + "Any member of that faction's government may sign. This contract is binding "
                + "upon signing.";
    }

    /** The one editable page. Its exact shape is what {@link #parseTerms} reads back. */
    public static String draftTermsPage(MercenaryCompany company) {
        return termsPage(company, ContractTerms.defaults());
    }

    public static String termsPage(MercenaryCompany company, ContractTerms terms) {
        return "§6§l[CONTRACT TERMS]\n"
                + "§0Company: " + company.getName() + "\n"
                + "Slots: " + terms.slots() + "\n"
                + "Price per battle (d): " + Formatter.formatMoney(terms.pricePerSlotPerBattle()) + "\n"
                + "Price per day (d): " + Formatter.formatMoney(terms.pricePerSlotPerDay()) + "\n"
                + "Duration(days): " + terms.durationDays() + "\n"
                + "Absence refund (d): "
                + Formatter.formatMoney(terms.absenceRefundPerSlotPerBattle()) + "\n"
                + "Breach refund (d): " + Formatter.formatMoney(terms.breachRefund()) + "\n";
    }

    public static String termsPage(MercenaryContract contract) {
        return termsPage(contract.getCompany(), contract.getTerms());
    }

    /**
     * What the terms actually cost. Prices are per slot, so the totals are spelled
     * out rather than left as an exercise, and the both-prices rule is stated in
     * full because getting it wrong is the difference between a bargain and ruin.
     */
    public static String pricingPage(MercenaryContract contract) {
        ContractTerms terms = contract.getTerms();
        return "§6§l[WHAT IT COSTS]\n"
                + "§0Slots hired: " + terms.slots() + "\n"
                + "Per day: " + Formatter.formatMoney(contract.getDailyPrice()) + "d\n"
                + "Per battle: " + Formatter.formatMoney(contract.getBattlePrice()) + "d\n"
                + "A day with a battle: "
                + Formatter.formatMoney(contract.getDailyPrice() + contract.getBattlePrice()) + "d\n"
                + "\n"
                + "§0" + BOTH_PRICES_SENTENCE;
    }

    public static String refundsPage(MercenaryContract contract) {
        ContractTerms terms = contract.getTerms();
        return "§6§l[REFUNDS]\n"
                + "§0Absence refund: "
                + Formatter.formatMoney(terms.absenceRefundPerSlotPerBattle())
                + "d per slot that fails to show for a battle.\n"
                + "\n"
                + "Breach refund: " + Formatter.formatMoney(terms.breachRefund())
                + "d if the company drops below the " + terms.slots()
                + " slots it promised.\n"
                + "\n"
                + "Days already served are owed in every case.";
    }

    public static String scopePage(MercenaryContract contract) {
        return "§6§l[SCOPE]\n"
                + "§0This contract names no war. The company serves the hiring faction in "
                + "every war it is in for the " + contract.getDurationDays()
                + " days of the window, exactly as that faction's own army does.\n"
                + "\n"
                + "The company will not take arms against its own realm, its allies or its "
                + "overlord. Should that come to pass the contract simply ends.";
    }

    public static String signaturePage(MercenaryContract contract) {
        Faction hirer = contract.getHirer();
        return "§6§l[SIGNATURES]\n"
                // Book ink stays black, so the band carries what the gradient carries elsewhere.
                + "§0Reputation at signing: " + contract.getReputationAtSigning()
                + " (" + MercenaryReputationCalculator.band(contract.getReputationAtSigning()) + ")\n"
                + "\n"
                + "Company:\n"
                + "-------------------\n"
                + contract.getCompany().getLeader() + "\n"
                + "\n"
                + "Hirer:\n"
                + "-------------------\n"
                + (hirer != null ? hirer.getName() : "[SIGN BOOK TO ACCEPT]") + "\n"
                + "-------------------\n"
                + "Binding upon signing.";
    }

    /* =====================================================
     * Parsing
     * ===================================================== */

    /** Reads a terms page back, however the company reworded the rest of the book. */
    public static ContractTerms parseTerms(String page) {
        if (page == null) return null;
        String[] lines = ChatColor.stripColor(page).split("\n");

        int slots = 0;
        double perBattle = 0;
        double perDay = 0;
        int duration = 0;
        double absenceRefund = 0;
        double breachRefund = 0;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("Slots")) {
                slots = (int) numberAfterColon(line);
            } else if (line.startsWith("Price per battle")) {
                perBattle = numberAfterColon(line);
            } else if (line.startsWith("Price per day")) {
                perDay = numberAfterColon(line);
            } else if (line.startsWith("Duration")) {
                duration = (int) numberAfterColon(line);
            } else if (line.startsWith("Absence refund")) {
                absenceRefund = numberAfterColon(line);
            } else if (line.startsWith("Breach refund")) {
                breachRefund = numberAfterColon(line);
            }
        }
        return new ContractTerms(slots, perBattle, perDay, duration, absenceRefund, breachRefund);
    }

    public static ContractTerms parseTerms(BookMeta meta) {
        if (meta == null || meta.getPageCount() < 2) return null;
        return parseTerms(meta.getPage(2));
    }

    public static String companyGuildId(BookMeta meta) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
    }

    public static Integer stage(BookMeta meta) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer()
                .get(Keys.CONTRACT_STAGE, PersistentDataType.INTEGER);
    }

    public static String contractId(BookMeta meta) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(Keys.CONTRACT_ID, PersistentDataType.STRING);
    }

    public static Long expiry(BookMeta meta) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(Keys.LONG, PersistentDataType.LONG);
    }

    /** The tamper check: the terms page as it was when the company signed it. */
    public static String snapshot(BookMeta meta) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer()
                .get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING);
    }

    /**
     * True when the terms in the book still match the snapshot taken at the previous
     * stage. Compared as parsed figures, so recolouring is fine and rewriting a
     * number is not.
     */
    public static boolean matchesSnapshot(BookMeta meta) {
        ContractTerms signed = parseTerms(meta);
        ContractTerms snapshot = parseTerms(snapshot(meta));
        return signed != null && snapshot != null && signed.equals(snapshot);
    }

    private static double numberAfterColon(String line) {
        try {
            int colon = line.lastIndexOf(':');
            if (colon < 0 || colon + 1 >= line.length()) return 0;
            return Double.parseDouble(line.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /* =====================================================
     * Books
     * ===================================================== */

    public static ItemStack draftBook(MercenaryCompany company) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.addPage(draftInstructionsPage(company));
        meta.addPage(draftTermsPage(company));
        meta.addPage(minimumsPage());
        stamp(meta, company, STAGE_DRAFT, null, null);
        book.setItemMeta(meta);
        return book;
    }

    public static ItemStack reviewBook(MercenaryCompany company, ContractTerms terms) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.addPage(reviewInstructionsPage());
        meta.addPage(termsPage(company, terms));
        meta.addPage(minimumsPage());
        stamp(meta, company, STAGE_REVIEW, null, meta.getPage(2));
        book.setItemMeta(meta);
        return book;
    }

    /** The agreement, tied to the offered contract it will activate. */
    public static ItemStack agreementBook(MercenaryContract contract) {
        MercenaryCompany company = contract.getCompany();
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.addPage(agreementInstructionsPage(company));
        meta.addPage(termsPage(contract));
        meta.addPage(pricingPage(contract));
        meta.addPage(refundsPage(contract));
        meta.addPage(scopePage(contract));
        meta.addPage(signaturePage(contract));
        stamp(meta, company, STAGE_AGREEMENT, contract.getId(), meta.getPage(2));
        book.setItemMeta(meta);
        return book;
    }

    /** The same floors as {@link #minimumsPage()}, short enough for a lore line. */
    public static String minimumsSummary() {
        return "Minimum " + Formatter.formatMoney(Cache.mercenaryMinPricePerBattle)
                + "d per battle, " + Formatter.formatMoney(Cache.mercenaryMinPricePerDay)
                + "d per day, max " + Cache.mercenaryMaxContractDays + " days";
    }

    public static String minimumsPage() {
        return "§6§l[MINIMUMS]\n"
                + "§0No contract may go below " + Formatter.formatMoney(Cache.mercenaryMinPricePerBattle)
                + "d per slot per battle or " + Formatter.formatMoney(Cache.mercenaryMinPricePerDay)
                + "d per slot per day, or run longer than " + Cache.mercenaryMaxContractDays
                + " days.\n"
                + "\n"
                + "The absence refund may not be below the price per battle.";
    }

    private static void stamp(
            BookMeta meta, MercenaryCompany company, int stage, String contractId, String snapshot) {
        meta.getPersistentDataContainer().set(
                Keys.STRING_KEY, PersistentDataType.STRING, company.getGuild().getId());
        meta.getPersistentDataContainer().set(
                Keys.CONTRACT_STAGE, PersistentDataType.INTEGER, stage);
        meta.getPersistentDataContainer().set(
                Keys.LONG, PersistentDataType.LONG, System.currentTimeMillis() + BOOK_WINDOW_MS);
        if (contractId != null) {
            meta.getPersistentDataContainer().set(
                    Keys.CONTRACT_ID, PersistentDataType.STRING, contractId);
        }
        if (snapshot != null) {
            meta.getPersistentDataContainer().set(
                    Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, snapshot);
        }
    }
}
