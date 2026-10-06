package net.tfminecraft.simplefactions.guild.hub;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** How far a lone train station pushes trade along track that has no second station. */
public final class OpenTrackSettings {
    public static final double DEFAULT_SHARE = 0.75;
    public static final double DEFAULT_KEPT = 0.85;
    public static final double DEFAULT_RANGE = 2500;

    private static volatile boolean enabled = true;
    private static volatile double share = DEFAULT_SHARE;
    private static volatile double keptPer1000 = DEFAULT_KEPT;
    private static volatile double rangeBlocks = DEFAULT_RANGE;

    private OpenTrackSettings() {
    }

    public static void load(FileConfiguration config) {
        ConfigurationSection open = config == null
                ? null : config.getConfigurationSection("installation-trade.open-track");
        enabled = open == null || open.getBoolean("enabled", true);
        share = nonNegative(open == null ? DEFAULT_SHARE : open.getDouble("share", DEFAULT_SHARE), DEFAULT_SHARE);
        keptPer1000 = kept(open == null
                ? DEFAULT_KEPT : open.getDouble("kept-per-1000-blocks", DEFAULT_KEPT));
        double range = open == null ? DEFAULT_RANGE : open.getDouble("range-blocks", DEFAULT_RANGE);
        rangeBlocks = Double.isFinite(range) && range > 0 ? range : 0;
    }

    public static void reset() {
        enabled = true;
        share = DEFAULT_SHARE;
        keptPer1000 = DEFAULT_KEPT;
        rangeBlocks = DEFAULT_RANGE;
    }

    public static boolean enabled() {
        return enabled;
    }

    /** Multiplied by the rail trade share. The product is capped at {@link HubTransport#MAX_SHARE}. */
    public static double share() {
        return share;
    }

    public static double keptPer1000() {
        return keptPer1000;
    }

    public static double rangeBlocks() {
        return rangeBlocks;
    }

    private static double nonNegative(double value, double fallback) {
        if (!Double.isFinite(value) || value < 0) {
            return fallback;
        }
        return value;
    }

    /** Same cap as a line, so distance loss cannot be turned off by config. */
    private static double kept(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return DEFAULT_KEPT;
        }
        return Math.min(value, HubTransport.MAX_SHARE);
    }
}
