package me.Plugins.SimpleFactions.player.income;

public enum PlayerCashflow {
    EARNINGS("#92d665Earnings"),
    CITIZEN_TAX("#94b572Citizen Tax"),
    DIVIDEND_PAYOUT("#c49e5cDividend Payout"),
    WAGES("#c9a05eWages"),
    VEHICLE_UPKEEP("#a6659fVehicles");

    private final String display;

    PlayerCashflow(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }

    public boolean isIncome() {
        return this == EARNINGS || this == DIVIDEND_PAYOUT || this == WAGES;
    }
}
