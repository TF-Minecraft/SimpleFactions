package net.tfminecraft.simplefactions.vehicles.maintenance;

import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.PaymentSource;

public final class VehicleMaintenanceMessages {
    private VehicleMaintenanceMessages() {}

    public static String repairBlocked() {
        return "§cMaintenance unpaid. Use /faction vehicle maintenance pay bank, then right-click the vehicle to pay from your bank.";
    }

    public static String payArmed() {
        return payArmed(PaymentSource.POUCH);
    }

    public static String payArmed(PaymentSource source) {
        return "§aRight-click the vehicle to pay one day of maintenance from your "
                + (source == PaymentSource.BANK ? "bank" : "pouch") + ".";
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

    public static String insufficientBank() {
        return "§cInsufficient bank balance to pay vehicle maintenance.";
    }

    public static String unknownType() {
        return "§cThis vehicle is not registered for faction upkeep.";
    }

    public static String notLeader() {
        return "§cOnly faction leaders can pay from their pouch. Use /faction vehicle maintenance pay bank to pay from your own bank.";
    }

    public static String payUsage() {
        return "§cUsage: §e/faction vehicle maintenance pay [bank]";
    }

    public static String vehicleUsage() {
        return "§cUsage: §e/faction vehicle transfer <installation id> §7or §e/faction vehicle maintenance pay [bank]";
    }

    public static String transferUsage() {
        return "§cUsage: §e/faction vehicle transfer <installation id>";
    }
}
