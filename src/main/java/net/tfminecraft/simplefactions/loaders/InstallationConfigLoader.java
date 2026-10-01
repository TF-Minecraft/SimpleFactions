package net.tfminecraft.simplefactions.loaders;

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

import net.tfminecraft.simplefactions.guild.hub.SupplyHubService;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.InstallationKindConfig;
import net.tfminecraft.simplefactions.installation.InstallationKindConfig.Level;

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
                if (kind == InstallationKind.TRAIN_STATION) {
                    byKind.put(
                            kind,
                            new InstallationKindConfig(
                                    80,
                                    Map.of(
                                            1, new Level(10, 259200, Map.of("static_emplacements", 2),
                                                    SupplyHubService.defaultHubSlots(kind, 1)),
                                            2, new Level(35, 259200, Map.of("static_emplacements", 3),
                                                    SupplyHubService.defaultHubSlots(kind, 2)),
                                            3, new Level(100, 432000, Map.of("static_emplacements", 4),
                                                    SupplyHubService.defaultHubSlots(kind, 3)))));
                    continue;
                }
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

            Map<String, Integer> categorySlots = readSlots(key, slotsSection, knownCategories);
            int hubSlots = readHubSlots(kind, 1, section, key);
            Map<Integer, Level> levels = new HashMap<>();
            levels.put(1, new Level(dailyUpkeep, constructionTimeSeconds, categorySlots, hubSlots));
            ConfigurationSection levelsSection = section.getConfigurationSection("levels");
            if (section.contains("levels") && levelsSection == null) {
                fail("installations.yml " + key + ".levels must be a section");
            }
            if (levelsSection != null) {
                Map<Integer, ConfigurationSection> parsedLevels = new HashMap<>();
                for (String levelKey : levelsSection.getKeys(false)) {
                    int level;
                    try {
                        level = Integer.parseInt(levelKey);
                    } catch (NumberFormatException e) {
                        fail("installations.yml " + key + ".levels." + levelKey
                                + " must be a level number >= 2");
                        return;
                    }
                    if (level < 2
                            || parsedLevels.put(
                                    level, levelsSection.getConfigurationSection(levelKey)) != null) {
                        fail("installations.yml " + key + ".levels must start at 2 with no gaps");
                    }
                }
                for (int level = 2; level <= parsedLevels.size() + 1; level++) {
                    ConfigurationSection levelSection = parsedLevels.get(level);
                    if (levelSection == null) {
                        fail("installations.yml " + key + ".levels must start at 2 with no gaps");
                    }
                    String prefix = key + ".levels." + level;
                    if (!levelSection.contains("daily-upkeep")) {
                        fail("installations.yml " + prefix + ".daily-upkeep is required");
                    }
                    if (!levelSection.contains("construction-time")) {
                        fail("installations.yml " + prefix + ".construction-time is required");
                    }
                    double levelUpkeep = levelSection.getDouble("daily-upkeep");
                    int levelConstruction = levelSection.getInt("construction-time");
                    if (levelUpkeep < 0) {
                        fail("installations.yml " + prefix + ".daily-upkeep must be >= 0");
                    }
                    if (levelConstruction <= 0) {
                        fail("installations.yml " + prefix + ".construction-time must be > 0");
                    }
                    ConfigurationSection levelSlots = levelSection.getConfigurationSection("slots");
                    if (levelSlots == null) {
                        fail("installations.yml " + prefix + ".slots is required");
                    }
                    levels.put(
                            level,
                            new Level(
                                    levelUpkeep,
                                    levelConstruction,
                                    readSlots(prefix, levelSlots, knownCategories),
                                    readHubSlots(kind, level, levelSection, prefix)));
                }
            }

            byKind.put(kind, new InstallationKindConfig(radius, levels));
        }
    }

    private static Map<String, Integer> readSlots(
            String key,
            ConfigurationSection slotsSection,
            Set<String> knownCategories) {
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
        return categorySlots;
    }

    private static int readHubSlots(
            InstallationKind kind, int level, ConfigurationSection section, String key) {
        if (!section.contains("hub-slots")) {
            return SupplyHubService.defaultHubSlots(kind, level);
        }
        int hubSlots = section.getInt("hub-slots");
        if (hubSlots < 0) {
            fail("installations.yml " + key + ".hub-slots must be >= 0");
        }
        return hubSlots;
    }

    public static double getDailyUpkeep(InstallationKind kind) {
        return getDailyUpkeep(kind, 1);
    }

    public static double getDailyUpkeep(InstallationKind kind, int level) {
        return require(kind).getDailyUpkeep(level);
    }

    public static int getConstructionTimeSeconds(InstallationKind kind) {
        return getConstructionTimeSeconds(kind, 1);
    }

    public static int getConstructionTimeSeconds(InstallationKind kind, int level) {
        return require(kind).getConstructionTimeSeconds(level);
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
        return getCategorySlotCapacity(kind, 1, categoryId);
    }

    public static int getCategorySlotCapacity(InstallationKind kind, int level, String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) {
            return 0;
        }
        Integer capacity = require(kind).getCategorySlots(level).get(categoryId.toLowerCase());
        return capacity == null ? 0 : capacity;
    }

    public static Map<String, Integer> getCategorySlots(InstallationKind kind) {
        return getCategorySlots(kind, 1);
    }

    public static Map<String, Integer> getCategorySlots(InstallationKind kind, int level) {
        return require(kind).getCategorySlots(level);
    }

    public static int getHubSlots(InstallationKind kind, int level) {
        return require(kind).getHubSlots(level);
    }

    public static int getMaximumLevel(InstallationKind kind) {
        return require(kind).getMaximumLevel();
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
