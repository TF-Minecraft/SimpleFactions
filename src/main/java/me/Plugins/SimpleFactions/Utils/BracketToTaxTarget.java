package me.Plugins.SimpleFactions.Utils;

import me.Plugins.SimpleFactions.enums.Brackets;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;

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
