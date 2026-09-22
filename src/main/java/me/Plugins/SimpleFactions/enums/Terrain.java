package me.Plugins.SimpleFactions.enums;

public enum Terrain {
    FARMLAND,
    PLAINS,
    HILLS,
    FOREST,
    HIGHLANDS,
    JUNGLE,
    WATER(false),
    SEA(false),
    BOG,
    MOUNTAIN,
    DRYLANDS,
    UNKNOWN;

    private boolean generatesIncome;

    public boolean generatesIncome() { return generatesIncome; }

    private Terrain() {
        generatesIncome = true;
    }

    private Terrain(boolean i) {
        generatesIncome = i;
    }
}
