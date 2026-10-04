package net.tfminecraft.simplefactions.guild.hub;

import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.provinces.Province;

/** Track infrastructure sampling uses the same VehicleFramework boundary as hub routes. */
public final class VehicleFrameworkTrackProvinces {
    private VehicleFrameworkTrackProvinces() {}

    public static Set<Integer> sample(String world, ProvinceGrid grid, Map<Integer, Province> provinces) {
        return VehicleFrameworkTracks.sample(world, grid, provinces);
    }
}
