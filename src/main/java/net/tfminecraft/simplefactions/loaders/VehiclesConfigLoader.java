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
import net.tfminecraft.simplefactions.vehicles.maintenance.UnpaidVehicleEffects;

public final class VehiclesConfigLoader {
    private static final Set<String> UNPAID_SECTIONS = Set.of("default", "categories", "types");
    private static final Set<String> UNPAID_KEYS = Set.of(
            "hourly-damage-percent",
            "min-health-percent",
            "part-min-health-percent",
            "keep-afloat",
            "keep-aloft",
            "block-repair");

    private static int personalSlotLimit = 1;
    private static int defaultPerPerson = 1;
    private static long maintenanceIntervalTicks = 72000L;
    private static UnpaidVehicleEffects defaultUnpaidEffects = UnpaidVehicleEffects.DEFAULT;
    private static Map<String, UnpaidVehicleEffects> unpaidEffectsByType = Map.of();
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

        // The old top-level maintenance keys still set the default decay that unpaid-effects.default leaves out.
        UnpaidVehicleEffects nextDefaultUnpaid = new UnpaidVehicleEffects(
                nextMaintenanceHourlyDamagePercent,
                nextMaintenanceMinHealthPercent,
                Map.of(),
                UnpaidVehicleEffects.DEFAULT.keepAfloat(),
                UnpaidVehicleEffects.DEFAULT.keepAloft(),
                UnpaidVehicleEffects.DEFAULT.blockRepair());
        Map<String, UnpaidVehicleEffects> nextUnpaidByType = new HashMap<>();
        if (config.contains("unpaid-effects")) {
            ConfigurationSection unpaid = section(config, "unpaid-effects", "unpaid-effects");
            for (String key : unpaid.getKeys(false)) {
                if (!UNPAID_SECTIONS.contains(key)) {
                    throw failure("vehicles.yml unpaid-effects." + key + " is not default, categories or types");
                }
            }
            nextDefaultUnpaid = unpaidOverlay(nextDefaultUnpaid, unpaid, "default", "unpaid-effects.default");
            Map<String, String> categoryKeys = unpaidKeys(unpaid, "categories", categories, "vehicle category");
            Map<String, String> typeKeys = unpaidKeys(unpaid, "types", typeToCategory.keySet(), "vehicle type");
            // Check every category once here; a category with no vehicle types is never resolved below.
            for (String categoryKey : categoryKeys.values()) {
                unpaidOverlay(nextDefaultUnpaid, unpaid, "categories." + categoryKey,
                        "unpaid-effects.categories." + categoryKey);
            }
            for (Map.Entry<String, String> type : typeToCategory.entrySet()) {
                UnpaidVehicleEffects effects = nextDefaultUnpaid;
                String categoryKey = categoryKeys.get(type.getValue());
                if (categoryKey != null) {
                    effects = unpaidOverlay(effects, unpaid, "categories." + categoryKey,
                            "unpaid-effects.categories." + categoryKey);
                }
                String typeKey = typeKeys.get(type.getKey());
                if (typeKey != null) {
                    effects = unpaidOverlay(effects, unpaid, "types." + typeKey, "unpaid-effects.types." + typeKey);
                }
                nextUnpaidByType.put(type.getKey(), effects);
            }
        }

        personalSlotLimit = nextPersonalSlotLimit;
        defaultPerPerson = nextDefaultPerPerson;
        maintenanceIntervalTicks = nextMaintenanceIntervalTicks;
        defaultUnpaidEffects = nextDefaultUnpaid;
        unpaidEffectsByType = Collections.unmodifiableMap(nextUnpaidByType);
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

    public static long getMaintenanceIntervalTicks() {
        return maintenanceIntervalTicks;
    }

    /** What unpaid upkeep does to a vehicle of this type; unlisted types get the default. */
    public static UnpaidVehicleEffects getUnpaidEffects(String vehicleTypeId) {
        if (vehicleTypeId == null) {
            return defaultUnpaidEffects;
        }
        return unpaidEffectsByType.getOrDefault(
                vehicleTypeId.toLowerCase(java.util.Locale.ROOT), defaultUnpaidEffects);
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

    /** Lower-cased id to the key as written, for each entry under unpaid-effects.<group>. */
    private static Map<String, String> unpaidKeys(
            ConfigurationSection unpaid, String group, Set<String> known, String kind) {
        Map<String, String> keys = new HashMap<>();
        if (!unpaid.contains(group)) {
            return keys;
        }
        for (String key : section(unpaid, group, "unpaid-effects." + group).getKeys(false)) {
            String normalized = key.toLowerCase(java.util.Locale.ROOT);
            if (!known.contains(normalized)) {
                throw failure("vehicles.yml unpaid-effects." + group + "." + key + " is not a " + kind);
            }
            String previous = keys.putIfAbsent(normalized, key);
            if (previous != null) {
                throw failure("vehicles.yml unpaid-effects." + group + " lists " + key + " twice (also as " + previous + ")");
            }
        }
        return keys;
    }

    /** {@code base} with whatever the section at {@code key} sets; a missing section changes nothing. */
    private static UnpaidVehicleEffects unpaidOverlay(
            UnpaidVehicleEffects base, ConfigurationSection parent, String key, String path) {
        if (!parent.contains(key)) {
            return base;
        }
        ConfigurationSection section = section(parent, key, path);
        for (String setting : section.getKeys(false)) {
            if (!UNPAID_KEYS.contains(setting)) {
                throw failure("vehicles.yml " + path + "." + setting + " is not an unpaid effect");
            }
        }
        Map<String, Double> parts = new HashMap<>(base.partMinHealthPercent());
        if (section.contains("part-min-health-percent")) {
            String partsPath = path + ".part-min-health-percent";
            ConfigurationSection partsSection = section(section, "part-min-health-percent", partsPath);
            Set<String> seen = new HashSet<>();
            for (String part : partsSection.getKeys(false)) {
                String normalized = part.toLowerCase(java.util.Locale.ROOT);
                if (!seen.add(normalized)) {
                    throw failure("vehicles.yml " + partsPath + " lists " + part + " twice");
                }
                parts.put(normalized, percent(partsSection, part, partsPath));
            }
        }
        return new UnpaidVehicleEffects(
                section.contains("hourly-damage-percent")
                        ? percent(section, "hourly-damage-percent", path)
                        : base.hourlyDamagePercent(),
                section.contains("min-health-percent")
                        ? percent(section, "min-health-percent", path)
                        : base.minHealthPercent(),
                parts,
                flag(section, "keep-afloat", base.keepAfloat(), path),
                flag(section, "keep-aloft", base.keepAloft(), path),
                flag(section, "block-repair", base.blockRepair(), path));
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key, String path) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw failure("vehicles.yml " + path + " must be a section");
        }
        return section;
    }

    private static double percent(ConfigurationSection section, String key, String path) {
        if (!(section.get(key) instanceof Number number)
                || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < 0
                || number.doubleValue() > 100) {
            throw failure("vehicles.yml " + path + "." + key + " must be a number between 0 and 100");
        }
        return number.doubleValue();
    }

    private static boolean flag(ConfigurationSection section, String key, boolean fallback, String path) {
        if (!section.contains(key)) {
            return fallback;
        }
        if (!(section.get(key) instanceof Boolean value)) {
            throw failure("vehicles.yml " + path + "." + key + " must be true or false");
        }
        return value;
    }

    private static IllegalStateException failure(String message) {
        if (Bukkit.getServer() != null) {
            Bukkit.getLogger().severe("[SimpleFactions] " + message);
        }
        return new IllegalStateException(message);
    }
}
