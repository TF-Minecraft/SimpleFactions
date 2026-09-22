package me.Plugins.SimpleFactions.enums;

public enum Scope {

    FACTION("Faction"),
    VASSALS("All Vassals"),
    THEIR_GUILDS("Their Guilds"),
    FAVOURED_VASSALS("Favoured Vassals"),
    REPRESSED_VASSALS("Repressed Vassals"),
    FAVOURED_GUILDS("Favoured Guilds"),
    REPRESSED_GUILDS("Repressed Guilds"),
    DOMESTIC_GUILDS("Domestic Guilds"),
    FOREIGN_GUILDS("Foreign Guilds"),
    VASSAL_GUILDS("Vassal Guilds"),
    OVERLORD_GUILDS("Overlord Guilds");

    private final String display;

    Scope(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
