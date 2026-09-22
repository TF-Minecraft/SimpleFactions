package me.Plugins.SimpleFactions.Loaders;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.SimpleFactions.vehicles.VehicleTypeConfig;

public final class VehiclesConfigLoader {
    private static int personalSlotLimit = 1;
    private static int defaultPerPerson = 1;
    private static int maintenanceHourlyDamagePercent = 20;
    private static int maintenanceMinHealthPercent = 3;
    private static long maintenanceIntervalTicks = 72000L;
    private static Set<String> categoryIds = Set.of();
    private static Map<String, Map<String, VehicleTypeConfig>> typesByCategory = Map.of();
    private static Map<String, String> categoryByVehicleTypeId = Map.of();

    private VehiclesConfigLoader() {}

    public static void load(File vehiclesYaml) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(vehiclesYaml);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
            fail("Failed to load vehicles.yml");
        }

        personalSlotLimit = config.getInt("personal-slot-limit", 1);
        if (personalSlotLimit < 0) {
            fail("vehicles.yml personal-slot-limit must be >= 0");
        }

        defaultPerPerson = config.getInt("default-per-person", 1);
        if (defaultPerPerson < 1) {
            fail("vehicles.yml default-per-person must be >= 1");
        }

        boolean hasDefaultUpkeep = config.contains("default-upkeep");
        double defaultUpkeep = hasDefaultUpkeep ? config.getDouble("default-upkeep") : 0.0;
        if (hasDefaultUpkeep && defaultUpkeep < 0) {
            fail("vehicles.yml default-upkeep must be >= 0");
        }

        maintenanceHourlyDamagePercent = config.getInt("maintenance-hourly-damage-percent", 20);
        if (maintenanceHourlyDamagePercent < 0 || maintenanceHourlyDamagePercent > 100) {
            fail("vehicles.yml maintenance-hourly-damage-percent must be between 0 and 100");
        }
        maintenanceMinHealthPercent = config.getInt("maintenance-min-health-percent", 3);
        if (maintenanceMinHealthPercent < 0 || maintenanceMinHealthPercent > 100) {
            fail("vehicles.yml maintenance-min-health-percent must be between 0 and 100");
        }
        maintenanceIntervalTicks = config.getLong("maintenance-interval-ticks", 72000L);
        if (maintenanceIntervalTicks < 1L) {
            fail("vehicles.yml maintenance-interval-ticks must be >= 1");
        }

        if (config.isConfigurationSection("upkeep")) {
            fail("vehicles.yml uses legacy upkeep block; use categories.<category>.<type>.upkeep instead");
        }

        ConfigurationSection categoriesSection = config.getConfigurationSection("categories");
        if (categoriesSection == null) {
            fail("vehicles.yml missing required categories section");
        }

        Set<String> categories = new HashSet<>();
        Map<String, Map<String, VehicleTypeConfig>> byCategory = new HashMap<>();
        Map<String, String> typeToCategory = new HashMap<>();

        for (String categoryId : categoriesSection.getKeys(false)) {
            String normalizedCategoryId = categoryId.toLowerCase();
            categories.add(normalizedCategoryId);

            ConfigurationSection categorySection = categoriesSection.getConfigurationSection(categoryId);
            Map<String, VehicleTypeConfig> types = new HashMap<>();
            if (categorySection != null) {
                boolean categoryShowIcon = categorySection.getBoolean("show-on-upcoming-battle-icon", false);
                for (String vehicleTypeId : categorySection.getKeys(false)) {
                    if (!categorySection.isConfigurationSection(vehicleTypeId)) {
                        continue;
                    }
                    String path = "categories." + categoryId + "." + vehicleTypeId;
                    if (!config.contains(path + ".size")) {
                        fail("vehicles.yml " + path + ".size is required");
                    }

                    double upkeep;
                    if (config.contains(path + ".upkeep")) {
                        upkeep = config.getDouble(path + ".upkeep");
                    } else if (hasDefaultUpkeep) {
                        upkeep = defaultUpkeep;
                    } else {
                        fail("vehicles.yml " + path + ".upkeep is required");
                        upkeep = 0;
                    }
                    if (upkeep < 0) {
                        fail("vehicles.yml " + path + ".upkeep must be >= 0");
                    }

                    int size = config.getInt(path + ".size");
                    if (size <= 0) {
                        fail("vehicles.yml " + path + ".size must be > 0");
                    }

                    int perPerson = config.getInt(path + ".per-person", defaultPerPerson);
                    if (perPerson < 1) {
                        fail("vehicles.yml " + path + ".per-person must be >= 1");
                    }

                    boolean ignoreLimit = config.getBoolean(path + ".ignore-limit", false);
                    boolean showOnUpcomingBattleIcon = config.contains(path + ".show-on-upcoming-battle-icon")
                            ? config.getBoolean(path + ".show-on-upcoming-battle-icon")
                            : categoryShowIcon;

                    String normalizedTypeId = vehicleTypeId.toLowerCase();
                    if (typeToCategory.containsKey(normalizedTypeId)) {
                        fail("vehicles.yml duplicate vehicle type id: " + vehicleTypeId);
                    }
                    types.put(
                            normalizedTypeId,
                            new VehicleTypeConfig(upkeep, size, perPerson, ignoreLimit, showOnUpcomingBattleIcon));
                    typeToCategory.put(normalizedTypeId, normalizedCategoryId);
                }
            }
            byCategory.put(normalizedCategoryId, Collections.unmodifiableMap(types));
        }

        categoryIds = Collections.unmodifiableSet(categories);
        typesByCategory = Collections.unmodifiableMap(byCategory);
        categoryByVehicleTypeId = Collections.unmodifiableMap(typeToCategory);
    }

    public static int getPersonalSlotLimit() {
        return personalSlotLimit;
    }

    public static int getMaintenanceHourlyDamagePercent() {
        return maintenanceHourlyDamagePercent;
    }

    public static int getMaintenanceMinHealthPercent() {
        return maintenanceMinHealthPercent;
    }

    public static long getMaintenanceIntervalTicks() {
        return maintenanceIntervalTicks;
    }

    public static double getMaintenanceHourlyDamageFraction() {
        return maintenanceHourlyDamagePercent / 100.0;
    }

    public static double getMaintenanceMinHealthFraction() {
        return maintenanceMinHealthPercent / 100.0;
    }

    public static int getDefaultPerPerson() {
        return defaultPerPerson;
    }

    public static boolean isKnownType(String vehicleTypeId) {
        return resolveType(vehicleTypeId) != null;
    }

    public static int getPerPersonLimit(String vehicleTypeId) {
        VehicleTypeConfig type = resolveType(vehicleTypeId);
        return type == null ? defaultPerPerson : type.getPerPersonLimit();
    }

    public static boolean ignoresPersonalSlotLimit(String vehicleTypeId) {
        VehicleTypeConfig type = resolveType(vehicleTypeId);
        return type != null && type.isIgnoreLimit();
    }

    public static boolean showsOnUpcomingBattleIcon(String vehicleTypeId) {
        VehicleTypeConfig type = resolveType(vehicleTypeId);
        return type != null && type.isShowOnUpcomingBattleIcon();
    }

    public static double getUpkeep(String vehicleTypeId) {
        VehicleTypeConfig type = resolveType(vehicleTypeId);
        return type == null ? 0.0 : type.getUpkeep();
    }

    public static Optional<String> getCategoryId(String vehicleTypeId) {
        if (vehicleTypeId == null || vehicleTypeId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(categoryByVehicleTypeId.get(vehicleTypeId.toLowerCase()));
    }

    public static int getSize(String vehicleTypeId) {
        VehicleTypeConfig type = resolveType(vehicleTypeId);
        return type == null ? 0 : type.getSize();
    }

    public static Set<String> getCategoryIds() {
        return categoryIds;
    }

    public static Map<String, VehicleTypeConfig> getTypesInCategory(String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) {
            return Map.of();
        }
        Map<String, VehicleTypeConfig> types = typesByCategory.get(categoryId.toLowerCase());
        return types == null ? Map.of() : types;
    }

    private static VehicleTypeConfig resolveType(String vehicleTypeId) {
        if (vehicleTypeId == null || vehicleTypeId.isEmpty()) {
            return null;
        }
        String categoryId = categoryByVehicleTypeId.get(vehicleTypeId.toLowerCase());
        if (categoryId == null) {
            return null;
        }
        return typesByCategory.get(categoryId).get(vehicleTypeId.toLowerCase());
    }

    private static void fail(String message) {
        if (Bukkit.getServer() != null) {
            Bukkit.getLogger().severe("[SimpleFactions] " + message);
        }
        throw new IllegalStateException(message);
    }
}
