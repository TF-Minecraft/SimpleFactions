package me.Plugins.SimpleFactions.enums;

public enum Brackets {

    CITIZEN_TAX("Citizen Tax"),
    GUILD_TAX("Guild Tax"),
    VASSAL_TAX("Vassal Tax"),
    DIVIDEND_TAX("Dividend Tax"),
    TARIFFS("Tariffs");

    private final String display;

    Brackets(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}

