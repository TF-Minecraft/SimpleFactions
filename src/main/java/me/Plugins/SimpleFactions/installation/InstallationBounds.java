package me.Plugins.SimpleFactions.installation;

import java.util.Locale;

import org.bukkit.Location;

import me.Plugins.SimpleFactions.Loaders.InstallationConfigLoader;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattlePlacementValidator;

public final class InstallationBounds {
    private InstallationBounds() {}

    public static double horizontalDistanceBlocks(int centerX, int centerZ, Location location) {
        if (location == null) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = location.getBlockX() - centerX;
        double dz = location.getBlockZ() - centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static String formatDistance(double blocks) {
        return String.format(Locale.US, "%.1f", blocks);
    }

    public static boolean isWithinRadius(Installation installation, Location location) {
        if (installation == null || location == null) {
            return false;
        }
        int radius = InstallationConfigLoader.getRadius(installation.getKind());
        return horizontalDistanceBlocks(
                installation.getCenterX(),
                installation.getCenterZ(),
                location) <= radius;
    }

    public static boolean isCorrectProvince(Installation installation, Location location) {
        if (installation == null || location == null) {
            return false;
        }
        return provinceAt(location) == installation.getProvince();
    }

    public static int provinceAt(Location location) {
        return BattlePlacementValidator.provinceAt(location);
    }
}
