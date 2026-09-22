package me.Plugins.SimpleFactions.mercenary.contract;

import me.Plugins.SimpleFactions.Cache;

/**
 * The numbers a contract is made of, separated from the contract so the book
 * parser can hand raw figures to {@link ContractValidator} before anything is
 * signed. Every value is absolute denars: nothing here is a percentage of
 * anyone's income, which is what keeps the Phase 5 cashflows from recursing.
 */
public record ContractTerms(
        int slots,
        double pricePerSlotPerBattle,
        double pricePerSlotPerDay,
        int durationDays,
        double absenceRefundPerSlotPerBattle,
        double breachRefund) {

    /** What a fresh draft book spawns with: the config floors and default refund. */
    public static ContractTerms defaults() {
        return new ContractTerms(
                1,
                Cache.mercenaryMinPricePerBattle,
                Cache.mercenaryMinPricePerDay,
                7,
                Cache.mercenaryMinPricePerBattle,
                Cache.mercenaryDefaultBreachRefund);
    }

    public long durationMillis() {
        return durationDays * 24L * 60L * 60L * 1000L;
    }

    /** A battle day costs both rates, so this is the worst-case daily figure per slot. */
    public double maxDailyCostPerSlot() {
        return pricePerSlotPerDay + pricePerSlotPerBattle;
    }
}
