package net.tfminecraft.simplefactions.espionage;

import java.util.Map;
import java.util.random.RandomGenerator;

public final class EspionageMath {
    private EspionageMath() {}

    public static int aptitude(Map<String, Integer> attributes, RandomGenerator random) {
        return aptitude(attributes, EspionageConfig.weights(), random);
    }

    public static int aptitude(Map<String, Integer> attributes, Map<String, Double> weights, RandomGenerator random) {
        double score = EspionageConfig.base();
        for (var weight : weights.entrySet()) score += centered(attributes, weight.getKey()) * weight.getValue();
        score = Math.max(-1000, Math.min(1000, score));
        return clamp((int) Math.round(score) + random.nextInt(-EspionageConfig.aptitudeSpread(), EspionageConfig.aptitudeSpread() + 1), 0, 100);
    }

    private static int centered(Map<String, Integer> attributes, String key) {
        // Permanent character attributes; configurable centering and cap prevent runaway scaling.
        return clamp(attributes.getOrDefault(key, 0), 0, EspionageConfig.cap()) - EspionageConfig.center();
    }

    /** Share of aptitude a holder has built up, rising linearly from the starting share to full. */
    public static double buildUp(long appointedAt, long now) {
        double days = EspionageConfig.buildUpDays();
        if (appointedAt <= 0 || days <= 0) return 1;
        double progress = (now - appointedAt) / (days * 86_400_000);
        if (progress >= 1) return 1;
        double start = EspionageConfig.startingAptitude();
        return start + (1 - start) * Math.max(0, progress);
    }

    public static int dailyRoll(int aptitude, int reduction, RandomGenerator random) {
        // Several independent draws concentrate luck around zero while retaining rare extremes.
        int luck = 0;
        for (int draw = 0; draw < EspionageConfig.luckDraws(); draw++)
            luck += random.nextInt(-EspionageConfig.luckSpread(), EspionageConfig.luckSpread() + 1);
        return (int) Math.round(clamp(aptitude, 0, 100) * EspionageConfig.rollMultiplier()
                + luck / (double) EspionageConfig.luckDraws()) - clamp(reduction, 0, 100);
    }

    public static String quality(int margin) { return EspionageConfig.tier(margin).label(); }

    /** Rounded, asymmetric ranges; their midpoint cannot reveal the exact value. */
    public static Estimate estimate(double value, int margin, boolean signed, RandomGenerator random) {
        var tier = EspionageConfig.tier(margin);
        if (tier == IntelligenceTier.UNKNOWN || !Double.isFinite(value)) return null;
        double uncertainty = EspionageConfig.settings(tier).uncertainty();
        double width = Math.max(2, Math.abs(value) * uncertainty * 2);
        double step = Math.pow(10, Math.floor(Math.log10(Math.max(1, width / 4))));
        double below = width * random.nextDouble(0.2, 0.8);
        long lower = (long) (Math.floor((value - below) / step) * step);
        long upper = (long) (Math.ceil((value + width - below) / step) * step);
        if (!signed) lower = Math.max(0, lower);
        return new Estimate(lower, Math.max(lower + 1, upper));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Estimate(long lower, long upper) {
        public double midpoint() { return lower / 2.0 + upper / 2.0; }
        public String display() {
            return lower + " to " + upper;
        }
    }
}
