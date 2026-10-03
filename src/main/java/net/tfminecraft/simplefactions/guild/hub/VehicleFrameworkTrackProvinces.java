package net.tfminecraft.simplefactions.guild.hub;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceLookup;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceLookup.Point;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.tracks.TrackRegistry;
import net.tfminecraft.vehicleframework.tracks.TrackSamplePoint;

/** The only track sampler here that names VehicleFramework's sample point type. */
public final class VehicleFrameworkTrackProvinces {
    private VehicleFrameworkTrackProvinces() {}

    public static Set<Integer> sample(String world, ProvinceGrid grid, Map<Integer, Province> provinces) {
        TrackRegistry tracks = VehicleFramework.getTrackRegistry();
        if (tracks == null || world == null) return Set.of();
        List<TrackSamplePoint> samples = tracks.sampleTrack(world, 8);
        return TrackProvinceLookup.collect(grid, samples.stream()
                .map(point -> new Point(point.x(), point.y(), point.z())).toList(), provinces);
    }
}
