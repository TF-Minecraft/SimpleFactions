package me.Plugins.SimpleFactions.vehicles.berth;

import me.Plugins.SimpleFactions.vehicles.berth.InstallationVehicleService.CanRegisterResult;
import me.Plugins.SimpleFactions.Loaders.InstallationConfigLoader;
import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationBounds;
import net.tfminecraft.VehicleFramework.Vehicles.ActiveVehicle;

public final class VehicleTransferMessages {
    private VehicleTransferMessages() {}

    public static String forResult(
            CanRegisterResult result,
            Installation installation,
            ActiveVehicle vehicle,
            String vehicleTypeId) {
        if (result == null || installation == null) {
            return null;
        }

        return switch (result) {
            case OK -> berthSuccess(installation);
            case NOT_IN_REGISTRY -> "§cThis vehicle must be owned by a player before it can be berthed.";
            case ALREADY_BERTHED -> "§cThis vehicle is already berthed at an installation.";
            case UNKNOWN_TYPE -> "§cThis vehicle is not registered for faction upkeep.";
            case UNSUPPORTED_CATEGORY -> unsupportedCategory(installation, vehicleTypeId);
            case NO_CAPACITY -> noCapacity(installation, vehicleTypeId);
            case OUT_OF_RADIUS -> outOfRadius(installation, vehicle);
            case WRONG_PROVINCE -> wrongProvince(installation, vehicle);
            case REPAIR_LOCKED -> VehicleInstallationLockService.BERTH_BLOCKED;
        };
    }

    public static String commandArmed(Installation installation) {
        return "§aRight-click the vehicle to transfer it to " + installation.getName() + ".";
    }

    public static String notLeader() {
        return "§cYou need to be a faction leader to transfer vehicles.";
    }

    public static String unknownInstallation() {
        return "§cUnknown installation id.";
    }

    public static String noPendingSession() {
        return "§cYou are not transferring a vehicle. Use /faction vehicle transfer <id>.";
    }

    public static String berthSuccess(Installation installation) {
        return "§aVehicle berthed at " + installation.getName() + ".";
    }

    public static String consentPrompt(String leaderName, String typeId, String installationName) {
        return "§e" + leaderName + " wants to berth your " + typeId + " at "
                + installationName + ". It will become a faction vehicle. §7/faction accept";
    }

    public static String ownerOffline() {
        return "§cThe vehicle owner must be online to transfer this vehicle.";
    }

    public static String ownerTooFar(int blocks) {
        return "§cThe vehicle owner must be within " + blocks + " blocks of the vehicle.";
    }

    public static String consentExpired() {
        return "§cVehicle transfer request expired or was cancelled.";
    }

    public static String consentSent(String ownerName) {
        return "§aSent vehicle transfer request to " + ownerName + ".";
    }

    private static String unsupportedCategory(Installation installation, String vehicleTypeId) {
        String category = VehiclesConfigLoader.getCategoryId(vehicleTypeId).orElse("vehicle");
        return "§cThis installation does not support " + category + " vehicles.";
    }

    private static String noCapacity(Installation installation, String vehicleTypeId) {
        String category = VehiclesConfigLoader.getCategoryId(vehicleTypeId).orElse("vehicle");
        int capacity = InstallationConfigLoader.getCategorySlotCapacity(
                installation.getKind(),
                category);
        int used = SimpleFactions.getVehicleRegistry().usedCategorySize(
                installation.getId(),
                category);
        return "§c" + installation.getName() + " has no space for " + category
                + " (" + used + "/" + capacity + " used).";
    }

    private static String outOfRadius(Installation installation, ActiveVehicle vehicle) {
        int radius = InstallationConfigLoader.getRadius(installation.getKind());
        double distance = InstallationBounds.horizontalDistanceBlocks(
                installation.getCenterX(),
                installation.getCenterZ(),
                vehicle == null ? null : vehicle.getLocation());
        return "§cVehicle must be within " + radius + " blocks of "
                + installation.getName() + " (currently "
                + InstallationBounds.formatDistance(distance) + ").";
    }

    private static String wrongProvince(Installation installation, ActiveVehicle vehicle) {
        int required = installation.getProvince();
        int actual = InstallationBounds.provinceAt(vehicle == null ? null : vehicle.getLocation());
        return "§cVehicle must be in province " + required + " (currently " + actual + ").";
    }
}
