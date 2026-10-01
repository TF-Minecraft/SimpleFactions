package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Mode;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Rates;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.HubStanding;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.SeaConnectivity;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * The connections between each guild's active supply hubs.
 *
 * <p>Working out a connection reads installations, permits and train track, which is only safe
 * on the server thread. The trade calculation also runs off that thread for income previews, so
 * it never works connections out itself: it reads the last set built by {@link #refresh}.
 */
public final class HubNetwork {
    /** Along-track distance between two stations, or empty when no track joins them. */
    @FunctionalInterface
    public interface RailRoutes {
        OptionalDouble distance(Installation from, Installation to);
    }

    private static volatile Map<String, List<Link>> links = Map.of();
    private static final Map<String, OptionalDouble> railDistances = new ConcurrentHashMap<>();
    private static RailRoutes railRoutes = HubNetwork::trackDistance;

    private HubNetwork() {
    }

    /** Connections that carry this guild's power, in both directions. Safe on any thread. */
    public static List<Link> linksFor(Guild guild) {
        if (guild == null || guild.getId() == null) {
            return List.of();
        }
        return links.getOrDefault(guild.getId(), List.of());
    }

    /** Rebuilds every guild's connections. Server thread only. */
    public static void refresh(ProvinceManager provinces) {
        Map<String, List<Link>> built = new HashMap<>();
        List<Guild> guilds = SupplyHubService.allGuilds();
        for (Guild guild : guilds) {
            if (guild == null || guild.getId() == null || guild.getSupplyHubs().size() < 2) {
                continue;
            }
            List<Link> found = linksBetween(activeSites(guild, guilds), provinces);
            if (!found.isEmpty()) {
                built.put(guild.getId(), List.copyOf(found));
            }
        }
        links = Map.copyOf(built);
    }

    /** Refreshes when called for the live province data on the server thread; otherwise no-op. */
    public static void refreshIfLive(ProvinceManager provinces) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin == null || plugin.getProvinceManager() != provinces) {
            return;
        }
        try {
            if (!Bukkit.isPrimaryThread()) {
                return;
            }
        } catch (RuntimeException | LinkageError e) {
            return;
        }
        refresh(provinces);
    }

    /**
     * Forgets measured track distances, so the next refresh measures the track again. Called once
     * a day, which is when cut track stops carrying a link.
     */
    public static void forgetRoutes() {
        railDistances.clear();
    }

    /**
     * Trade power a new hub at {@code target} would receive from the guild's existing hubs,
     * given the guild's power at each of them. Server thread only.
     */
    public static double potentialTrade(
            Guild guild, Installation target, ProvinceManager provinces) {
        if (guild == null || target == null || provinces == null) {
            return 0;
        }
        double best = 0;
        for (Installation source : activeSites(guild, SupplyHubService.allGuilds())) {
            Link link = connect(source, target, provinces);
            if (link == null) {
                continue;
            }
            double power = provinces.get(source.getProvince()).getRawGuildTrade(guild);
            best = Math.max(best, power * link.tradeFactor());
        }
        return best;
    }

    /** The connection from one installation to another, or null when nothing joins them. */
    public static Link connect(Installation from, Installation to, ProvinceManager provinces) {
        if (from == null || to == null || from == to || from.getProvince() == to.getProvince()) {
            return null;
        }
        Mode mode = HubTransport.modeBetween(from.getKind(), to.getKind());
        if (mode == null) {
            return null;
        }
        Rates rates = HubTransport.rates(mode);
        double distance;
        switch (mode) {
            case RAIL:
                OptionalDouble track = railDistance(from, to);
                if (track.isEmpty()) {
                    return null;
                }
                distance = track.getAsDouble();
                break;
            case SEA:
                if (SeaConnectivity.sharedSeaProvinces(
                        provinces, List.of(from.getProvince()), List.of(to.getProvince())).isEmpty()) {
                    return null;
                }
                distance = straightLine(from, to);
                break;
            default:
                distance = straightLine(from, to);
                break;
        }
        if (!Double.isFinite(distance) || distance < 0 || !HubTransport.inRange(rates, distance)) {
            return null;
        }
        return HubTransport.link(from.getProvince(), to.getProvince(), mode, distance);
    }

    public static void setLinksForTests(Map<String, List<Link>> replacement) {
        links = replacement == null ? Map.of() : Map.copyOf(replacement);
    }

    public static void setRailRoutesForTests(RailRoutes routes) {
        railRoutes = routes == null ? HubNetwork::trackDistance : routes;
        railDistances.clear();
    }

    static List<Link> linksBetween(List<Installation> sites, ProvinceManager provinces) {
        List<Link> found = new ArrayList<>();
        for (Installation from : sites) {
            for (Installation to : sites) {
                Link link = connect(from, to, provinces);
                if (link != null) {
                    found.add(link);
                }
            }
        }
        return found;
    }

    /** Installations of the guild's hubs that are active right now. */
    private static List<Installation> activeSites(Guild guild, List<Guild> guilds) {
        List<Installation> sites = new ArrayList<>();
        String guildFactionId = guild.getFaction() == null ? null : guild.getFaction().getId();
        for (SupplyHub hub : guild.getSupplyHubs()) {
            Installation installation =
                    SupplyHubService.findInstallation(hub.ownerFactionId(), hub.installationId());
            if (installation == null) {
                continue;
            }
            Faction owner = FactionManager.getByString(hub.ownerFactionId());
            boolean permit = owner != null && owner.hasHubPermit(guild.getId());
            HubStanding standing = SupplyHubService.standing(
                    hub,
                    true,
                    SupplyHubService.ownerAllows(guildFactionId, hub.ownerFactionId(), permit),
                    InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel()),
                    SupplyHubService.atInstallation(hub.ownerFactionId(), hub.installationId(), guilds));
            if (standing.active()) {
                sites.add(installation);
            }
        }
        return sites;
    }

    private static OptionalDouble railDistance(Installation from, Installation to) {
        String a = from.getProvince() + ":" + from.getId();
        String b = to.getProvince() + ":" + to.getId();
        String key = a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
        OptionalDouble known = railDistances.get(key);
        if (known != null) {
            return known;
        }
        // Only a found route is remembered: stations with no track yet are asked about again,
        // so a line connects as soon as it is finished rather than at the next daily check.
        OptionalDouble measured = railRoutes.distance(from, to);
        if (measured.isPresent()) {
            railDistances.put(key, measured);
        }
        return measured;
    }

    /** Asks VehicleFramework. Empty when it is missing or too old to answer. */
    private static OptionalDouble trackDistance(Installation from, Installation to) {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("VehicleFramework")) {
                return OptionalDouble.empty();
            }
            return VehicleFrameworkTracks.distance(Cache.worldName, from, to);
        } catch (RuntimeException | LinkageError e) {
            return OptionalDouble.empty();
        }
    }

    private static double straightLine(Installation from, Installation to) {
        double dx = from.getCenterX() - to.getCenterX();
        double dz = from.getCenterZ() - to.getCenterZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
