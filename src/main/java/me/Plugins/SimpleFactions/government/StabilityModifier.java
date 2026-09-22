package me.Plugins.SimpleFactions.government;

public class StabilityModifier {
    private String name;
    private double modifier;
    private double decay;

    public StabilityModifier(String name, double modifier, double decay) {
        this.name = name;
        this.modifier = modifier;
        this.decay = decay;
    }

    public String getName() {
        return name;
    }

    public double getModifier() {
        return modifier;
    }

    public void increaseModifier(double amount) {
        this.modifier += amount;
    }

    public double getDecay() {
        return decay;
    }

    public boolean tick() {
        double step = Math.abs(decay);
        if (modifier > 0) {
            modifier -= step;
            return modifier <= 0;
        }
        if (modifier < 0) {
            modifier += step;
            return modifier >= 0;
        }
        return true;
    }
}
