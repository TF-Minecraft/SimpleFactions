package me.Plugins.SimpleFactions.mercenary.contract;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/**
 * Every rule a contract must pass before it is written, each with its own
 * refusal so a company knows which figure to change.
 */
public final class ContractValidator {
    private ContractValidator() {
    }

    /**
     * @param from when the window opens, used for the overlapping-window capacity check
     */
    public static MercenaryResult validate(
            ContractTerms terms, MercenaryCompany company, long from) {
        if (terms == null) {
            return MercenaryResult.deny("The contract terms could not be read.");
        }
        if (company == null || !company.isFormed()) {
            return MercenaryResult.deny("That company is not open for hire yet.");
        }
        MercenaryResult prices = validatePrices(terms);
        if (!prices.ok()) return prices;

        MercenaryResult duration = validateDuration(terms);
        if (!duration.ok()) return duration;

        MercenaryResult refunds = validateRefunds(terms);
        if (!refunds.ok()) return refunds;

        return validateSlots(terms, company, from);
    }

    static MercenaryResult validatePrices(ContractTerms terms) {
        if (terms.pricePerSlotPerBattle() < Cache.mercenaryMinPricePerBattle) {
            return MercenaryResult.deny("The price per battle cannot be below "
                    + money(Cache.mercenaryMinPricePerBattle) + " per slot.");
        }
        if (terms.pricePerSlotPerDay() < Cache.mercenaryMinPricePerDay) {
            return MercenaryResult.deny("The price per day cannot be below "
                    + money(Cache.mercenaryMinPricePerDay) + " per slot.");
        }
        return MercenaryResult.ok("Prices accepted.");
    }

    static MercenaryResult validateDuration(ContractTerms terms) {
        if (terms.durationDays() < 1) {
            return MercenaryResult.deny("A contract must run for at least one day.");
        }
        if (terms.durationDays() > Cache.mercenaryMaxContractDays) {
            return MercenaryResult.deny("A contract cannot run longer than "
                    + Cache.mercenaryMaxContractDays + " days.");
        }
        return MercenaryResult.ok("Duration accepted.");
    }

    /**
     * The absence refund floor is the rule that keeps the incentive the right way
     * round. Refund a company less than it was paid for a battle and it earns more
     * per head by staying home than by fighting.
     */
    static MercenaryResult validateRefunds(ContractTerms terms) {
        if (terms.absenceRefundPerSlotPerBattle() < terms.pricePerSlotPerBattle()) {
            return MercenaryResult.deny("The absence refund must be at least the price per battle, "
                    + money(terms.pricePerSlotPerBattle()) + " per slot.");
        }
        if (terms.breachRefund() < 0) {
            return MercenaryResult.deny("The breach refund cannot be negative.");
        }
        return MercenaryResult.ok("Refunds accepted.");
    }

    static MercenaryResult validateSlots(
            ContractTerms terms, MercenaryCompany company, long from) {
        if (terms.slots() < 1) {
            return MercenaryResult.deny("A contract must hire at least one slot.");
        }
        if (terms.slots() > company.getSlots()) {
            return MercenaryResult.deny("That company only has " + company.getSlots()
                    + " slot" + (company.getSlots() == 1 ? "" : "s") + ".");
        }
        return SlotReservations.canPromise(
                company, terms.slots(), from, from + terms.durationMillis());
    }

    private static String money(double amount) {
        return Formatter.formatMoney(amount) + "d";
    }
}
