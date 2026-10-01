package net.tfminecraft.simplefactions.guild.hub;

import java.util.OptionalDouble;

import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.tracks.TrackRegistry;

/**
 * The only class here that names VehicleFramework types, so it is loaded only once
 * {@link HubNetwork} has checked that the plugin is enabled.
 */
final class VehicleFrameworkTracks {
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
}
