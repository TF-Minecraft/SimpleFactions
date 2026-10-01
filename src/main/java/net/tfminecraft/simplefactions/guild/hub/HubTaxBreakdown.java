package net.tfminecraft.simplefactions.guild.hub;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;

/** Immutable daily assessment. Ordinary trade recalculations do not replace it. */
public final class HubTaxBreakdown {
    public record Assessment(double taxableIncome, double tax) {
    }

    private static final HubTaxBreakdown EMPTY = new HubTaxBreakdown(Map.of(), Map.of(), Map.of(), Set.of());

    private final Set<Faction> exemptHosts;
    private final Map<SupplyHub, Assessment> hubs;
    private final Map<Faction, Double> taxableByFaction;
    private final Map<Faction, Double> taxesByFaction;

    public HubTaxBreakdown(
            Map<SupplyHub, Assessment> hubs,
            Map<Faction, Double> taxableByFaction,
            Map<Faction, Double> taxesByFaction,
            Set<Faction> exemptHosts) {
        this.exemptHosts = Set.copyOf(exemptHosts);
        this.hubs = Map.copyOf(hubs);
        this.taxableByFaction = Map.copyOf(taxableByFaction);
        this.taxesByFaction = Map.copyOf(taxesByFaction);
    }

    public static HubTaxBreakdown empty() {
        return EMPTY;
    }

    public Assessment forHub(SupplyHub hub) {
        return hubs.getOrDefault(hub, new Assessment(0, 0));
    }

    public double getTaxableIncome(Faction host) {
        return taxableByFaction.getOrDefault(host, 0.0);
    }

    public Map<Faction, Double> getTaxesByFaction() {
        IncomePreviewContext context = IncomePreviewContext.current();
        if (context == null) {
            return taxesByFaction;
        }
        Map<Faction, Double> preview = new HashMap<>(taxesByFaction);
        for (Map.Entry<Faction, Double> entry : taxableByFaction.entrySet()) {
            Faction host = entry.getKey();
            if (!exemptHosts.contains(host) && context.affectsHubTax(host)) {
                preview.put(host, Formatter.formatDouble(
                        entry.getValue() * host.getTaxRate(TaxTarget.HUB_TAX, null, true) / 100.0));
            }
        }
        return preview;
    }

    public double getTax(Faction host) {
        return getTaxesByFaction().getOrDefault(host, 0.0);
    }

    public double getTotalTax() {
        double total = 0;
        for (double tax : getTaxesByFaction().values()) {
            total += tax;
        }
        return Formatter.formatDouble(total);
    }
}
