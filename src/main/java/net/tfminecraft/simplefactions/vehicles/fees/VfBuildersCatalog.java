package net.tfminecraft.simplefactions.vehicles.fees;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.vehicles.VehicleIntegrationListener;
import net.tfminecraft.vfbuilders.core.Blueprint;
import net.tfminecraft.vfbuilders.core.BlueprintCategory;
import net.tfminecraft.vfbuilders.loaders.CategoryLoader;

/**
 * The vehicles players can build, grouped by VFBuilders blueprint category, for choosing
 * which vehicle a fee proposal applies to. Categories listed in vehicles.yml under
 * fee-excluded-categories are left out.
 */
public final class VfBuildersCatalog {
    public record Entry(String vehicleTypeId, ItemStack icon) {}

    public record Category(String id, ItemStack icon, List<Entry> vehicles) {}

    private VfBuildersCatalog() {}

    public static List<Category> categories() {
        List<Category> out = new ArrayList<>();
        if (!Bukkit.getPluginManager().isPluginEnabled("VFBuilders")) {
            return out;
        }
        for (BlueprintCategory category : CategoryLoader.get().values()) {
            if (category == null || VehiclesConfigLoader.isFeeExcludedCategory(category.getId())) {
                continue;
            }
            Map<String, Entry> vehicles = new LinkedHashMap<>();
            for (Blueprint blueprint : category.getBlueprints()) {
                String typeId = VehicleIntegrationListener.resolveVehicleTypeId(blueprint);
                if (typeId == null || !VehiclesConfigLoader.isKnownType(typeId)) {
                    continue;
                }
                ItemStack icon = blueprint.getItem() == null ? null : blueprint.getItem().clone();
                vehicles.putIfAbsent(typeId.toLowerCase(), new Entry(typeId, icon));
            }
            if (vehicles.isEmpty()) {
                continue;
            }
            ItemStack icon = category.getItem() == null ? null : category.getItem().clone();
            out.add(new Category(category.getId(), icon, new ArrayList<>(vehicles.values())));
        }
        return out;
    }

    public static Category category(String id) {
        for (Category category : categories()) {
            if (category.id().equalsIgnoreCase(id)) {
                return category;
            }
        }
        return null;
    }
}
