package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Where a guild can place a hub, and how much of its trade power would arrive there
 * from the hubs it already has. Read from the live map. No income projection.
 */
public final class HubEstimates {
    /**
     * One existing installation. {@code arrivesHere} is the most trade that would arrive
     * here with a hub, best over the node's edges. {@code connected} is true when one of
     * the guild's active hubs already has an edge to this site.
     */
    public record Site(
            String hostFactionId,
            String installationId,
            String label,
            int provinceId,
            InstallationKind kind,
            boolean ownRealm,
            double tradeHere,
            double arrivesHere,
            boolean connected) {
    }

    private record Arrival(double trade, boolean connected) {
    }

    private HubEstimates() {
    }

    public static List<Site> sites(Guild guild) {
        ProvinceManager provinces = net.tfminecraft.simplefactions.SimpleFactions.getInstance() == null
                ? null
                : net.tfminecraft.simplefactions.SimpleFactions.getInstance().getProvinceManager();
        List<Faction> factions = FactionManager.factions == null ? List.of() : new ArrayList<>(FactionManager.factions);
        return sites(guild, provinces, factions, SupplyHubService.allGuilds());
    }

    static List<Site> sites(Guild guild, ProvinceManager provinces, List<Faction> factions, List<Guild> guilds) {
        if (guild == null || guild.getId() == null || !SupplyHubService.allowsSupplyHubs(guild)) {
            return List.of();
        }
        List<Installation> sources = activeSources(guild, guilds);
        List<Site> sites = new ArrayList<>();
        if (factions != null) {
            for (Faction host : factions) {
                if (host == null || host.getId() == null || host.getInstallationHandler() == null
                        || host.getInstallationHandler().getAll() == null) {
                    continue;
                }
                boolean realm = sameRealm(guild.getFaction(), host);
                if (!realm && !host.hasFactionRule(Rules.HUB_TAX)) {
                    continue;
                }
                for (Installation installation : host.getInstallationHandler().getAll()) {
                    if (!eligible(guild, host, installation, guilds)) {
                        continue;
                    }
                    double here = tradeHere(provinces, guild, installation.getProvince());
                    Arrival delivered = arrival(guild, installation, sources, provinces);
                    sites.add(new Site(
                            host.getId(),
                            installation.getId(),
                            installation.getName() == null ? installation.getId() : installation.getName(),
                            installation.getProvince(),
                            installation.getKind(),
                            realm,
                            here,
                            delivered.trade(),
                            delivered.connected()));
                }
            }
        }
        return List.copyOf(sites);
    }

    /**
     * Trade that would arrive at {@code target} with a hub there, from the current snapshot.
     * {@code sources} only decides {@link Site#connected()}.
     */
    static double arrives(Guild guild, Installation target, List<Installation> sources, ProvinceManager provinces) {
        return arrival(guild, target, sources, provinces).trade();
    }

    private static Arrival arrival(
            Guild guild, Installation target, List<Installation> sources, ProvinceManager provinces) {
        boolean connected = false;
        if (guild != null && target != null && sources != null && provinces != null) {
            for (Installation source : sources) {
                if (source == null || source.getProvince() == target.getProvince()) {
                    continue;
                }
                if (HubNetwork.connect(source, target, provinces) != null) {
                    connected = true;
                    break;
                }
            }
        }
        double trade = 0;
        if (guild != null && guild.getId() != null && target != null && provinces != null) {
            HighwaySnapshot snapshot = HighwaySnapshot.current();
            double bonus = GuildModifierOverride.resolve(guild, GuildModifier.HUB_TRADE);
            trade = Highway.wouldArrive(
                    provinces, guild, snapshot.graph(), snapshot.hubbed(guild.getId()),
                    HubNetwork.findNode(snapshot.graph(), target), bonus);
        }
        return new Arrival(trade, connected);
    }

    public static List<Site> territory(List<Site> sites) {
        if (sites == null || sites.isEmpty()) {
            return List.of();
        }
        List<Site> shown = new ArrayList<>();
        for (Site site : sites) {
            if (site != null && site.ownRealm()) {
                shown.add(site);
            }
        }
        return List.copyOf(shown);
    }

    public static List<Site> withTrade(List<Site> sites) {
        if (sites == null || sites.isEmpty()) {
            return List.of();
        }
        List<Site> shown = new ArrayList<>();
        for (Site site : sites) {
            if (site != null && tradeHundredths(site.tradeHere()) > 0) {
                shown.add(site);
            }
        }
        return List.copyOf(shown);
    }

