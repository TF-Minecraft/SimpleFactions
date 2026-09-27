package net.tfminecraft.simplefactions.government.proposal;

import net.tfminecraft.simplefactions.enums.Brackets;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.utils.Formatter;

/**
 * The charges in the vehicle tax law group. Every one is priced off the vehicle's
 * daily upkeep: the tax as a percentage paid each day, the fees as a multiple paid once.
 */
public enum FeeKind {
    VEHICLE_TAX("Vehicle Tax", Brackets.VEHICLE_TAX, Rules.VEHICLE_TAX, true),
    REGISTRATION_FEE("Registration Fee", Brackets.REGISTRATION_FEE, Rules.REGISTRATION_FEE, false),
    TRANSFER_FEE("Transfer Fee", Brackets.TRANSFER_FEE, Rules.TRANSFER_FEE, false);

    private final String displayName;
    private final Brackets bracket;
    private final Rules rule;
    private final boolean percent;

    FeeKind(String displayName, Brackets bracket, Rules rule, boolean percent) {
        this.displayName = displayName;
        this.bracket = bracket;
        this.rule = rule;
        this.percent = percent;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Brackets getBracket() {
        return bracket;
    }

    public Rules getRule() {
        return rule;
    }

    /** True when the rate is a percentage of upkeep, false when it is a multiple of it. */
    public boolean isPercent() {
        return percent;
    }

    /** The denars charged for a vehicle with this daily upkeep at this rate. */
    public double amount(double rate, double upkeep) {
        if (rate <= 0.0 || upkeep <= 0.0) {
            return 0.0;
        }
        return Formatter.formatDouble(percent ? upkeep * rate / 100.0 : upkeep * rate);
    }

    /** "12.5%" for the tax, "1.5x upkeep" for the fees. */
    public String formatRate(double rate) {
        return percent ? rate + "%" : rate + "x upkeep";
    }

    public static FeeKind fromBracket(Brackets bracket) {
        for (FeeKind kind : values()) {
            if (kind.bracket == bracket) {
                return kind;
            }
        }
        return null;
    }

    public static FeeKind fromRule(Rules rule) {
        for (FeeKind kind : values()) {
            if (kind.rule == rule) {
                return kind;
            }
        }
        return null;
    }
}
