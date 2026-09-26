package net.tfminecraft.simplefactions.vehicles.berth;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Outcome;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Status;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

public final class InstallationVehicleUnberthService {
    private final FactionVehicleReleaseService releaseService;

    public InstallationVehicleUnberthService(PlayerVehicleRegistry registry) {
        this(new FactionVehicleReleaseService(registry));
    }

    public InstallationVehicleUnberthService(FactionVehicleReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    InstallationVehicleUnberthService(
            PlayerVehicleRegistry registry,
            FactionVehicleReleaseService.PersonalOwner personalOwner) {
        this(new FactionVehicleReleaseService(registry, personalOwner));
    }

    public enum UnberthResult {
        OK,
        NOT_LEADER,
        NOT_BERTHED,
        EMBARGO,
        IN_BATTLE,
        NO_PERSONAL_ROOM,
        UNKNOWN_TYPE,
        OWNERSHIP_UNAVAILABLE,
        SAVE_FAILED
    }

    public record UnberthOutcome(UnberthResult result, CanBuildResult slotFailure, String vehicleTypeId) {}

    public UnberthOutcome unberth(
            Faction faction,
            String leaderName,
            Installation installation,
            String vehicleUuid) {
        String installationId = installation == null ? "" : installation.getId();
        Outcome outcome = releaseService.takeFromInstallation(
                faction, leaderName, installationId, vehicleUuid);
        return new UnberthOutcome(map(outcome.status()), outcome.slotFailure(), outcome.vehicleTypeId());
    }

    private static UnberthResult map(Status status) {
        if (status == null) {
            return UnberthResult.NOT_BERTHED;
        }
        return switch (status) {
            case OK -> UnberthResult.OK;
            case NOT_LEADER -> UnberthResult.NOT_LEADER;
            case NOT_FACTION_VEHICLE -> UnberthResult.NOT_BERTHED;
            case INSTALLATION_LOCKED -> UnberthResult.EMBARGO;
            case IN_BATTLE -> UnberthResult.IN_BATTLE;
            case NO_PERSONAL_ROOM -> UnberthResult.NO_PERSONAL_ROOM;
            case UNKNOWN_TYPE -> UnberthResult.UNKNOWN_TYPE;
            case OWNERSHIP_UNAVAILABLE -> UnberthResult.OWNERSHIP_UNAVAILABLE;
            case SAVE_FAILED -> UnberthResult.SAVE_FAILED;
        };
    }

    public static String messageFor(UnberthOutcome outcome) {
        if (outcome == null) {
            return "§cThat vehicle is not berthed at this installation.";
        }
        return switch (outcome.result()) {
            case OK -> FactionVehicleReleaseMessages.takenOut();
            case NOT_LEADER -> FactionVehicleReleaseMessages.notLeader();
            case NOT_BERTHED -> "§cThat vehicle is not berthed at this installation.";
            case EMBARGO -> VehicleInstallationLockService.UNBERTH_BLOCKED;
            case IN_BATTLE -> FactionCampaignBattleLock.BLOCKED;
            case NO_PERSONAL_ROOM, UNKNOWN_TYPE -> FactionVehicleReleaseMessages.personalRoom(
                    outcome.slotFailure() == null
                            ? (outcome.result() == UnberthResult.UNKNOWN_TYPE
                                    ? CanBuildResult.UNKNOWN_TYPE
                                    : CanBuildResult.TOTAL_LIMIT)
                            : outcome.slotFailure(),
                    outcome.vehicleTypeId(),
                    null);
            case OWNERSHIP_UNAVAILABLE -> FactionVehicleReleaseMessages.ownershipUnavailable();
            case SAVE_FAILED -> FactionVehicleReleaseMessages.saveFailed();
        };
    }
}