    /** Own installations tied for the most trade power already in the province. */
    public static List<Site> bestTerritory(List<Site> sites) {
        if (sites == null || sites.isEmpty()) {
            return List.of();
        }
        long best = Long.MIN_VALUE;
        boolean any = false;
        for (Site site : sites) {
            if (site == null || !site.ownRealm()) {
                continue;
            }
            any = true;
            best = Math.max(best, tradeHundredths(site.tradeHere()));
        }
        if (!any) {
            return List.of();
        }
        List<Site> shown = new ArrayList<>();
        for (Site site : sites) {
            if (site != null && site.ownRealm() && tradeHundredths(site.tradeHere()) == best) {
                shown.add(site);
            }
        }
        return List.copyOf(shown);
    }

    public static List<Site> byTradePower(List<Site> sites) {
        return sorted(sites, Comparator.comparingLong((Site site) -> tradeHundredths(site.tradeHere())).reversed());
    }

    /** Sites an existing hub can already reach. New track is not a connection. */
    public static List<Site> connected(List<Site> sites) {
        if (sites == null || sites.isEmpty()) {
            return List.of();
        }
        List<Site> shown = new ArrayList<>();
        for (Site site : sites) {
            if (site != null && site.connected()) {
                shown.add(site);
            }
        }
        return List.copyOf(shown);
    }

    public static List<Site> byArrival(List<Site> sites) {
        return sorted(sites, Comparator.comparingDouble(Site::arrivesHere).reversed()
                .thenComparing(Comparator.comparingDouble(Site::tradeHere).reversed()));
    }

    private static List<Site> sorted(List<Site> sites, Comparator<Site> order) {
        if (sites == null || sites.isEmpty()) {
            return List.of();
        }
        List<Site> sorted = new ArrayList<>();
        for (Site site : sites) {
            if (site != null) {
                sorted.add(site);
            }
        }
        sorted.sort(order);
        return List.copyOf(sorted);
    }

    private static boolean eligible(Guild guild, Faction host, Installation installation, List<Guild> guilds) {
        if (installation == null || installation.getId() == null || !hubKind(installation.getKind())) {
            return false;
        }
        int slots = hubSlots(installation);
        if (slots <= 0) {
            return false;
        }
        if (SupplyHubService.hasHub(guild.getSupplyHubs(), host.getId(), installation.getId())) {
            return false;
        }
        return SupplyHubService.countAt(host.getId(), installation.getId(), guilds) < slots;
    }

    private static List<Installation> activeSources(Guild guild, List<Guild> guilds) {
        List<Installation> sources = new ArrayList<>();
        if (guild == null || guild.getSupplyHubs() == null) {
            return sources;
        }
        for (SupplyHub hub : guild.getSupplyHubs()) {
            if (hub == null) {
                continue;
            }
            Installation installation = SupplyHubService.findInstallation(hub.ownerFactionId(), hub.installationId());
            if (installation == null) {
                continue;
            }
            boolean allowed = SupplyHubService.hubPermitted(guild, hub.ownerFactionId(), hub.installationId());
            SupplyHubService.HubStanding standing = SupplyHubService.standing(
                    guild, hub, true, allowed, hubSlots(installation),
                    SupplyHubService.atInstallation(hub.ownerFactionId(), hub.installationId(), guilds));
            if (standing.active()) {
                sources.add(installation);
            }
        }
        return sources;
    }

    private static double tradeHere(ProvinceManager provinces, Guild guild, int provinceId) {
        if (provinces == null || guild == null || guild.getId() == null) {
            return 0;
        }
        Province province = provinces.get(provinceId);
        if (province == null || !province.isValid()) {
            return 0;
        }
        return province.getRawGuildTrade(guild);
    }

    private static long tradeHundredths(double trade) {
        if (!Double.isFinite(trade) || trade <= 0) {
            return 0;
        }
        return Math.round(trade * 100.0);
    }

    private static boolean hubKind(InstallationKind kind) {
        return kind == InstallationKind.PORT
                || kind == InstallationKind.AIRPORT
                || kind == InstallationKind.TRAIN_STATION;
    }

    private static int hubSlots(Installation installation) {
        try {
            return InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel());
        } catch (IllegalStateException ex) {
            return SupplyHubService.defaultHubSlots(installation.getKind(), installation.getLevel());
        }
    }

    private static boolean sameRealm(Faction left, Faction right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && left.getId().equalsIgnoreCase(right.getId())) {
            return true;
        }
        return RelationManager.sameRealm(left, right);
    }
}
