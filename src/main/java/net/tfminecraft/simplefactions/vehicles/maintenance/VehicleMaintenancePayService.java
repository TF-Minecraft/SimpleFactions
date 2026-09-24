package net.tfminecraft.simplefactions.vehicles.maintenance;

import java.util.UUID;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerPouch;

public final class VehicleMaintenancePayService {
    private final VehicleMaintenanceStore store;
    private final PlayerPouch playerPouch;
    private final PlayerBank playerBank;

    public enum PaymentSource { POUCH, BANK }

    public VehicleMaintenancePayService(VehicleMaintenanceStore store, PlayerPouch playerPouch) {
        this(store, playerPouch, DenarEconomyPlayerBank.INSTANCE);
    }

    public VehicleMaintenancePayService(VehicleMaintenanceStore store, PlayerPouch playerPouch, PlayerBank playerBank) {
        this.store = store;
        this.playerPouch = playerPouch;
        this.playerBank = playerBank;
    }

    public double payAmount(String vehicleTypeId) {
        return VehiclesConfigLoader.getUpkeep(vehicleTypeId);
    }

    public VehicleMaintenancePayResult tryPay(UUID playerUuid, String vehicleUuid, String vehicleTypeId) {
        return tryPay(playerUuid, vehicleUuid, vehicleTypeId, PaymentSource.POUCH);
    }

    public VehicleMaintenancePayResult tryPay(
            UUID playerUuid, String vehicleUuid, String vehicleTypeId, PaymentSource source) {
        if (vehicleUuid == null || !store.isUnpaid(vehicleUuid)) {
            return VehicleMaintenancePayResult.NOT_UNPAID;
        }
        if (!VehiclesConfigLoader.isKnownType(vehicleTypeId)) {
            return VehicleMaintenancePayResult.UNKNOWN_TYPE;
        }
        double amount = payAmount(vehicleTypeId);
        if (amount > 0.0) {
            boolean paid = switch (source) {
                case BANK -> playerBank.withdrawFromBank(playerUuid, amount);
                case POUCH -> playerPouch.withdrawFromPouch(playerUuid, amount);
            };
            if (!paid) {
                return source == PaymentSource.BANK
                        ? VehicleMaintenancePayResult.INSUFFICIENT_BANK
                        : VehicleMaintenancePayResult.INSUFFICIENT_POUCH;
            }
        }
        store.clearUnpaid(vehicleUuid);
        return VehicleMaintenancePayResult.SUCCESS;
    }

    public enum VehicleMaintenancePayResult {
        SUCCESS,
        NOT_UNPAID,
        INSUFFICIENT_POUCH,
        INSUFFICIENT_BANK,
        UNKNOWN_TYPE
    }
}
