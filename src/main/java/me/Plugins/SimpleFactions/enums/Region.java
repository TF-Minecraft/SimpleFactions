package me.Plugins.SimpleFactions.enums;

public enum Region {

    OUR_TERRITORY("Our Territory"),
    THEIR_TERRITORY("Their Territory"),
    FOREIGN_TERRITORY("Foreign Territory"),
    WILDERNESS("Wilderness"),
    VASSAL_TERRITORY("Vassal Territory");

    private final String display;

    Region(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
