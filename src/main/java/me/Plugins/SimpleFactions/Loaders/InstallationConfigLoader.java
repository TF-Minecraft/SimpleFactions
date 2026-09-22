package me.Plugins.SimpleFactions.Loaders;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.InstallationKindConfig;

public final class InstallationConfigLoader {
    private static final Map<InstallationKind, InstallationKindConfig> byKind =
            new EnumMap<>(InstallationKind.class);
    private static int consentProximityBlocks = 20;
    private static int transferRequestTimeoutSeconds = 60;

    private InstallationConfigLoader() {}

    public static void load(File installationsYaml) {
        byKind.clear();

        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(installationsYaml);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
            fail("Failed to load installations.yml");
        }

        if (!config.contains("consent-proximity-blocks")) {
            fail("installations.yml consent-proximity-blocks is required");
        }
        if (!config.contains("transfer-request-timeout-seconds")) {
            fail("installations.yml transfer-request-timeout-seconds is required");
        }

        consentProximityBlocks = config.getInt("consent-proximity-blocks");
        transferRequestTimeoutSeconds = config.getInt("transfer-request-timeout-seconds");
        if (consentProximityBlocks < 0) {
            fail("installations.yml consent-proximity-blocks must be >= 0");
        }
        if (transferRequestTimeoutSeconds <= 0) {
            fail("installations.yml transfer-request-timeout-seconds must be > 0");
        }

        Set<String> knownCategories = VehiclesConfigLoader.getCategoryIds();

        for (InstallationKind kind : InstallationKind.values()) {
            String key = kind.getCommandName();
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                fail("installations.yml missing required section: " + key);
            }

            if (!section.contains("daily-upkeep")) {
                fail("installations.yml " + key + ".daily-upkeep is required");
            }
            if (!section.contains("construction-time")) {
                fail("installations.yml " + key + ".construction-time is required");
            }
            if (!section.contains("radius")) {
                fail("installations.yml " + key + ".radius is required");
            }

            double dailyUpkeep = section.getDouble("daily-upkeep");
            int constructionTimeSeconds = section.getInt("construction-time");
            int radius = section.getInt("radius");

            if (dailyUpkeep < 0) {
                fail("installations.yml " + key + ".daily-upkeep must be >= 0");
            }
            if (constructionTimeSeconds <= 0) {
                fail("installations.yml " + key + ".construction-time must be > 0");
            }
            if (radius <= 0) {
                fail("installations.yml " + key + ".radius must be > 0");
            }

            ConfigurationSection slotsSection = section.getConfigurationSection("slots");
            if (slotsSection == null) {
                fail("installations.yml " + key + ".slots is required");
            }

            Map<String, Integer> categorySlots = new HashMap<>();
            for (String categoryId : slotsSection.getKeys(false)) {
                String normalizedCategoryId = categoryId.toLowerCase();
                if (!knownCategories.contains(normalizedCategoryId)) {
                    fail("installations.yml " + key + ".slots." + categoryId
                            + " references unknown vehicle category (check vehicles.yml categories)");
                }
                int capacity = slotsSection.getInt(categoryId);
                if (capacity < 0) {
                    fail("installations.yml " + key + ".slots." + categoryId + " must be >= 0");
                }
                categorySlots.put(normalizedCategoryId, capacity);
            }

            byKind.put(kind, new InstallationKindConfig(
                    dailyUpkeep,
                    constructionTimeSeconds,
                    radius,
                    categorySlots));
        }
    }

    public static double getDailyUpkeep(InstallationKind kind) {
        return require(kind).getDailyUpkeep();
    }

    public static int getConstructionTimeSeconds(InstallationKind kind) {
        return require(kind).getConstructionTimeSeconds();
    }

    public static int getRadius(InstallationKind kind) {
        return require(kind).getRadius();
    }

    public static int getConsentProximityBlocks() {
        return consentProximityBlocks;
    }

    public static int getTransferRequestTimeoutSeconds() {
        return transferRequestTimeoutSeconds;
    }

    public static int getCategorySlotCapacity(InstallationKind kind, String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) {
            return 0;
        }
        Integer capacity = require(kind).getCategorySlots().get(categoryId.toLowerCase());
        return capacity == null ? 0 : capacity;
    }

    public static Map<String, Integer> getCategorySlots(InstallationKind kind) {
        return require(kind).getCategorySlots();
    }

    public static Map<InstallationKind, InstallationKindConfig> getAll() {
        return Collections.unmodifiableMap(byKind);
    }

    private static InstallationKindConfig require(InstallationKind kind) {
        InstallationKindConfig config = byKind.get(kind);
        if (config == null) {
            throw new IllegalStateException(
                    "Installation config not loaded for kind " + kind.getCommandName());
        }
        return config;
    }

    private static void fail(String message) {
        if (Bukkit.getServer() != null) {
            Bukkit.getLogger().severe("[SimpleFactions] " + message);
        }
        throw new IllegalStateException(message);
    }
}
