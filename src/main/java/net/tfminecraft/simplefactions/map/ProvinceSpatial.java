package net.tfminecraft.simplefactions.map;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.SimpleFactions;

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
        long radiusSq = (long) radiusBlocks * radiusBlocks;
        long minX = Math.max(0L, (long) x - radiusBlocks);
        long maxX = Math.min(grid.getWidth() - 1L, (long) x + radiusBlocks);
        long minZ = Math.max(0L, (long) z - radiusBlocks);
        long maxZ = Math.min(grid.getHeight() - 1L, (long) z + radiusBlocks);

        for (long sampleZ = minZ; sampleZ <= maxZ; sampleZ++) {
            long dz = sampleZ - z;
            for (long sampleX = minX; sampleX <= maxX; sampleX++) {
                long dx = sampleX - x;
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }
                int provinceId = grid.getAt((int) sampleX, (int) sampleZ);
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
