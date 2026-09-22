package me.Plugins.SimpleFactions.Guild.income.entry;

import me.Plugins.SimpleFactions.Objects.Faction;

public class FactionEntry implements TaxEntry{
    private Faction origin;
    private double amount;

    public FactionEntry(Faction f, double a) {
        origin = f;
        amount = a;
    }

    public Faction getOrigin() {
        return origin;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double d) {
        amount = d;
    }
}
