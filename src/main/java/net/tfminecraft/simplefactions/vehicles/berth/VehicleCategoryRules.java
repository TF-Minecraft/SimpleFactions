package net.tfminecraft.simplefactions.vehicles.berth;

import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.installation.InstallationKind;

public final class VehicleCategoryRules {
    private VehicleCategoryRules() {}

    public static boolean isBerthableCategory(String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) {
            return false;
        }
        for (InstallationKind kind : InstallationKind.values()) {
            if (InstallationConfigLoader.getCategorySlotCapacity(kind, categoryId) > 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBerthableType(String vehicleTypeId) {
        return VehiclesConfigLoader.getCategoryId(vehicleTypeId)
                .map(VehicleCategoryRules::isBerthableCategory)
                .orElse(false);
    }
}
