package net.tfminecraft.simplefactions.guild.hub;

import java.util.EnumMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;

/**
 * How much of a guild's trade power and production one hop passes to the next stop.
 *
 * <p>The mode's base share is reduced by distance. Nothing is added along the way. Every share
 * is below 1, so passing power round a ring of stops can never grow it.
 */
public final class HubTransport {
    /** Shares are capped here so a chain of hubs always loses power. */
    public static final double MAX_SHARE = 0.95;

    public enum Mode {
        RAIL("rail", InstallationKind.TRAIN_STATION),
        SEA("sea", InstallationKind.PORT),
        AIR("air", InstallationKind.AIRPORT);

        private final String key;
        private final InstallationKind kind;

        Mode(String key, InstallationKind kind) {
            this.key = key;
            this.kind = kind;
        }

        public String getKey() {
            return key;
        }

        public InstallationKind getKind() {
            return kind;
        }
    }

    /**
     * @param trade share of trade power delivered before distance loss
     * @param production share of production delivered before distance loss
     * @param keptPer1000 share that survives each 1000 blocks travelled
     */
    public record Rates(double trade, double production, double keptPer1000) {
    }

    /**
     * One direction of a connection between two of a guild's hubs.
     *
     * @param tradeFactor trade power at {@code toProvince} per point at {@code fromProvince}
     * @param productionFactor the same for production
     */
    public record Link(
            int fromProvince,
            int toProvince,
            String fromFactionId,
            String fromInstallationId,
            String toFactionId,
            String toInstallationId,
            Mode mode,
            double distance,
            double tradeFactor,
            double productionFactor) {

        public Link(int fromProvince, int toProvince, Mode mode, double distance,
                double tradeFactor, double productionFactor) {
            this(fromProvince, toProvince, null, null, null, null, mode,
                    distance, tradeFactor, productionFactor);
        }

        /** The boosted share is capped before the cached distance loss is applied. */
        public double boostedTradeFactor(double bonus) {
            return boostedFactor(tradeFactor, mode, distance, bonus, true);
        }

        /** The boosted share is capped before the cached distance loss is applied. */
        public double boostedProductionFactor(double bonus) {
            return boostedFactor(productionFactor, mode, distance, bonus, false);
        }
    }

    private static final Map<Mode, Rates> DEFAULTS = new EnumMap<>(Mode.class);
    private static volatile Map<Mode, Rates> rates;

    static {
        DEFAULTS.put(Mode.RAIL, new Rates(0.40, 0.80, 0.90));
        DEFAULTS.put(Mode.SEA, new Rates(0.30, 0.70, 0.85));
        DEFAULTS.put(Mode.AIR, new Rates(0.20, 0.50, 0.80));
        rates = new EnumMap<>(DEFAULTS);
    }

    private HubTransport() {
    }

    public static void loadConfig(FileConfiguration config) {
        Map<Mode, Rates> loaded = new EnumMap<>(DEFAULTS);
        ConfigurationSection transport = transportSection(config);
        if (transport != null) {
            for (Mode mode : Mode.values()) {
                ConfigurationSection section = transport.getConfigurationSection(mode.getKey());
                if (section == null) {
                    continue;
                }
                Rates fallback = DEFAULTS.get(mode);
                loaded.put(mode, new Rates(
                        share(section.getDouble("trade", fallback.trade())),
                        share(section.getDouble("production", fallback.production())),
                        share(section.getDouble("kept-per-1000-blocks", fallback.keptPer1000()))));
            }
        }
        rates = loaded;
    }

    /** installation-trade.transport, or the old supply-hubs.transport when that section is absent. */
    private static ConfigurationSection transportSection(FileConfiguration config) {
        if (config == null) {
            return null;
        }
        if (config.getConfigurationSection("installation-trade") != null) {
            return config.getConfigurationSection("installation-trade.transport");
        }
        return config.getConfigurationSection("supply-hubs.transport");
    }

    public static void resetConfig() {
        rates = new EnumMap<>(DEFAULTS);
    }

    public static Rates rates(Mode mode) {
        return rates.get(mode);
    }

    /** The mode that joins two installations of these kinds, or null when none does. */
    public static Mode modeBetween(InstallationKind from, InstallationKind to) {
        if (from == null || from != to) {
            return null;
        }
        for (Mode mode : Mode.values()) {
            if (mode.getKind() == from) {
                return mode;
            }
        }
        return null;
    }

    /** Share delivered over {@code distance} blocks, given the share delivered at no distance. */
    public static double delivered(double share, double keptPer1000, double distance) {
        if (share <= 0 || keptPer1000 <= 0) {
            return 0;
        }
        return share * Math.pow(keptPer1000, Math.max(0, distance) / 1000.0);
    }

    public static Link link(Installation from, String fromFactionId, Installation to, String toFactionId, Mode mode, double distance) {
        Rates modeRates = rates(mode);
        return new Link(
                from.getProvince(),
                to.getProvince(),
                fromFactionId,
                from.getId(),
                toFactionId,
                to.getId(),
                mode,
                distance,
                delivered(modeRates.trade(), modeRates.keptPer1000(), distance),
                delivered(modeRates.production(), modeRates.keptPer1000(), distance));
    }

    public static Link link(int fromProvince, int toProvince, Mode mode, double distance) {
        Rates modeRates = rates(mode);
        return new Link(fromProvince, toProvince, mode, distance,
                delivered(modeRates.trade(), modeRates.keptPer1000(), distance),
                delivered(modeRates.production(), modeRates.keptPer1000(), distance));
    }

    private static double boostedFactor(double factor, Mode mode, double distance, double bonus, boolean trade) {
        Rates modeRates = rates(mode);
        double baseShare = trade ? modeRates.trade() : modeRates.production();
        if (factor <= 0 || baseShare <= 0) {
            return 0;
        }
        double distanceLoss = delivered(1.0, modeRates.keptPer1000(), distance);
        if (distanceLoss <= 0) {
            return 0;
        }
        double unboostedShare = Math.min(MAX_SHARE, factor / distanceLoss);
        double boostedShare = Math.min(MAX_SHARE, unboostedShare * (1.0 + Math.max(0, bonus)));
        return delivered(boostedShare, modeRates.keptPer1000(), distance);
    }

    private static double share(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return 0;
        }
        return Math.min(value, MAX_SHARE);
    }
}
