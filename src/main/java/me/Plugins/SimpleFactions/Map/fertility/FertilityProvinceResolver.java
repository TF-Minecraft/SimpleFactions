package me.Plugins.SimpleFactions.Map.fertility;

import org.bukkit.Location;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Map.ProvinceGrid;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.SimpleFactions;

public final class FertilityProvinceResolver {
    private FertilityProvinceResolver() {}

    public static boolean isActive() {
        return Cache.provincesEnabled && Cache.mapEnabled;
    }

    public static int fertilityAt(int x, int z, ProvinceGrid grid, ProvinceManager manager) {
        if (grid == null || manager == null) {
            return 0;
        }
        int provinceId = grid.getAt(x, z);
        if (provinceId <= 0) {
            return 0;
        }
        return manager.get(provinceId).getFertility();
    }

    public static int fertilityAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return 0;
        }
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin == null) {
            return 0;
        }
        ProvinceGrid grid = plugin.getProvinceGrid();
        ProvinceManager manager = plugin.getProvinceManager();
        return fertilityAt(location.getBlockX(), location.getBlockZ(), grid, manager);
    }
}
