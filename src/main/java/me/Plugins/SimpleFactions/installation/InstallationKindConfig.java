package me.Plugins.SimpleFactions.installation;

import java.util.Collections;
import java.util.Map;

public final class InstallationKindConfig {
    private final double dailyUpkeep;
    private final int constructionTimeSeconds;
    private final int radius;
    private final Map<String, Integer> categorySlots;

    public InstallationKindConfig(
            double dailyUpkeep,
            int constructionTimeSeconds,
            int radius,
            Map<String, Integer> categorySlots) {
        this.dailyUpkeep = dailyUpkeep;
        this.constructionTimeSeconds = constructionTimeSeconds;
        this.radius = radius;
        this.categorySlots = Collections.unmodifiableMap(categorySlots);
    }

    public double getDailyUpkeep() {
        return dailyUpkeep;
    }

    public int getConstructionTimeSeconds() {
        return constructionTimeSeconds;
    }

    public int getRadius() {
        return radius;
    }

    public Map<String, Integer> getCategorySlots() {
        return categorySlots;
    }
}
