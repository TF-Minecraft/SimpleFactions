package net.tfminecraft.simplefactions.map.infra;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.provinces.Province;

public final class TrackProvinceLookup {
    public record Point(double x, double y, double z) {}

    private TrackProvinceLookup() {}

    public static Set<Integer> collect(ProvinceGrid grid, Iterable<Point> points, Map<Integer, Province> provinces) {
        Set<Integer> ids = new HashSet<>();
        for (Point point : points) {
            int id = grid.getAt((int) Math.floor(point.x()), (int) Math.floor(point.z()));
            Province province = provinces.get(id);
            if (id != 0 && province != null && !province.isSea()) ids.add(id);
        }
        return Set.copyOf(ids);
    }
}
