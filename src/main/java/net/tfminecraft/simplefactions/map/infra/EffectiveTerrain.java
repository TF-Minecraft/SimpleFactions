package net.tfminecraft.simplefactions.map.infra;

public final class EffectiveTerrain {
    private EffectiveTerrain() {}

    public static double calculate(double terrain, double infrastructure, double full, double target, double access) {
        if (terrain >= target || infrastructure <= 0 || full <= 0) return terrain;
        double fill = Math.min(1, infrastructure / full);
        return terrain + (target - terrain) * fill * access;
    }
}
