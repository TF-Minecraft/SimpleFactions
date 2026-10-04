package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.hub.Highway.HubSite;
import net.tfminecraft.simplefactions.guild.network.RailRoutes;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.ProvinceData;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.Site;
import net.tfminecraft.simplefactions.installation.Installation;

/** Small graphs for highway tests. Rail joins only the pairs the predicate accepts. */
public final class TestGraphs {
    private TestGraphs() {
    }

    public static TradeGraph rail(String owner, List<Installation> sites, BiPredicate<Installation, Installation> join, double length) {
        Map<Integer, ProvinceData> provinces = new HashMap<>();
        List<Site> built = new ArrayList<>();
        for (Installation site : sites) {
            built.add(new Site(owner, site, 1, true));
            provinces.putIfAbsent(site.getProvince(), new ProvinceData(Terrain.PLAINS, Set.of()));
        }
        return TradeGraphBuilder.build(built, provinces, (from, to) -> {
            if (!join.test(from, to) && !join.test(to, from)) {
                return Optional.empty();
            }
            return Optional.of(new RailRoutes.Route(length, List.of()));
        }, point -> 0);
    }

    public static TradeGraph linked(String owner, double length, Installation... sites) {
        return rail(owner, List.of(sites), (from, to) -> true, length);
    }

    public static Set<HubSite> hubs(String owner, Installation... sites) {
        Set<HubSite> hubbed = new java.util.HashSet<>();
        for (Installation site : sites) {
            hubbed.add(new HubSite(owner, site.getId()));
        }
        return hubbed;
    }
}
