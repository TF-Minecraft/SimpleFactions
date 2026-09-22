package me.Plugins.SimpleFactions.government.proposal;

public class TaxLawChange {
    private TaxTarget target;
    private String id;
    private double newTax;

    public TaxLawChange(TaxTarget target, String id, double newTax) {
        this.target = target;
        this.id = id;
        this.newTax = newTax;
    }

    public TaxTarget getTarget() {
        return target;
    }
    public String getId() {
        return id;
    }
    public double getNewTax() {
        return newTax;
    }
}
