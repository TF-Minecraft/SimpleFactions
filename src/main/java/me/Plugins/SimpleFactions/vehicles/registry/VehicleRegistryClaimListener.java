package me.Plugins.SimpleFactions.vehicles.registry;


import me.Plugins.SimpleFactions.vehicles.berth.VehicleConstructionMessages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.VehicleFramework.Data.OwnerData;
import net.tfminecraft.VehicleFramework.Events.VehicleOwnerClaimedEvent;
import net.tfminecraft.VehicleFramework.Events.VehiclePreInteractEvent;
import net.tfminecraft.VehicleFramework.Vehicles.ActiveVehicle;

public final class VehicleRegistryClaimListener implements Listener {
    private final VehicleRegistryClaimService claimService;

    public VehicleRegistryClaimListener(VehicleRegistryClaimService claimService) {
        this.claimService = claimService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehiclePreInteract(VehiclePreInteractEvent event) {
        Player player = event.getPlayer();
        ActiveVehicle vehicle = event.getVehicle();
        if (player == null || vehicle == null) {
            return;
        }
        if (!isUnowned(vehicle)) {
            return;
        }

        VehicleRegistryClaimService.ClaimRegisterResult result =
                claimService.tryRegisterOnClaim(player, vehicle);
        handleClaimResult(event, player, vehicle.getId(), result);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleOwnerClaimed(VehicleOwnerClaimedEvent event) {
        Player player = event.getPlayer();
        ActiveVehicle vehicle = event.getVehicle();
        if (player == null || vehicle == null) {
            return;
        }

        VehicleRegistryClaimService.ClaimRegisterResult result =
                claimService.tryRegisterOnClaim(player, vehicle);
        handleClaimResult(event, player, vehicle.getId(), result);
    }

    static void handleClaimResult(
            org.bukkit.event.Cancellable event,
            Player player,
            String vehicleTypeId,
            VehicleRegistryClaimService.ClaimRegisterResult result) {
        switch (result.status()) {
            case FAIL_UNKNOWN_TYPE, FAIL_SLOT_LIMIT -> {
                event.setCancelled(true);
                String message = VehicleConstructionMessages.forResult(
                        result.buildFailure(),
                        vehicleTypeId);
                if (message != null) {
                    player.sendMessage(message);
                }
            }
            case SKIP, ALLOWED -> {
                // allow VF to continue
            }
        }
    }

    private static boolean isUnowned(ActiveVehicle vehicle) {
        OwnerData ownerData = vehicle.getOwnerData();
        String owner = ownerData == null ? null : ownerData.getOwner();
        return owner == null || owner.equalsIgnoreCase("none");
    }
}
