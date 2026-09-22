package me.Plugins.SimpleFactions.Guild.income.entry;

import me.Plugins.SimpleFactions.Guild.Guild;

public class GuildEntry implements TaxEntry {
    private Guild origin;
    private double amount;

    public GuildEntry(Guild f, double a) {
        origin = f;
        amount = a;
    }

    public Guild getOrigin() {
        return origin;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double d) {
        amount = d;
    }
}
