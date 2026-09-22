package me.Plugins.SimpleFactions.vehicles.maintenance;

public final class VehicleMaintenanceMessages {
    private VehicleMaintenanceMessages() {}

    public static String repairBlocked() {
        return "§cPay outstanding vehicle maintenance before repairing. Use /faction vehicle maintenance pay.";
    }

    public static String payArmed() {
        return "§aRight-click the vehicle to pay one day of maintenance from your pouch.";
    }

    public static String paySuccess() {
        return "§aPaid vehicle maintenance.";
    }

    public static String notUnpaid() {
        return "§cThis vehicle has no unpaid maintenance.";
    }

    public static String insufficientPouch() {
        return "§cInsufficient pouch balance to pay vehicle maintenance.";
    }

    public static String unknownType() {
        return "§cThis vehicle is not registered for faction upkeep.";
    }

    public static String notLeader() {
        return "§cYou need to be a faction leader to pay vehicle maintenance.";
    }

    public static String payUsage() {
        return "§cUsage: §e/faction vehicle maintenance pay";
    }

    public static String vehicleUsage() {
        return "§cUsage: §e/faction vehicle transfer <installation id> §7or §e/faction vehicle maintenance pay";
    }

    public static String transferUsage() {
        return "§cUsage: §e/faction vehicle transfer <installation id>";
    }
}
