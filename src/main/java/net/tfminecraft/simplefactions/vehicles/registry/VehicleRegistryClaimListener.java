package net.tfminecraft.simplefactions.vehicles.registry;


import net.tfminecraft.simplefactions.vehicles.berth.VehicleConstructionMessages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.vehicleframework.data.OwnerData;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

public final class VehicleRegistryClaimListener implements Listener {
    public static final String FACTION_VEHICLE =
            "§cThis vehicle belongs to a faction; its leader manages it with /faction vehicle.";
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

    public static void handleClaimResult(
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
            case FAIL_FACTION_VEHICLE -> {
                event.setCancelled(true);
                player.sendMessage(FACTION_VEHICLE);
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
