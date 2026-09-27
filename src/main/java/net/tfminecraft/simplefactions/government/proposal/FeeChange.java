package net.tfminecraft.simplefactions.government.proposal;

/** A proposed vehicle tax or fee rate, for every vehicle or for one vehicle type. */
public class FeeChange {
    private final FeeKind kind;
    private final String vehicleTypeId;
    private final double newRate;

    /** A null vehicle type changes the general rate. */
    public FeeChange(FeeKind kind, String vehicleTypeId, double newRate) {
        this.kind = kind;
        this.vehicleTypeId = vehicleTypeId == null || vehicleTypeId.isBlank() ? null : vehicleTypeId;
        this.newRate = newRate;
    }

    public FeeKind getKind() {
        return kind;
    }

    public String getVehicleTypeId() {
        return vehicleTypeId;
    }

    public boolean isGeneral() {
        return vehicleTypeId == null;
    }

    public double getNewRate() {
        return newRate;
    }

    /** Two proposals clash when they would set the same rate. */
    public boolean sameTarget(FeeChange other) {
        if (other == null || other.kind != kind) {
            return false;
        }
        if (vehicleTypeId == null) {
            return other.vehicleTypeId == null;
        }
        return vehicleTypeId.equalsIgnoreCase(other.vehicleTypeId);
    }
}
