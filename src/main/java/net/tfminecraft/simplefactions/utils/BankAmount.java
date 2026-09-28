package net.tfminecraft.simplefactions.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Parses an amount typed into a bank deposit or withdrawal. Balances round each change to whole
 * cents, so an amount such as 0.005 would move nothing from one side and 0.01 into the other.
 */
public final class BankAmount {

    private BankAmount() {
    }

    /** The amount, or null unless it is a positive number of whole cents. */
    public static Double parse(String text) {
        if (text == null) return null;
        BigDecimal value;
        try {
            value = new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (value.signum() <= 0) return null;
        if (value.stripTrailingZeros().scale() > 2) return null;
        double amount = value.doubleValue();
        if (!Double.isFinite(amount)) return null;
        // A huge amount can lose its cents as a double, which would move a different sum.
        BigDecimal kept = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        return kept.compareTo(value) == 0 ? amount : null;
    }
}
