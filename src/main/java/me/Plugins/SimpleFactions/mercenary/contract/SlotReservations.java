package me.Plugins.SimpleFactions.mercenary.contract;

import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/**
 * The over-promising guard. The constraint is on <b>overlapping</b> windows, not
 * on lifetime totals: two back-to-back seven day contracts for every slot are
 * perfectly legal, two overlapping ones are not. A running counter cannot say
 * that, so every question is asked about a window.
 */
public final class SlotReservations {
    private SlotReservations() {
    }

    /**
     * Slots already promised across the given window. Offered contracts count,
     * which is what stops a company selling the same slots to two prospects at
     * once; declining or expiring an offer releases the hold on its own.
     */
    public static int promised(MercenaryCompany company, long from, long to) {
        if (company == null) return 0;
        int total = 0;
        for (MercenaryContract c : company.getContractHandler().getReserving()) {
            if (c.overlaps(from, to)) total += c.getSlots();
        }
        return total;
    }

    /** What the market screen may honestly advertise for a window. */
    public static int remaining(MercenaryCompany company, long from, long to) {
        if (company == null) return 0;
        return Math.max(0, company.getSlots() - promised(company, from, to));
    }

    public static MercenaryResult canPromise(
            MercenaryCompany company, int slots, long from, long to) {
        if (company == null) {
            return MercenaryResult.deny("That company no longer exists.");
        }
        int free = remaining(company, from, to);
        if (slots > free) {
            return MercenaryResult.deny("That company has only " + free + " of its "
                    + company.getSlots() + " slots free for those dates.");
        }
        return MercenaryResult.ok("Slots available.");
    }
}
