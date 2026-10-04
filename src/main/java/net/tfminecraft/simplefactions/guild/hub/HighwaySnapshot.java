package net.tfminecraft.simplefactions.guild.hub;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.guild.hub.Highway.HubSite;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;

/**
 * The graph and each guild's active hubs, taken together on the server thread.
 * Recalculation and previews read this instead of installations or track.
 */
public final class HighwaySnapshot {
    private static volatile HighwaySnapshot current = new HighwaySnapshot(TradeGraph.live(), Map.of());

    private final TradeGraph graph;
    private final Map<String, Set<HubSite>> hubbed;
    private final Map<String, List<Link>> links;

    private HighwaySnapshot(TradeGraph graph, Map<String, Set<HubSite>> hubbed) {
        this.graph = graph == null ? TradeGraph.live() : graph;
        Map<String, Set<HubSite>> copied = new HashMap<>();
        Map<String, List<Link>> built = new HashMap<>();
        if (hubbed != null) {
            for (Map.Entry<String, Set<HubSite>> entry : hubbed.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                    continue;
                }
                Set<HubSite> sites = Set.copyOf(entry.getValue());
                copied.put(entry.getKey(), sites);
                List<Link> guildLinks = Highway.links(this.graph, sites);
                if (!guildLinks.isEmpty()) {
                    built.put(entry.getKey(), guildLinks);
                }
            }
        }
        this.hubbed = Map.copyOf(copied);
        this.links = Map.copyOf(built);
    }

    public static HighwaySnapshot current() {
        return current;
    }

    /** Replaces the shared snapshot. Used by refresh and tests. */
    public static void install(TradeGraph graph, Map<String, Set<HubSite>> hubbed) {
        current = new HighwaySnapshot(graph, hubbed);
    }

    public static HighwaySnapshot of(TradeGraph graph, Map<String, Set<HubSite>> hubbed) {
        return new HighwaySnapshot(graph, hubbed);
    }

    public TradeGraph graph() {
        return graph;
    }

    public Set<HubSite> hubbed(String guildId) {
        if (guildId == null) {
            return Set.of();
        }
        return hubbed.getOrDefault(guildId, Set.of());
    }

    public List<Link> links(String guildId) {
        if (guildId == null) {
            return List.of();
        }
        return links.getOrDefault(guildId, List.of());
    }
}
