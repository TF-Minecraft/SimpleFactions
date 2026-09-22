package me.Plugins.SimpleFactions.Managers.Inventory;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;

public class TaxChange {
    private Faction faction;
    private TaxTarget target;
    private String id;
    private int time;

    public TaxChange(Faction faction, TaxTarget target, String id) {
        this.faction = faction;
        this.target = target;
        this.id = id;
        time = 0;
    }

    public boolean tick() {
        time++;
        return time == 30;
    }

    public TaxTarget getTarget() {
        return target;
    }

    public String getId() {
        return id;
    }

    public Faction getFaction() {
        return faction;
    }
}
