package net.tfminecraft.simplefactions.vehicles.maintenance;

import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.simplefactions.economy.Money;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments;
import net.tfminecraft.tlibs.character.CharacterRef;

public final class VehicleMaintenancePayService {
    private final VehicleMaintenanceStore store;
    public enum PaymentSource { POUCH, BANK }

    public VehicleMaintenancePayService(VehicleMaintenanceStore store) { this.store = store; }

    public double payAmount(String vehicleTypeId) {
        return VehiclesConfigLoader.getUpkeep(vehicleTypeId);
    }

    public VehicleMaintenancePayResult tryPay(String operationId, CharacterRef payer, String vehicleUuid, String vehicleTypeId, PaymentSource source) {
        if (vehicleUuid == null || !store.isUnpaid(vehicleUuid)) return VehicleMaintenancePayResult.NOT_UNPAID;
        if (!VehiclesConfigLoader.isKnownType(vehicleTypeId)) return VehicleMaintenancePayResult.UNKNOWN_TYPE;
        var payments = VehiclePayments.current();
        if (payments == null || payer == null) return VehicleMaintenancePayResult.UNAVAILABLE;
        Result result = payments.maintain(operationId, payer, vehicleUuid, source == PaymentSource.BANK, Money.cents(payAmount(vehicleTypeId)));
        if (result == Result.APPLIED) return VehicleMaintenancePayResult.SUCCESS;
        if (result != Result.REFUSED) return VehicleMaintenancePayResult.UNAVAILABLE;
        return source == PaymentSource.BANK ? VehicleMaintenancePayResult.INSUFFICIENT_BANK : VehicleMaintenancePayResult.INSUFFICIENT_POUCH;
    }

    public enum VehicleMaintenancePayResult {
        SUCCESS,
        NOT_UNPAID,
        INSUFFICIENT_POUCH,
        INSUFFICIENT_BANK,
        UNKNOWN_TYPE,
        UNAVAILABLE
    }
}
