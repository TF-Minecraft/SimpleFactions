package net.tfminecraft.simplefactions.vehicles.berth;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Outcome;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Status;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;

public final class FactionVehicleReleaseMessages {
    private FactionVehicleReleaseMessages() {}

    public static String notLeader() {
        return "§cYou need to be a faction leader to take or give faction vehicles.";
    }

    public static String takeArmed() {
        return "§aRight-click a faction pool or installation vehicle to take it as your personal vehicle.";
    }

    public static String giveArmed(String playerName) {
        return "§aRight-click a faction pool or installation vehicle to give it to " + playerName
                + ". They must accept with /faction accept.";
    }

    public static String giveUsage() {
        return "§cUsage: §e/faction vehicle give <player>";
    }

    public static String giveSelf() {
        return "§cUse /faction vehicle take to take a vehicle yourself.";
    }

    public static String recipientOffline(String playerName) {
        return "§c" + playerName + " must be online to receive a vehicle.";
    }

    public static String notFactionVehicle() {
        return "§cThat vehicle is not in your faction pool or berthed at your installation.";
    }

    public static String giveBlocked() {
        return "§cCannot give vehicles from this installation during battle or raid embargo.";
    }

    public static String ownershipUnavailable() {
        return "§cCould not assign that vehicle. Try again while it is spawned.";
    }

    public static String takenOut() {
        return "§aVehicle taken out as your personal vehicle.";
    }

    public static String giveSuccessLeader(String playerName) {
        return "§aGave the vehicle to " + playerName + ".";
    }

    public static String giveSuccessRecipient(String leaderName) {
        return "§a" + leaderName + " gave you a faction vehicle. It is now your personal vehicle.";
    }

    public static String givePrompt(String leaderName, String vehicleTypeId) {
        String type = vehicleTypeId == null || vehicleTypeId.isBlank() ? "vehicle" : vehicleTypeId;
        return "§e" + leaderName + " wants to give you the faction's " + type
                + ". It will become your personal vehicle. §7/faction accept";
    }

    public static String giveSent(String playerName) {
        return "§aSent vehicle gift request to " + playerName + ".";
    }

    public static String giveExpired() {
        return "§cVehicle gift request expired or was cancelled.";
    }

    public static String forTake(Outcome outcome) {
        return forOutcome(outcome, true, null);
    }

    public static String forGive(Outcome outcome, String recipientName) {
        return forOutcome(outcome, false, recipientName);
    }

    private static String forOutcome(Outcome outcome, boolean self, String recipientName) {
        if (outcome == null) {
            return notFactionVehicle();
        }
        Status status = outcome.status();
        return switch (status) {
            case OK -> self ? takenOut() : giveSuccessLeader(recipientName);
            case NOT_LEADER -> notLeader();
            case NOT_FACTION_VEHICLE -> notFactionVehicle();
            case INSTALLATION_LOCKED -> self
                    ? VehicleInstallationLockService.UNBERTH_BLOCKED
                    : giveBlocked();
            case IN_BATTLE -> FactionCampaignBattleLock.BLOCKED;
            case NO_PERSONAL_ROOM, UNKNOWN_TYPE -> personalRoom(
                    outcome.slotFailure() == null
                            ? (status == Status.UNKNOWN_TYPE
                                    ? CanBuildResult.UNKNOWN_TYPE
                                    : CanBuildResult.TOTAL_LIMIT)
                            : outcome.slotFailure(),
                    outcome.vehicleTypeId(),
                    self ? null : recipientName);
            case OWNERSHIP_UNAVAILABLE -> ownershipUnavailable();
        };
    }

    static String personalRoom(CanBuildResult slot, String vehicleTypeId, String otherPlayer) {
        if (otherPlayer == null || otherPlayer.isBlank()) {
            return VehicleConstructionMessages.forResult(slot, vehicleTypeId);
        }
        if (slot == null) {
            return "§c" + otherPlayer + " has no room for another personal vehicle.";
        }
        return switch (slot) {
            case OK -> null;
            case TOTAL_LIMIT -> "§c" + otherPlayer + " has reached their personal vehicle limit ("
                    + VehiclesConfigLoader.getPersonalSlotLimit() + ").";
            case PER_TYPE_LIMIT -> "§c" + otherPlayer + " already has the maximum number of "
                    + typeName(vehicleTypeId) + " vehicles ("
                    + VehiclesConfigLoader.getPerPersonLimit(vehicleTypeId) + ").";
            case UNKNOWN_TYPE -> "§cThis vehicle type is not registered for faction upkeep.";
        };
    }

    private static String typeName(String vehicleTypeId) {
        return vehicleTypeId == null || vehicleTypeId.isBlank() ? "that" : vehicleTypeId;
    }
}
