package net.tfminecraft.simplefactions.guild.hub;

import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.provinces.Province;

/** Provinces crossed by railway track. Hub routes use the same VehicleFramework boundary. */
public final class VehicleFrameworkTrackProvinces {
    private VehicleFrameworkTrackProvinces() {}

    public static Set<Integer> sample(String world, ProvinceGrid grid, Map<Integer, Province> provinces) {
        return VehicleFrameworkTracks.sample(world, grid, provinces);
    }
}
