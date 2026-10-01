package net.tfminecraft.simplefactions.installation;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class InstallationKindConfig {
    public record Level(double dailyUpkeep, int constructionTimeSeconds, Map<String, Integer> categorySlots) {
        public Level {
            categorySlots = Collections.unmodifiableMap(categorySlots);
        }
    }

    private final int radius;
    private final Map<Integer, Level> levels;

    public InstallationKindConfig(int radius, Map<Integer, Level> levels) {
        this.radius = radius;
        this.levels = Collections.unmodifiableMap(new TreeMap<>(levels));
    }

    public Level getLevel(int level) {
        return levels.get(Math.min(Math.max(level, 1), getMaximumLevel()));
    }

    public int getMaximumLevel() {
        return levels.size();
    }

    public Map<Integer, Level> getLevels() {
        return levels;
    }

    public double getDailyUpkeep() {
        return getDailyUpkeep(1);
    }

    public double getDailyUpkeep(int level) {
        return getLevel(level).dailyUpkeep();
    }

    public int getConstructionTimeSeconds() {
        return getConstructionTimeSeconds(1);
    }

    public int getConstructionTimeSeconds(int level) {
        return getLevel(level).constructionTimeSeconds();
    }

    public int getRadius() {
        return radius;
    }

    public Map<String, Integer> getCategorySlots() {
        return getCategorySlots(1);
    }

    public Map<String, Integer> getCategorySlots(int level) {
        return getLevel(level).categorySlots();
    }
}
