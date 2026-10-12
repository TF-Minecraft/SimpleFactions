package net.tfminecraft.simplefactions.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Boundary between existing display-unit calculations and exact ledger cents. */
public final class Money {
    private Money() {}

    public static long cents(double denars) {
        return BigDecimal.valueOf(denars).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static double denars(long cents) {
        return BigDecimal.valueOf(cents, 2).doubleValue();
    }
}
