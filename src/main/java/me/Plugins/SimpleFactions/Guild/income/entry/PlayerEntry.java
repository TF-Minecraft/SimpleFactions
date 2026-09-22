package me.Plugins.SimpleFactions.Guild.income.entry;

public class PlayerEntry implements TaxEntry {
    private String origin;
    private double amount;

    public PlayerEntry(String f, double a) {
        origin = f;
        amount = a;
    }

    public String getOrigin() {
        return origin;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double d) {
        amount = d;
    }
}
