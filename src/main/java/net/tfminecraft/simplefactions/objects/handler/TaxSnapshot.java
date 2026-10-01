package net.tfminecraft.simplefactions.objects.handler;

import java.util.HashMap;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;

public class TaxSnapshot {

    final double citizenTax;
    final double guildTax;
    final double vassalTax;
    final double dividendTax;
    final double tariffs;
    final double hubTax;

    final HashMap<TaxTarget, HashMap<String, Double>> specificTaxes;

    TaxSnapshot(
        double citizenTax,
        double guildTax,
        double vassalTax,
        double dividendTax,
        double tariffs,
        double hubTax,
        HashMap<TaxTarget, HashMap<String, Double>> specificTaxes
    ) {
        this.citizenTax = citizenTax;
        this.guildTax = guildTax;
        this.vassalTax = vassalTax;
        this.dividendTax = dividendTax;
        this.tariffs = tariffs;
        this.hubTax = hubTax;
        this.specificTaxes = specificTaxes;
    }
}
