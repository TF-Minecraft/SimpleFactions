package net.tfminecraft.simplefactions.loaders;

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

import net.tfminecraft.simplefactions.vehicles.VehicleTypeConfig;

public final class VehiclesConfigLoader {
    private static int personalSlotLimit = 1;
    private static int defaultPerPerson = 1;
    private static int maintenanceHourlyDamagePercent = 20;
    private static int maintenanceMinHealthPercent = 3;
    private static long maintenanceIntervalTicks = 72000L;
    private static Set<String> categoryIds = Set.of();
    private static Map<String, Map<String, VehicleTypeConfig>> typesByCategory = Map.of();
    private static Map<String, String> categoryByVehicleTypeId = Map.of();
    private static Map<String, String> categoryDisplayNames = Map.of();
    private static Set<String> feeExcludedCategories = Set.of();

    private VehiclesConfigLoader() {}

    public static void load(File vehiclesYaml) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(vehiclesYaml);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
            throw failure("Failed to load vehicles.yml");
        }

        int nextPersonalSlotLimit = config.getInt("personal-slot-limit", 1);
        if (nextPersonalSlotLimit < 0) {
            throw failure("vehicles.yml personal-slot-limit must be >= 0");
        }

        int nextDefaultPerPerson = config.getInt("default-per-person", 1);
        if (nextDefaultPerPerson < 1) {
            throw failure("vehicles.yml default-per-person must be >= 1");
        }

        boolean hasDefaultUpkeep = config.contains("default-upkeep");
        double defaultUpkeep = hasDefaultUpkeep ? config.getDouble("default-upkeep") : 0.0;
        if (hasDefaultUpkeep && (!Double.isFinite(defaultUpkeep) || defaultUpkeep < 0)) {
            throw failure("vehicles.yml default-upkeep must be finite and >= 0");
        }

        int nextMaintenanceHourlyDamagePercent = config.getInt("maintenance-hourly-damage-percent", 20);
        if (nextMaintenanceHourlyDamagePercent < 0 || nextMaintenanceHourlyDamagePercent > 100) {
            throw failure("vehicles.yml maintenance-hourly-damage-percent must be between 0 and 100");
        }
        int nextMaintenanceMinHealthPercent = config.getInt("maintenance-min-health-percent", 3);
        if (nextMaintenanceMinHealthPercent < 0 || nextMaintenanceMinHealthPercent > 100) {
            throw failure("vehicles.yml maintenance-min-health-percent must be between 0 and 100");
        }
        long nextMaintenanceIntervalTicks = config.getLong("maintenance-interval-ticks", 72000L);
        if (nextMaintenanceIntervalTicks < 1L) {
            throw failure("vehicles.yml maintenance-interval-ticks must be >= 1");
        }

        Set<String> excluded = new HashSet<>();
        for (String category : config.getStringList("fee-excluded-categories")) {
            excluded.add(category.toLowerCase(java.util.Locale.ROOT));
        }

        if (config.isConfigurationSection("upkeep")) {
            throw failure("vehicles.yml uses legacy upkeep block; use categories.<category>.<type>.upkeep instead");
        }

        ConfigurationSection categoriesSection = config.getConfigurationSection("categories");
        if (categoriesSection == null) {
            throw failure("vehicles.yml missing required categories section");
        }

        Set<String> categories = new HashSet<>();
        Map<String, Map<String, VehicleTypeConfig>> byCategory = new HashMap<>();
        Map<String, String> typeToCategory = new HashMap<>();
        Map<String, String> displayNames = new HashMap<>();

        for (String categoryId : categoriesSection.getKeys(false)) {
            String normalizedCategoryId = categoryId.toLowerCase(java.util.Locale.ROOT);
            if (!categories.add(normalizedCategoryId)) {
                throw failure("vehicles.yml duplicate category id: " + categoryId);
            }

            ConfigurationSection categorySection = categoriesSection.getConfigurationSection(categoryId);
            Map<String, VehicleTypeConfig> types = new HashMap<>();
            if (categorySection != null) {
                String displayName = categorySection.getString("display-name");
                if (displayName != null && !displayName.isBlank()) {
                    displayNames.put(normalizedCategoryId, displayName);
                }
                boolean categoryShowIcon = categorySection.getBoolean("show-on-upcoming-battle-icon", false);
                for (String vehicleTypeId : categorySection.getKeys(false)) {
                    if (!categorySection.isConfigurationSection(vehicleTypeId)) {
                        continue;
                    }
                    String path = "categories." + categoryId + "." + vehicleTypeId;
                    if (!config.contains(path + ".size")) {
                        throw failure("vehicles.yml " + path + ".size is required");
                    }

                    double upkeep;
                    if (config.contains(path + ".upkeep")) {
                        upkeep = config.getDouble(path + ".upkeep");
                    } else if (hasDefaultUpkeep) {
                        upkeep = defaultUpkeep;
                    } else {
                        throw failure("vehicles.yml " + path + ".upkeep is required");
                    }
                    if (!Double.isFinite(upkeep) || upkeep < 0) {
                        throw failure("vehicles.yml " + path + ".upkeep must be finite and >= 0");
                    }

                    int size = config.getInt(path + ".size");
                    if (size <= 0) {
                        throw failure("vehicles.yml " + path + ".size must be > 0");
                    }

                    int perPerson = config.getInt(path + ".per-person", nextDefaultPerPerson);
                    if (perPerson < 1) {
                        throw failure("vehicles.yml " + path + ".per-person must be >= 1");
                    }

                    boolean ignoreLimit = config.getBoolean(path + ".ignore-limit", false);
                    boolean showOnUpcomingBattleIcon = config.contains(path + ".show-on-upcoming-battle-icon")
                            ? config.getBoolean(path + ".show-on-upcoming-battle-icon")
                            : categoryShowIcon;

                    String normalizedTypeId = vehicleTypeId.toLowerCase(java.util.Locale.ROOT);
                    if (typeToCategory.containsKey(normalizedTypeId)) {
                        throw failure("vehicles.yml duplicate vehicle type id: " + vehicleTypeId);
                    }
                    types.put(
                            normalizedTypeId,
                            new VehicleTypeConfig(upkeep, size, perPerson, ignoreLimit, showOnUpcomingBattleIcon));
                    typeToCategory.put(normalizedTypeId, normalizedCategoryId);
                }
            }
            byCategory.put(normalizedCategoryId, Collections.unmodifiableMap(types));
        }

        personalSlotLimit = nextPersonalSlotLimit;
        defaultPerPerson = nextDefaultPerPerson;
        maintenanceHourlyDamagePercent = nextMaintenanceHourlyDamagePercent;
        maintenanceMinHealthPercent = nextMaintenanceMinHealthPercent;
        maintenanceIntervalTicks = nextMaintenanceIntervalTicks;
        feeExcludedCategories = Collections.unmodifiableSet(excluded);
        categoryIds = Collections.unmodifiableSet(categories);
        typesByCategory = Collections.unmodifiableMap(byCategory);
        categoryByVehicleTypeId = Collections.unmodifiableMap(typeToCategory);
        categoryDisplayNames = Collections.unmodifiableMap(displayNames);
    }

    /** VFBuilders blueprint categories left out of the vehicle fee proposal menu (staff-only ones). */
    public static boolean isFeeExcludedCategory(String vfBuildersCategoryId) {
        return vfBuildersCategoryId != null
                && feeExcludedCategories.contains(vfBuildersCategoryId.toLowerCase(java.util.Locale.ROOT));
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
        return Optional.ofNullable(categoryByVehicleTypeId.get(vehicleTypeId.toLowerCase(java.util.Locale.ROOT)));
    }

    public static int getSize(String vehicleTypeId) {
        VehicleTypeConfig type = resolveType(vehicleTypeId);
        return type == null ? 0 : type.getSize();
    }

    public static Set<String> getCategoryIds() {
        return categoryIds;
    }

    public static String getCategoryDisplayName(String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) {
            return null;
        }
        return categoryDisplayNames.get(categoryId.toLowerCase(java.util.Locale.ROOT));
    }

    public static Map<String, VehicleTypeConfig> getTypesInCategory(String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) {
            return Map.of();
        }
        Map<String, VehicleTypeConfig> types = typesByCategory.get(categoryId.toLowerCase(java.util.Locale.ROOT));
        return types == null ? Map.of() : types;
    }

    private static VehicleTypeConfig resolveType(String vehicleTypeId) {
        if (vehicleTypeId == null || vehicleTypeId.isEmpty()) {
            return null;
        }
        String categoryId = categoryByVehicleTypeId.get(vehicleTypeId.toLowerCase(java.util.Locale.ROOT));
        if (categoryId == null) {
            return null;
        }
        return typesByCategory.get(categoryId).get(vehicleTypeId.toLowerCase(java.util.Locale.ROOT));
    }

    private static IllegalStateException failure(String message) {
        if (Bukkit.getServer() != null) {
            Bukkit.getLogger().severe("[SimpleFactions] " + message);
        }
        return new IllegalStateException(message);
    }
}
