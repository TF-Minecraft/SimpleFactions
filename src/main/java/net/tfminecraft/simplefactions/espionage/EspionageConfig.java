package net.tfminecraft.simplefactions.espionage;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class EspionageConfig {
    public static final String DEFAULT_BYPASS_PERMISSION = "simplefactions.espionage.bypass";
    private static String bypassPermission = DEFAULT_BYPASS_PERMISSION;
    public static final Map<String, Double> DEFAULT_WEIGHTS = Map.of(
            "intelligence", 3.0, "wisdom", 2.5, "charisma", 1.5,
            "dexterity", 1.0, "constitution", -2.0, "strength", -2.0);
    private static Map<String, Double> weights = DEFAULT_WEIGHTS;
    private static double stabilityPenalty = 10;
    private static double penaltyDays = 7;
    private static double buildUpDays = 7, startingAptitude = .25, changeCooldownDays = 2;
    private static String reloadPermission = "simplefactions.espionage.reload";
    private static double base = 50, extraPositionPenalty = .25, rollMultiplier = 1.25;
    private static int center = 6, cap = 16, aptitudeSpread = 20, luckSpread = 75, luckDraws = 3, rosterLimit = 23;
    private static final Map<IntelligenceTier, TierSettings> tiers = new java.util.EnumMap<>(IntelligenceTier.class);
    private static final Map<String, IntelligenceTier> minimums = new LinkedHashMap<>();
    private static final Map<String, IntelligenceTier> cashflows = new LinkedHashMap<>();
    private static final Map<SpecialPosition, Double> vacancyPenalties = new java.util.EnumMap<>(SpecialPosition.class);
    private static final Map<String, IntelligenceTier> DEFAULT_MINIMUMS = Map.ofEntries(
            Map.entry("members", IntelligenceTier.RUMOURS), Map.entry("roster", IntelligenceTier.RUMOURS),
            Map.entry("wealth", IntelligenceTier.RUMOURS), Map.entry("prosperity", IntelligenceTier.BROAD),
            Map.entry("stability", IntelligenceTier.BROAD), Map.entry("administrative-power", IntelligenceTier.RELIABLE),
            Map.entry("professional-army", IntelligenceTier.RELIABLE), Map.entry("levies", IntelligenceTier.BROAD),
            Map.entry("mercenaries", IntelligenceTier.RELIABLE), Map.entry("installations", IntelligenceTier.BROAD),
            Map.entry("net-income", IntelligenceTier.BROAD), Map.entry("trade-power", IntelligenceTier.BROAD),
            Map.entry("income-total", IntelligenceTier.BROAD), Map.entry("expense-total", IntelligenceTier.BROAD),
            Map.entry("cashflow", IntelligenceTier.RELIABLE), Map.entry("dividend-rate", IntelligenceTier.RELIABLE),
            Map.entry("dividend-pool", IntelligenceTier.RELIABLE), Map.entry("dividend-tax", IntelligenceTier.RELIABLE),
            Map.entry("dividend-per-member", IntelligenceTier.RELIABLE), Map.entry("office-holder", IntelligenceTier.RELIABLE),
            Map.entry("office-aptitude", IntelligenceTier.DETAILED),
            Map.entry("guild-leader", IntelligenceTier.RELIABLE), Map.entry("guild-members", IntelligenceTier.RUMOURS),
            Map.entry("training", IntelligenceTier.RELIABLE), Map.entry("buildings", IntelligenceTier.BROAD),
            Map.entry("upgrades", IntelligenceTier.RELIABLE), Map.entry("taxes", IntelligenceTier.RELIABLE),
            Map.entry("laws", IntelligenceTier.RELIABLE), Map.entry("government", IntelligenceTier.RELIABLE),
            Map.entry("installation-details", IntelligenceTier.RELIABLE));
    static { load(new org.bukkit.configuration.file.YamlConfiguration()); }
    public record TierSettings(int minimumMargin, double uncertainty, double rosterFraction,
                               double boundedFraction, double relativeWidth) {}

    private EspionageConfig() {}

    public static void load(ConfigurationSection config) {
        String configuredPermission = config.getString("espionage.bypass-permission", DEFAULT_BYPASS_PERMISSION);
        bypassPermission = configuredPermission == null || configuredPermission.isBlank()
                ? DEFAULT_BYPASS_PERMISSION : configuredPermission.strip();
        Map<String, Double> loaded = new LinkedHashMap<>();
        DEFAULT_WEIGHTS.forEach((attribute, fallback) -> {
            double value = config.getDouble("espionage.aptitude.attribute-weights." + attribute, fallback);
            loaded.put(attribute, Double.isFinite(value) && Math.abs(value) <= 1000 ? value : fallback);
        });
        weights = Map.copyOf(loaded);
        stabilityPenalty = Math.min(100, nonnegative(config, "espionage.appointments.stability-penalty", 10));
        penaltyDays = Math.min(3650, nonnegative(config, "espionage.appointments.penalty-days", 7));
        buildUpDays = bounded(config, "espionage.appointments.build-up-days", 7, 0, 3650);
        startingAptitude = bounded(config, "espionage.appointments.starting-aptitude", .25, 0, 1);
        changeCooldownDays = bounded(config, "espionage.appointments.change-cooldown-days", 2, 0, 3650);
        vacancyPenalties.clear();
        for (SpecialPosition office : SpecialPosition.values()) vacancyPenalties.put(office,
                Math.min(100, nonnegative(config, "positions." + office.name().toLowerCase(java.util.Locale.ROOT)
                        + ".vacancy-stability-penalty", 10)));
        reloadPermission = config.getString("espionage.reload-permission", "simplefactions.espionage.reload");
        if (reloadPermission == null || reloadPermission.isBlank()) reloadPermission = "simplefactions.espionage.reload";
        reloadPermission = reloadPermission.strip();
        base = bounded(config, "espionage.aptitude.base", 50, 0, 100);
        cap = (int) bounded(config, "espionage.aptitude.attribute-cap", 16, 1, 1000);
        center = (int) bounded(config, "espionage.aptitude.attribute-center", 6, 0, cap);
        aptitudeSpread = (int) bounded(config, "espionage.aptitude.random-spread", 20, 0, 100);
        extraPositionPenalty = bounded(config, "espionage.aptitude.extra-position-penalty", .25, 0, 1);
        rollMultiplier = bounded(config, "espionage.checks.aptitude-multiplier", 1.25, .01, 10);
        luckSpread = (int) bounded(config, "espionage.checks.luck-spread", 75, 0, 1000);
        luckDraws = (int) bounded(config, "espionage.checks.luck-draws", 3, 1, 20);
        rosterLimit = (int) bounded(config, "espionage.intelligence.maximum-roster-size", 23, 0, 1000);
        tiers.clear();
        int previous = -1;
        for (var tier : IntelligenceTier.values()) {
            if (tier == IntelligenceTier.UNKNOWN) continue;
            int index = tier.ordinal() - 1;
            String path = "espionage.intelligence.tiers." + tier.key() + ".";
            int margin = Math.max(previous + 1, (int) bounded(config, path + "minimum-margin", new int[]{0,30,65,100}[index], 0, 10000));
            tiers.put(tier, new TierSettings(margin,
                    bounded(config, path + "uncertainty", new double[]{1.5,.75,.2,.1}[index], .01, 10),
                    bounded(config, path + "roster-fraction", new double[]{.2,.4,.6,.8}[index], 0, 1),
                    bounded(config, path + "maximum-bounded-fraction", new double[]{.75,.65,.5,.5}[index], .01, .99),
                    bounded(config, path + "maximum-relative-width", new double[]{4,3,2,2}[index], .1, 10)));
            previous = margin;
        }
        minimums.clear();
        DEFAULT_MINIMUMS.forEach((field, fallback) -> minimums.put(field, IntelligenceTier.parse(
                config.getString("espionage.intelligence.minimum-tiers." + field, fallback.key()))));
        cashflows.clear();
        var overrides = config.getConfigurationSection("espionage.intelligence.minimum-tiers.cashflows");
        if (overrides != null) for (String category : overrides.getKeys(false))
            cashflows.put(category, IntelligenceTier.parse(overrides.getString(category)));
    }

    private static double nonnegative(ConfigurationSection config, String key, double fallback) {
        double value = config.getDouble(key, fallback);
        return Double.isFinite(value) && value >= 0 ? value : fallback;
    }

    private static double bounded(ConfigurationSection config, String key, double fallback, double min, double max) {
        double value = config.getDouble(key, fallback);
        return Double.isFinite(value) && value >= min && value <= max ? value : Math.max(min, Math.min(max, fallback));
    }

    public static IntelligenceTier tier(int margin) {
        IntelligenceTier result = IntelligenceTier.RUMOURS;
        for (var tier : IntelligenceTier.values())
            if (tier != IntelligenceTier.UNKNOWN && margin >= tiers.get(tier).minimumMargin()) result = tier;
        return result;
    }
    public static TierSettings settings(IntelligenceTier tier) { return tiers.get(tier); }
    public static String metricKey(String metric) {
        if (metric.equals("Legitimacy") || metric.equals("Council size")) return "government";
        if (metric.startsWith("Tax:")) return "taxes";
        if (metric.startsWith("Training:")) return "training";
        if (metric.startsWith("Regiment:")) return metric.endsWith(":Levies") ? "levies" : "professional-army";
        if (metric.startsWith("Installation:")) return "installation-details";
        if (metric.startsWith("Position:")) return "office-aptitude";
        if (metric.contains(":Cashflow:")) return "cashflow";
        String leaf = metric.startsWith("Guild:") ? metric.substring(metric.indexOf(':', 6) + 1) : metric;
        if (leaf.startsWith("Branch:")) return "buildings";
        if (leaf.startsWith("Upgrade:")) return "upgrades";
        if (leaf.equals("Daily net income") || leaf.equals("Income")) return "net-income";
        return leaf.toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
    }
    public static boolean allows(IntelligenceTier tier, String metric) {
        var minimum = minimums.getOrDefault(metricKey(metric), IntelligenceTier.UNKNOWN);
        if (metric.contains(":Cashflow:")) minimum = cashflows.getOrDefault(
                metric.substring(metric.indexOf(":Cashflow:") + 10), minimum);
        return minimum != IntelligenceTier.UNKNOWN && tier != IntelligenceTier.UNKNOWN && tier.ordinal() >= minimum.ordinal();
    }
    public static String reloadPermission() { return reloadPermission; }
    public static double base() { return base; }
    public static int center() { return center; }
    public static int cap() { return cap; }
    public static int aptitudeSpread() { return aptitudeSpread; }
    public static double extraPositionPenalty() { return extraPositionPenalty; }
    public static double rollMultiplier() { return rollMultiplier; }
    public static int luckSpread() { return luckSpread; }
    public static int luckDraws() { return luckDraws; }
    public static int rosterLimit() { return rosterLimit; }

    public static double stabilityPenalty() { return stabilityPenalty; }
    public static double penaltyDays() { return penaltyDays; }
    public static double buildUpDays() { return buildUpDays; }
    public static double startingAptitude() { return startingAptitude; }
    public static double changeCooldownDays() { return changeCooldownDays; }
    /** False when a new Spymaster serves at full aptitude from the start. */
    public static boolean buildsUp() { return buildUpDays > 0 && startingAptitude < 1; }
    public static double vacancyPenalty(SpecialPosition office) { return vacancyPenalties.getOrDefault(office, 10.0); }

    public static Map<String, Double> weights() { return weights; }
    public static String bypassPermission() { return bypassPermission; }
}
