package net.tfminecraft.simplefactions.guild.hub;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.HubTaxBreakdown.Assessment;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;

/** Computes hub tax once per day on copies of the province data. Server thread only. */
public final class HubTaxService {
    private HubTaxService() {
    }

    public static void refresh(ProvinceManager provinces) {
        List<Guild> guilds = SupplyHubService.allGuilds();
        for (Guild guild : guilds) {
            guild.setHubTaxBreakdown(assess(provinces, guild, guilds));
        }
    }

    public static HubTaxBreakdown assess(ProvinceManager provinces, Guild guild, List<Guild> guilds) {
        List<Link> links = HubNetwork.linksFor(guild);
        if (links.isEmpty() || !guild.hasCapital()) {
            return HubTaxBreakdown.empty();
        }
        double base = incomeWith(provinces, guild, List.of());
        double full = incomeWith(provinces, guild, links);
        Map<SupplyHub, Double> without = new HashMap<>();
        Map<SupplyHub, Faction> hosts = new HashMap<>();
        for (SupplyHub hub : guild.getSupplyHubs()) {
            Installation installation = SupplyHubService.findInstallation(
                    hub.ownerFactionId(), hub.installationId());
            Faction host = FactionManager.getByString(hub.ownerFactionId());
            if (installation == null || host == null) {
                continue;
            }
            boolean allowed = SupplyHubService.ownerAllows(
                    guild.getFaction().getId(), host.getId(), host.hasHubPermit(guild.getId()));
            if (!SupplyHubService.standing(
                    guild, hub, true, allowed,
                    InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel()),
                    SupplyHubService.atInstallation(host.getId(), installation.getId(), guilds)).active()) {
                continue;
            }
            List<Link> remaining = links.stream()
                    .filter(link -> !touches(link, hub))
                    .toList();
            without.put(hub, incomeWith(provinces, guild, remaining));
            hosts.put(hub, host);
        }
        Map<SupplyHub, Double> taxable = taxableIncome(base, full, without);
        Map<SupplyHub, Assessment> assessments = new HashMap<>();
        Set<Faction> exemptHosts = new HashSet<>();
        Map<Faction, Double> taxableByFaction = new HashMap<>();
        Map<Faction, Double> taxesByFaction = new HashMap<>();
        for (Map.Entry<SupplyHub, Double> entry : taxable.entrySet()) {
            Faction host = hosts.get(entry.getKey());
            double income = entry.getValue();
            double tax = 0;
            taxableByFaction.merge(host, income, Double::sum);
            if (RelationManager.sameRealm(host, guild.getFaction())) {
                exemptHosts.add(host);
            } else {
                tax = income * host.getTaxRate(TaxTarget.HUB_TAX, null, true) / 100.0;
                taxesByFaction.merge(host, tax, Double::sum);
            }
            assessments.put(entry.getKey(), new Assessment(income, tax));
        }
        taxesByFaction.replaceAll((host, tax) -> Formatter.formatDouble(tax));
        return new HubTaxBreakdown(assessments, taxableByFaction, taxesByFaction, exemptHosts);
    }

    /** Positive marginal gains, scaled only when their sum exceeds the total hub gain. */
    public static <T> Map<T, Double> taxableIncome(double base, double full, Map<T, Double> without) {
        double gain = Math.max(0, full - base);
        Map<T, Double> marginal = new HashMap<>();
        double total = 0;
        for (Map.Entry<T, Double> entry : without.entrySet()) {
            double amount = Math.max(0, full - entry.getValue());
            marginal.put(entry.getKey(), amount);
            total += amount;
        }
        if (total > gain) {
            double scale = gain / total;
            marginal.replaceAll((hub, amount) -> amount * scale);
        }
        return marginal;
    }

    static boolean touches(Link link, SupplyHub hub) {
        return hub != null && (matches(link.fromFactionId(), link.fromInstallationId(), hub)
                || matches(link.toFactionId(), link.toInstallationId(), hub));
    }

    private static boolean matches(String factionId, String installationId, SupplyHub hub) {
        return factionId != null && installationId != null
                && factionId.equalsIgnoreCase(hub.ownerFactionId())
                && installationId.equalsIgnoreCase(hub.installationId());
    }

    private static double incomeWith(ProvinceManager source, Guild guild, List<Link> links) {
        ProvinceManager snapshot = source.createSnapshotShell();
        snapshot.copyAllDataFrom(source);
        snapshot.setHubLinksOverride(Map.of(guild.getId(), links));
        snapshot.recalculateForSingleGuild(guild, false);
        return snapshot.getGrossTradeIncome(guild);
    }
}
