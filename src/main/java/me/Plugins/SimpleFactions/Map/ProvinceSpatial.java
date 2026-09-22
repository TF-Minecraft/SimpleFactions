package me.Plugins.SimpleFactions.Map;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.SimpleFactions;

/**
 * Spatial helpers over {@link ProvinceGrid} and {@link ProvinceManager}.
 */
public final class ProvinceSpatial {
    private ProvinceSpatial() {
    }

    public static boolean isSeaAt(int x, int z) {
        ProvinceGrid grid = SimpleFactions.getInstance().getProvinceGrid();
        if (grid == null) {
            return false;
        }
        int provinceId = grid.getAt(x, z);
        if (provinceId <= 0) {
            return false;
        }
        Province province = SimpleFactions.getInstance().getProvinceManager().get(provinceId);
        return province.isValid() && province.isSea();
    }

    public static boolean withinBlocksOfSea(int x, int z, int radiusBlocks) {
        if (radiusBlocks < 0) {
            return false;
        }
        ProvinceGrid grid = SimpleFactions.getInstance().getProvinceGrid();
        if (grid == null) {
            return false;
        }
        ProvinceManager manager = SimpleFactions.getInstance().getProvinceManager();
        int radiusSq = radiusBlocks * radiusBlocks;

        for (int dz = -radiusBlocks; dz <= radiusBlocks; dz++) {
            for (int dx = -radiusBlocks; dx <= radiusBlocks; dx++) {
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }
                int provinceId = grid.getAt(x + dx, z + dz);
                if (provinceId <= 0) {
                    continue;
                }
                Province province = manager.get(provinceId);
                if (province.isValid() && province.isSea()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean withinConfiguredPortSeaProximity(int x, int z) {
        return withinBlocksOfSea(x, z, Cache.portSeaProximityBlocks);
    }
}
