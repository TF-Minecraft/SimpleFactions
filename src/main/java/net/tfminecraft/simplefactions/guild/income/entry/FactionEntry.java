package net.tfminecraft.simplefactions.guild.income.entry;

import net.tfminecraft.simplefactions.objects.Faction;

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
