package net.tfminecraft.simplefactions.guild.hub;

import java.util.OptionalDouble;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.guild.network.RailRoutes.Route;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceLookup;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceLookup.Point;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.tracks.TrackRegistry;

/**
 * The only class here that names VehicleFramework types, so it is loaded only once
 * the caller has checked that the plugin is enabled, or through the guarded route method.
 */
public final class VehicleFrameworkTracks {
    private VehicleFrameworkTracks() {
    }

    static OptionalDouble distance(String world, Installation from, Installation to) {
        TrackRegistry tracks = VehicleFramework.getTrackRegistry();
        if (tracks == null || world == null) {
            return OptionalDouble.empty();
        }
        return tracks.shortestRouteLength(
                world,
                from.getCenterX(),
                from.getCenterZ(),
                InstallationConfigLoader.getRadius(from.getKind()),
                to.getCenterX(),
                to.getCenterZ(),
                InstallationConfigLoader.getRadius(to.getKind()));
    }

    /** Empty when VehicleFramework is missing or too old to return route points. */
    public static Optional<Route> route(String world, Installation from, Installation to) {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("VehicleFramework")) return Optional.empty();
            TrackRegistry tracks = VehicleFramework.getTrackRegistry();
            if (tracks == null || world == null) return Optional.empty();
            return tracks.shortestRoute(world,
                    from.getCenterX(), from.getCenterZ(), InstallationConfigLoader.getRadius(from.getKind()),
                    to.getCenterX(), to.getCenterZ(), InstallationConfigLoader.getRadius(to.getKind()), 8)
                    .map(route -> new Route(route.length(), route.points().stream()
                            .map(point -> new Point(point.x(), point.y(), point.z())).toList()));
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    static Set<Integer> sample(String world, ProvinceGrid grid, Map<Integer, Province> provinces) {
        TrackRegistry tracks = VehicleFramework.getTrackRegistry();
        if (tracks == null || world == null) return Set.of();
        return TrackProvinceLookup.collect(grid, tracks.sampleTrack(world, 8).stream()
                .map(point -> new Point(point.x(), point.y(), point.z())).toList(), provinces);
    }
}
