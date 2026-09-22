package net.tfminecraft.simplefactions.utils;

import net.tfminecraft.simplefactions.enums.Brackets;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;

public class BracketToTaxTarget {
    public static TaxTarget convert(Brackets bracket) {
        switch (bracket) {
            case CITIZEN_TAX:
                return TaxTarget.CITIZENS;
            case DIVIDEND_TAX:
                return TaxTarget.DIVIDENDS;
            case GUILD_TAX:
                return TaxTarget.GUILDS;
            case TARIFFS:
                return TaxTarget.TARIFFS;
            case VASSAL_TAX:
                return TaxTarget.VASSALS;
            default:
                break;
        }
        return TaxTarget.CITIZENS;
    }
}
