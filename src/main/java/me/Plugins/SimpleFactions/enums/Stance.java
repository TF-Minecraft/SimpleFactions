package me.Plugins.SimpleFactions.enums;

public enum Stance {
    OPPOSE("#d13530Opposed"),
    NEUTRAL("#decc68Neutral"),
    SUPPORT("#4bc957Support");

    private String display;

    private Stance(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
