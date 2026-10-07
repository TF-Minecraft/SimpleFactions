package net.tfminecraft.simplefactions.vehicles.handover;

import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverService.Outcome;

public final class VehicleHandoverMessages {
    private VehicleHandoverMessages() {}

    public static String usage() {
        return "§cUsage: §e/faction vehicle handover <player>";
    }

    public static String self() {
        return "§cYou already own your vehicles.";
    }

    public static String recipientOffline(String playerName) {
        return "§c" + playerName + " must be online to receive a vehicle.";
    }

    public static String armed(String playerName) {
        return "§aRight-click one of your personal vehicles to hand it over to " + playerName
                + ". They must accept with /faction accept.";
    }

    public static String prompt(String ownerName, String vehicleTypeId) {
        return "§e" + ownerName + " §awants to hand you their §e" + vehicleTypeId
                + "§a. Type §e/faction accept §ato take it or §e/faction decline§a.";
    }

    public static String sent(String recipientName) {
        return "§aHandover offered to " + recipientName + ". Waiting for them to accept.";
    }

    public static String expired() {
        return "§cThe vehicle handover request expired.";
    }

    public static String successOwner(String recipientName) {
        return "§aHanded the vehicle over to " + recipientName + ".";
    }

    public static String successRecipient(String ownerName) {
        return "§aYou now own the vehicle " + ownerName + " handed over.";
    }

    public static String ownershipUnavailable() {
        return "§cCould not hand over that vehicle. Try again while it is spawned.";
    }

    public static String feeChanged() {
        return "§cThe vehicle handover fee changed. Please make a new offer and confirm the current fee.";
    }

    public static String forOutcome(Outcome outcome, String recipientName) {
        if (outcome == null) {
            return null;
        }
        return switch (outcome.status()) {
            case OK -> null;
            case NOT_OWNER -> "§cThat is not one of your personal vehicles.";
            case IN_BATTLE -> "§cYou cannot hand over vehicles while your faction is in a battle.";
            case UNKNOWN_TYPE -> "§cThis vehicle type is not registered for faction upkeep.";
            case NO_ROOM -> "§c" + recipientName + " has reached their personal vehicle limit for that vehicle.";
        };
    }
}
