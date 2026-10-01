package net.tfminecraft.simplefactions.enums;

public enum Brackets {

    CITIZEN_TAX("Citizen Tax"),
    GUILD_TAX("Guild Tax"),
    VASSAL_TAX("Vassal Tax"),
    DIVIDEND_TAX("Dividend Tax"),
    TARIFFS("Tariffs"),
    HUB_TAX("Hub Tax"),
    VEHICLE_TAX("Vehicle Tax (% of upkeep)"),
    REGISTRATION_FEE("Registration Fee (x upkeep)"),
    TRANSFER_FEE("Transfer Fee (x upkeep)");

    private final String display;

    Brackets(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}

