package net.tfminecraft.simplefactions.guild.hub;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.guild.hub.Highway.HubSite;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.HubStanding;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.enums.GuildModifier;

/**
 * Each guild's active hubs on the shared trade graph.
 *
 * <p>Which hubs are active is worked out once per refresh, on the server thread, and kept with
 * the graph. Trade calculation and income previews read that snapshot. They do not measure
 * track or look up installations.
 */
public final class HubNetwork {
    private static volatile Map<String, List<Link>> linksOverride;

    private HubNetwork() {
    }

    /**
     * Connections between this guild's active hubs, in both directions. Empty when the guild
     * may not have hubs. Safe on any thread.
     */
    public static List<Link> linksFor(Guild guild) {
        if (guild == null || guild.getId() == null || !SupplyHubService.allowsSupplyHubs(guild)) {
            return List.of();
        }
        Map<String, List<Link>> override = linksOverride;
        if (override != null) {
            return override.getOrDefault(guild.getId(), List.of());
        }
        return HighwaySnapshot.current().links(guild.getId());
    }

    /** Nodes where this guild's hubs are active right now. Empty when the guild may not have hubs. */
    public static Set<HubSite> hubbedNodes(Guild guild) {
        if (guild == null || guild.getId() == null || !SupplyHubService.allowsSupplyHubs(guild)) {
            return Set.of();
        }
        return HighwaySnapshot.current().hubbed(guild.getId());
    }

    /** Rebuilds the graph, then each guild's active hubs. Server thread only. */
    public static void refresh(ProvinceManager provinces) {
        TradeGraph.refresh(provinces);
        TradeGraph graph = TradeGraph.live();
        Map<String, Set<HubSite>> hubbed = new HashMap<>();
        List<Guild> guilds = SupplyHubService.allGuilds();
        for (Guild guild : guilds) {
            if (guild == null || guild.getId() == null) {
                continue;
            }
            Set<HubSite> sites = activeHubs(guild, guilds, graph);
            if (!sites.isEmpty()) {
                hubbed.put(guild.getId(), sites);
            }
        }
        HighwaySnapshot.install(graph, hubbed);
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
     * Forgets measured track routes, so the next refresh reads the track again. Called once a day,
     * which is when cut track stops carrying an edge.
     */
    public static void forgetRoutes() {
        TradeGraph.forgetRoutes();
    }

    /**
     * Trade power a new hub at {@code target} would receive from the guild's active hubs.
     * Reads the shared graph. Server thread only.
     */
    public static double potentialTrade(Guild guild, Installation target, ProvinceManager provinces) {
        if (guild == null || target == null || provinces == null) {
            return 0;
        }
        HighwaySnapshot snapshot = HighwaySnapshot.current();
        Node targetNode = findNode(snapshot.graph(), target);
        if (targetNode == null) {
            return 0;
        }
        double bonus = GuildModifierOverride.resolve(guild, GuildModifier.HUB_TRADE);
        double best = 0;
        for (HubSite site : hubbedNodes(guild)) {
            Node source = snapshot.graph().node(site.ownerFactionId(), site.installationId());
            if (source == null || source.equals(targetNode)) {
                continue;
            }
            Link link = Highway.bestLink(snapshot.graph(), source, targetNode);
            if (link == null || !provinces.contains(source.provinceId())) {
                continue;
            }
            double power = provinces.get(source.provinceId()).getRawGuildTrade(guild);
            best = Math.max(best, power * link.boostedTradeFactor(bonus));
        }
        return best;
    }

    /** The graph edge from one installation to the other, or null when the graph has none. */
    public static Link connect(Installation from, Installation to, ProvinceManager provinces) {
        if (from == null || to == null || from == to) {
            return null;
        }
        TradeGraph graph = HighwaySnapshot.current().graph();
        Node source = findNode(graph, from);
        Node target = findNode(graph, to);
        if (source == null || target == null) {
            return null;
        }
        return Highway.bestLink(graph, source, target);
    }

    /** Replaces the shared graph and hubbed nodes tests read. Null clears both. */
    public static void setHighwayForTests(TradeGraph graph, Map<String, Set<HubSite>> hubbed) {
        linksOverride = null;
        if (graph == null && hubbed == null) {
            TradeGraph.setLiveForTests(null);
            HighwaySnapshot.install(TradeGraph.live(), Map.of());
            return;
        }
        TradeGraph chosen = graph == null ? TradeGraph.live() : graph;
        TradeGraph.setLiveForTests(chosen);
        HighwaySnapshot.install(chosen, hubbed == null ? Map.of() : hubbed);
    }

    /** Snapshot-only link list for menus. Null clears it. Does not move trade. */
    public static void setLinksForTests(Map<String, List<Link>> replacement) {
        if (replacement == null) {
            linksOverride = null;
            return;
        }
        Map<String, List<Link>> copied = new HashMap<>();
        for (Map.Entry<String, List<Link>> entry : replacement.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copied.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        linksOverride = Map.copyOf(copied);
    }

    /** Hubs that pass the same standing test the network used before the graph. */
    static Set<HubSite> activeHubs(Guild guild, List<Guild> guilds, TradeGraph graph) {
        Set<HubSite> sites = new HashSet<>();
        if (guild == null || guild.getSupplyHubs() == null || graph == null) {
            return sites;
        }
        for (SupplyHub hub : guild.getSupplyHubs()) {
            if (hub == null) {
                continue;
            }
            Installation installation = SupplyHubService.findInstallation(
                    hub.ownerFactionId(), hub.installationId());
            if (installation == null || graph.node(hub.ownerFactionId(), hub.installationId()) == null) {
                continue;
            }
            HubStanding standing = SupplyHubService.standing(
                    guild, hub,
                    true,
                    SupplyHubService.hubPermitted(guild, hub.ownerFactionId(), hub.installationId()),
                    InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel()),
                    SupplyHubService.atInstallation(hub.ownerFactionId(), hub.installationId(), guilds));
            if (standing.active()) {
                sites.add(new HubSite(hub.ownerFactionId(), hub.installationId()));
            }
        }
        return sites;
    }

    static Node findNode(TradeGraph graph, Installation installation) {
        if (graph == null || installation == null || installation.getId() == null) {
            return null;
        }
        String owner = SupplyHubService.owningFactionId(installation);
        if (owner != null) {
            Node node = graph.node(owner, installation.getId());
            if (node != null) {
                return node;
            }
        }
        Node found = null;
        for (Node node : graph.nodes()) {
            if (node.provinceId() != installation.getProvince()
                    || !node.installationId().equalsIgnoreCase(installation.getId())) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = node;
        }
        return found;
    }
}
