package net.tfminecraft.simplefactions.mercenary.contract;

import net.tfminecraft.simplefactions.mercenary.MercenaryResult;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;

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
     * and so does a pending increase, which is what stops a company selling the
     * same slots twice. Declining, withdrawing, or letting the day lapse releases
     * the hold. A pending decrease does not free slots until it is accepted.
     */
    public static int promised(MercenaryCompany company, long from, long to) {
        return promised(company, from, to, null);
    }

    /**
     * @param except a contract whose own hold is left out, so an amendment can ask
     *               how much room it would have if its current promise were replaced
     */
    public static int promised(
            MercenaryCompany company, long from, long to, MercenaryContract except) {
        if (company == null) return 0;
        long now = System.currentTimeMillis();
        int total = 0;
        for (MercenaryContract c : company.getContractHandler().getReserving()) {
            if (same(c, except) || !c.overlaps(from, to)) continue;
            total += c.heldSlots(now);
        }
        return total;
    }

    /**
     * Largest slot count an amendment may offer for the rest of this contract.
     * The contract's own hold is replaceable, so a decrease always fits when the
     * company still covers what it already promised.
     */
    public static int maxForAmendment(MercenaryCompany company, MercenaryContract contract, long now) {
        if (company == null || contract == null) return 0;
        if (now >= contract.getDueDate()) return contract.getSlots();
        int others = promised(company, now, contract.getDueDate(), contract);
        return Math.max(0, company.getSlots() - others);
    }

    private static boolean same(MercenaryContract a, MercenaryContract b) {
        if (a == null || b == null) return false;
        String id = a.getId();
        return id != null && id.equalsIgnoreCase(b.getId());
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
