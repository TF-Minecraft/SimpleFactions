package net.tfminecraft.simplefactions.managers.inventory;

import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.objects.Faction;

/** A player typing a new vehicle fee rate in chat. A null vehicle type means the general rate. */
public class FeeRateInput {
    private final Faction faction;
    private final FeeKind kind;
    private final String vehicleTypeId;
    private int time;

    public FeeRateInput(Faction faction, FeeKind kind, String vehicleTypeId) {
        this.faction = faction;
        this.kind = kind;
        this.vehicleTypeId = vehicleTypeId;
    }

    public boolean tick() {
        time++;
        return time == 30;
    }

    public Faction getFaction() {
        return faction;
    }

    public FeeKind getKind() {
        return kind;
    }

    public String getVehicleTypeId() {
        return vehicleTypeId;
    }
}
