package net.tfminecraft.simplefactions.guild.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.hub.VehicleFrameworkTracks;
import net.tfminecraft.simplefactions.guild.network.RailRoutes.Route;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.ProvinceData;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.Site;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;

/** Takes the server-thread data needed by the pure builder. */
final class LiveTradeGraph {
    private static final RailRouteCache routes = new RailRouteCache(System::currentTimeMillis);

    private LiveTradeGraph() { }

    static TradeGraph build(ProvinceManager provinces) {
        List<Site> sites = new ArrayList<>();
        Map<Installation, String> identities = new IdentityHashMap<>();
        if (FactionManager.factions != null) {
            for (Faction faction : FactionManager.factions) {
                if (faction == null || faction.getId() == null || faction.getInstallationHandler() == null) continue;
                // Pending construction is separate from getAll().
                for (Installation installation : faction.getInstallationHandler().getAll()) {
                    if (installation == null || installation.getKind() == InstallationKind.FORT) continue;
                    sites.add(new Site(faction.getId(), installation, true));
                    identities.put(installation,
                            faction.getId() + ":" + installation.getProvince() + ":" + installation.getId());
                }
            }
        }
        Map<Integer, ProvinceData> map = new HashMap<>();
        for (Province province : provinces.getProvinces()) {
            if (province.isValid()) {
                map.put(province.getId(), new ProvinceData(province.getTerrain(), province.getNeighbours()));
            }
        }
        SimpleFactions plugin = SimpleFactions.getInstance();
        ProvinceGrid grid = plugin == null ? null : plugin.getProvinceGrid();
        String world = Cache.worldName;
        return TradeGraphBuilder.build(sites, map, (from, to) -> {
            String left = identities.get(from);
            String right = identities.get(to);
            if (left == null || right == null) return Optional.empty();
            return routes.route(left, right, () -> trackRoute(world, from, to));
        }, point -> grid == null ? 0 : grid.getAt(
                (int) Math.floor(point.x()), (int) Math.floor(point.z())));
    }

    /**
     * The plugin check sits outside {@link VehicleFrameworkTracks}, matching hub distances.
     * A missing or older plugin must not fail the refresh.
     */
    private static Optional<Route> trackRoute(String world, Installation from, Installation to) {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("VehicleFramework")) return Optional.empty();
            return VehicleFrameworkTracks.route(world, from, to);
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    static void forgetRoutes() {
        routes.forget();
    }
}
