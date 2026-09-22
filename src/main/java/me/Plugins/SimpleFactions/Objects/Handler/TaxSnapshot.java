package me.Plugins.SimpleFactions.Objects.Handler;

import java.util.HashMap;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;

public class TaxSnapshot {

    final double citizenTax;
    final double guildTax;
    final double vassalTax;
    final double dividendTax;
    final double tariffs;

    final HashMap<TaxTarget, HashMap<String, Double>> specificTaxes;

    TaxSnapshot(
        double citizenTax,
        double guildTax,
        double vassalTax,
        double dividendTax,
        double tariffs,
        HashMap<TaxTarget, HashMap<String, Double>> specificTaxes
    ) {
        this.citizenTax = citizenTax;
        this.guildTax = guildTax;
        this.vassalTax = vassalTax;
        this.dividendTax = dividendTax;
        this.tariffs = tariffs;
        this.specificTaxes = specificTaxes;
    }
}
