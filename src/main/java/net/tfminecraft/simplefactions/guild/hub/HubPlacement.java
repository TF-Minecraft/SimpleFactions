package net.tfminecraft.simplefactions.guild.hub;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.BuildFailure;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Network;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Where a guild may place a hub, and the sentence the Supply Hubs icon and screen show
 * when it cannot. The decision itself takes no server types.
 */
public final class HubPlacement {
    public static final String NEED_NETWORK =
            "You need a hub in that network first. Place one on your own land.";
    public static final String NO_INFRASTRUCTURE =
            "There is no infrastructure to support a trade network in your land.";
    public static final String NOT_GLOBAL =
            "You are not connected to the global network.";
    public static final String NETWORK_FULL =
            "Your network can't support more trade hubs.";

    /** The node's network, the networks the guild already has a hub in, and whether the node is its land. */
    public record Inputs(Network network, Set<Network> joined, boolean ownTerritory) {
        public Inputs {
            joined = joined == null ? Set.of() : Set.copyOf(joined);
        }

        /** Own land, so {@link #mayPlace} allows the hub. Tests that are not about networks use this. */
        public static Inputs ownLand() {
            return new Inputs(null, Set.of(), true);
        }
    }

    private HubPlacement() {
    }

    /** The refusal players see, in the same colour as the other hub refusals. */
    public static String refusal() {
        return "§c" + NEED_NETWORK;
    }

    /** A lore or title line. A sentence that already has a colour code is left as it is. */
    public static String display(String sentence) {
        if (sentence == null || sentence.isEmpty() || sentence.charAt(0) == '§') {
            return sentence;
        }
        return "§7" + sentence;
    }

    /**
     * Own territory, or the guild already has a hub in that node's network.
     * A null network is not one the guild has joined.
     */
    public static boolean mayPlace(Network nodeNetwork, Set<Network> networksWithHub, boolean ownTerritory) {
        if (ownTerritory) {
            return true;
        }
        if (nodeNetwork == null || networksWithHub == null) {
            return false;
        }
        return networksWithHub.contains(nodeNetwork);
    }

    /** Every saved hub counts, active or dormant. A hub whose stop is not a trade node joins nothing. */
    public static Set<Network> joinedNetworks(TradeGraph graph, List<SupplyHub> hubs) {
        if (graph == null || hubs == null || hubs.isEmpty()) {
            return Set.of();
        }
        Set<Network> joined = new HashSet<>();
        for (SupplyHub hub : hubs) {
            if (hub == null) {
                continue;
            }
            Network network = graph.networkOf(graph.node(hub.ownerFactionId(), hub.installationId()));
            if (network != null) {
                joined.add(network);
            }
        }
        return Set.copyOf(joined);
    }

    /** Own faction, or the same realm. */
    public static boolean ownLand(Guild guild, String ownerFactionId) {
        Faction guildFaction = guild == null ? null : guild.getFaction();
        if (guildFaction == null || ownerFactionId == null || guildFaction.getId() == null) {
            return false;
        }
        if (guildFaction.getId().equalsIgnoreCase(ownerFactionId)) {
            return true;
        }
        Faction owner = FactionManager.getByString(ownerFactionId);
        return owner != null && RelationManager.sameRealm(owner, guildFaction);
    }

    /**
     * The sentence for the icon and the screen, or null when the guild can still place a hub.
     * At the hub limit the wording is the existing limit refusal.
     */
    public static String emptyState(
            boolean anyHub,
            boolean tradeNodeInOwnTerritory,
            boolean atHubLimit,
            boolean eligibleNodeRemains,
            int largestJoinedNetwork,
            int largestNetwork,
            int hubLimit) {
        if (!anyHub && !tradeNodeInOwnTerritory) {
            return NO_INFRASTRUCTURE;
        }
        if (atHubLimit && eligibleNodeRemains) {
            return SupplyHubService.buildFailureMessage(BuildFailure.HUB_LIMIT, null, hubLimit);
        }
        if (!eligibleNodeRemains && largestNetwork > 0 && largestJoinedNetwork < largestNetwork) {
            return NOT_GLOBAL;
        }
        if (!eligibleNodeRemains && largestNetwork > 0) {
            return NETWORK_FULL;
        }
        return null;
    }

    /** Reads the current graph. Safe when the guild menu redraws; it does not recalculate trade. */
    public static String forGuild(Guild guild) {
        if (guild == null) {
            return null;
        }
        TradeGraph graph = HighwaySnapshot.current().graph();
        if (graph == null) {
            graph = TradeGraph.live();
        }
        List<SupplyHub> hubs = guild.getSupplyHubs() == null ? List.of() : guild.getSupplyHubs();
        boolean anyHub = !hubs.isEmpty();
        Set<Network> joined = joinedNetworks(graph, hubs);
        int largestJoined = 0;
        for (Network network : joined) {
            largestJoined = Math.max(largestJoined, network.size());
        }
        int largest = 0;
        for (Network network : graph.networks()) {
            largest = Math.max(largest, network.size());
        }
        boolean territory = false;
        boolean eligible = false;
        for (Node node : graph.nodes()) {
            boolean own = ownLand(guild, node.ownerFactionId());
            if (own) {
                territory = true;
            }
            if (eligible) {
                continue;
            }
            if (SupplyHubService.hasHub(hubs, node.ownerFactionId(), node.installationId())) {
                continue;
            }
            if (node.hubSlots() <= 0) {
                continue;
            }
            if (!mayPlace(graph.networkOf(node), joined, own)) {
                continue;
            }
            if (SupplyHubService.countLoaded(node.ownerFactionId(), node.installationId()) >= node.hubSlots()) {
                continue;
            }
            eligible = true;
        }
        int limit = SupplyHubService.limit(guild);
        return emptyState(anyHub, territory, hubs.size() >= limit, eligible, largestJoined, largest, limit);
    }
}
