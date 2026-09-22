package me.Plugins.SimpleFactions.Guild.Branch;

public class BranchModifier {
    private final double base;
    private final double perLevel;

    public BranchModifier(double b, double l) {
        base = b;
        perLevel = l;
    }

    public double getBase() { return base; }
    public double getPerLevel() { return perLevel; }
    public double getCurrent(int lvl) {
        return Math.round((base + perLevel*lvl)*100.0)/100.0;
    }
}
