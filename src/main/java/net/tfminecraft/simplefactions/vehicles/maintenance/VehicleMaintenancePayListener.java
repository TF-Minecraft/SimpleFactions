package net.tfminecraft.simplefactions.vehicles.maintenance;


import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePaySessionManager.VehicleMaintenancePaySession;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.VehicleMaintenancePayResult;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

public final class VehicleMaintenancePayListener implements Listener {
    private final VehicleMaintenancePaySessionManager sessionManager;
    private final VehicleMaintenancePayService payService;

    public VehicleMaintenancePayListener(
            VehicleMaintenancePaySessionManager sessionManager,
            VehicleMaintenancePayService payService) {
        this.sessionManager = sessionManager;
        this.payService = payService;
    }

    @EventHandler
    public void onVehiclePreInteract(VehiclePreInteractEvent event) {
        Player leader = event.getPlayer();
        if (leader == null) {
            return;
        }
        VehicleMaintenancePaySession session = sessionManager.get(leader.getUniqueId());
        if (session == null) {
            return;
        }

        event.setCancelled(true);
        ActiveVehicle vehicle = event.getVehicle();
        if (vehicle == null || vehicle.getUUID() == null) {
            return;
        }

        VehicleMaintenancePayResult result = payService.tryPay(
                leader.getUniqueId(),
                vehicle.getUUID(),
                vehicle.getId(),
                session.getPaymentSource());
        switch (result) {
            case SUCCESS -> {
                sessionManager.clear(leader.getUniqueId());
                SimpleFactions plugin = SimpleFactions.getInstance();
                if (plugin != null) {
                    plugin.saveVehicleRegistry();
                }
                leader.sendMessage(VehicleMaintenanceMessages.paySuccess());
            }
            case NOT_UNPAID -> leader.sendMessage(VehicleMaintenanceMessages.notUnpaid());
            case INSUFFICIENT_POUCH -> leader.sendMessage(VehicleMaintenanceMessages.insufficientPouch());
            case INSUFFICIENT_BANK -> leader.sendMessage(VehicleMaintenanceMessages.insufficientBank());
            case UNKNOWN_TYPE -> leader.sendMessage(VehicleMaintenanceMessages.unknownType());
        }
    }
}
