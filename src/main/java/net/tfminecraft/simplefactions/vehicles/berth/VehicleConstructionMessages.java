package net.tfminecraft.simplefactions.vehicles.berth;

import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;

public final class VehicleConstructionMessages {
    private VehicleConstructionMessages() {}

    public static String forResult(CanBuildResult result, String vehicleTypeId) {
        if (result == null) {
            return null;
        }

        return switch (result) {
            case OK -> null;
            case TOTAL_LIMIT -> totalLimit();
            case PER_TYPE_LIMIT -> perTypeLimit(vehicleTypeId);
            case UNKNOWN_TYPE -> "§cThis vehicle type is not registered for faction upkeep.";
        };
    }

    private static String totalLimit() {
        int limit = VehiclesConfigLoader.getPersonalSlotLimit();
        return "§cYou have reached your personal vehicle limit (" + limit + ").";
    }

    private static String perTypeLimit(String vehicleTypeId) {
        String type = vehicleTypeId == null || vehicleTypeId.isEmpty() ? "vehicle" : vehicleTypeId;
        int limit = VehiclesConfigLoader.getPerPersonLimit(vehicleTypeId);
        return "§cYou already have the maximum number of " + type + " vehicles (" + limit + ").";
    }
}
