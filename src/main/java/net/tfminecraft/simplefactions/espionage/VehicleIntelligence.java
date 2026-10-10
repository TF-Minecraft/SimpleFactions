package net.tfminecraft.simplefactions.espionage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

/** A faction's own vehicles (pool and installation berths) as daily intelligence. */
public final class VehicleIntelligence {
    public static final String TOTAL = "Vehicles";
    private VehicleIntelligence() {}

    public static String categoryKey(String category) { return "Vehicles:" + category; }
    public static String berthedKey(String installationId) { return "Installation:" + installationId + ":Vehicles"; }
    /** Detail key for vehicle types; a null installation means the faction pool. */
    public static String typesKey(String installationId) { return "vehicle-types:" + (installationId == null ? "pool" : installationId); }

    private static PlayerVehicleRegistry registry() {
        return SimpleFactions.plugin == null ? null : SimpleFactions.getVehicleRegistry();
    }

    /** Every configured category is captured, empty ones too, so a missing estimate never reveals zero. */
    static void capture(Map<String, Double> values, Faction faction) {
        var registry = registry();
        if (registry == null) return;
        List<PlayerVehicleRecord> owned = new ArrayList<>(registry.getPoolVehicles(faction.getId()));
        for (var installation : faction.getInstallationHandler().getAll()) {
            var berthed = registry.getByInstallation(faction.getId(), installation.getId());
            values.put(berthedKey(installation.getId()), (double) berthed.size());
            owned.addAll(berthed);
        }
        values.put(TOTAL, (double) owned.size());
        Map<String, Double> categories = new TreeMap<>();
        for (String category : VehiclesConfigLoader.getCategoryIds()) categories.put(category, 0.0);
        for (var record : owned) VehiclesConfigLoader.getCategoryId(record.getVehicleTypeId())
                .ifPresent(category -> categories.merge(category, 1.0, Double::sum));
        categories.forEach((category, count) -> values.put(categoryKey(category), count));
    }

    static void captureTypes(IntelligenceReport report, Faction faction) {
        var registry = registry();
        if (registry == null || !report.allows("vehicle-types")) return;
        report.details.put(typesKey(null), types(registry.getPoolVehicles(faction.getId())));
        for (var installation : faction.getInstallationHandler().getAll())
            report.details.put(typesKey(installation.getId()),
                    types(registry.getByInstallation(faction.getId(), installation.getId())));
    }

    private static List<String> types(List<PlayerVehicleRecord> records) {
        return records.stream().map(PlayerVehicleRecord::getVehicleTypeId).distinct().sorted().toList();
    }

    /** Foreign lore for the whole faction (null installation) or one installation's berths. */
    public static List<String> lore(IntelligenceReport report, String installationId) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Vehicles: " + value(report, installationId == null ? TOTAL : berthedKey(installationId)));
        if (installationId == null && report != null) {
            for (String category : new java.util.TreeSet<>(VehiclesConfigLoader.getCategoryIds())) {
                var estimate = report.estimate(categoryKey(category));
                if (estimate != null) lore.add("§7- " + categoryName(category) + "§7: §e" + estimate.display());
            }
        }
        var types = report == null ? List.<String>of() : report.details("vehicle-types", typesKey(installationId));
        if (!types.isEmpty()) lore.add("§7" + (installationId == null ? "Types in pool: " : "Types: ") + "§f" + String.join(", ", types));
        return lore;
    }

    private static String value(IntelligenceReport report, String metric) {
        String value = report == null ? IntelligenceReport.UNKNOWN : report.display(metric);
        return (value.equals(IntelligenceReport.UNKNOWN) ? "§7" : "§e") + value;
    }

    private static String categoryName(String category) {
        String name = VehiclesConfigLoader.getCategoryDisplayName(category);
        return name != null ? name : category.replace('_', ' ');
    }
}
