package net.tfminecraft.simplefactions.vehicles.berth;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleService.CanRegisterResult;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService.CanAddResult;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationBounds;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

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
            case ALREADY_IN_POOL -> "§cThis vehicle is already in a faction vehicle pool.";
            case UNKNOWN_TYPE -> "§cThis vehicle is not registered for faction upkeep.";
            case UNSUPPORTED_CATEGORY -> unsupportedCategory(installation, vehicleTypeId);
            case NO_CAPACITY -> noCapacity(installation, vehicleTypeId);
            case OUT_OF_RADIUS -> outOfRadius(installation, vehicle);
            case WRONG_PROVINCE -> wrongProvince(installation, vehicle);
            case REPAIR_LOCKED -> VehicleInstallationLockService.BERTH_BLOCKED;
        };
    }

    public static String forPoolResult(CanAddResult result, String vehicleTypeId, Faction faction) {
        if (result == null) {
            return null;
        }
        return switch (result) {
            case OK -> poolSuccess();
            case NOT_OWNED -> "§cThis vehicle must be owned by a player before it can join the faction pool.";
            case ALREADY_IN_POOL -> "§cThis vehicle is already in a faction vehicle pool.";
            case ALREADY_BERTHED -> "§cThis vehicle is already berthed at an installation.";
            case UNKNOWN_TYPE -> "§cThis vehicle is not registered for faction upkeep.";
            case MUST_BERTH -> "§cThis vehicle belongs at an installation, not in the faction pool.";
            case NO_ARTILLERY_CAPACITY -> noArtilleryCapacity(faction);
        };
    }

    public static String commandArmed(Installation installation) {
        return "§aRight-click the vehicle to transfer it to " + installation.getName() + ".";
    }

    public static String poolCommandArmed() {
        return "§aRight-click the vehicle to transfer it to the faction vehicle pool.";
    }

    public static String poolSuccess() {
        return "§aVehicle added to the faction vehicle pool.";
    }

    public static String poolConsentPrompt(String leaderName, String typeId) {
        return "§e" + leaderName + " wants to add your " + typeId
                + " to the faction vehicle pool. It will become a faction vehicle. §7/faction accept";
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

    private static String noArtilleryCapacity(Faction faction) {
        int slots = FactionVehiclePoolService.artillerySlots(faction);
        int used = 0;
        if (faction != null && SimpleFactions.plugin != null) {
            used = SimpleFactions.getVehicleRegistry()
                    .countPoolCategory(faction.getId(), FactionVehiclePoolService.ARTILLERY_CATEGORY);
        }
        return "§cThe faction pool has no free artillery slot (" + used + "/" + slots + " used).";
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
