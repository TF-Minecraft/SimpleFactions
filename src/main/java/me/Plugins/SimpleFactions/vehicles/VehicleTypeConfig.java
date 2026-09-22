package me.Plugins.SimpleFactions.vehicles;

public final class VehicleTypeConfig {
    private final double upkeep;
    private final int size;
    private final int perPersonLimit;
    private final boolean ignoreLimit;
    private final boolean showOnUpcomingBattleIcon;

    public VehicleTypeConfig(
            double upkeep,
            int size,
            int perPersonLimit,
            boolean ignoreLimit,
            boolean showOnUpcomingBattleIcon) {
        this.upkeep = upkeep;
        this.size = size;
        this.perPersonLimit = perPersonLimit;
        this.ignoreLimit = ignoreLimit;
        this.showOnUpcomingBattleIcon = showOnUpcomingBattleIcon;
    }

    public double getUpkeep() {
        return upkeep;
    }

    public int getSize() {
        return size;
    }

    public int getPerPersonLimit() {
        return perPersonLimit;
    }

    public boolean isIgnoreLimit() {
        return ignoreLimit;
    }

    public boolean isShowOnUpcomingBattleIcon() {
        return showOnUpcomingBattleIcon;
    }
}
