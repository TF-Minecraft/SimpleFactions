package me.Plugins.SimpleFactions.enums;

public enum GuildModifier {

    TRADE_POWER("#92d665Trade Power", true),
    TRADE_UPKEEP("#d6645aTrade Upkeep", false),
    PRODUCTION("#f2c94cProduction", true),
    TRADE_CARRY("#86d1b0Trade Carry", true),
    DIPLOMATIC_CAPACITY("#56ccf2Diplomatic Capacity", true),
    ADMIN_POWER("#ebde54Administrative Power", true),
    ADMIN_POWER_GAIN("#d1b347Administrative Power Gain", true),
    AUTO_DEALER_TABLES("#76ad9dAuto Dealer Tables", true),
    MAX_HEALTH("#e06c75Max Health", true),
    MAX_MANA("#61afefMax Mana", true),
    MANA_REGEN("#56b6c2Mana Regen", true);

    private final String name;
    private final boolean positive;

    GuildModifier(String name, boolean positive) {
        this.name = name;
        this.positive = positive;
    }

    public String getName() {
        return name;
    }

    public boolean isPositive() {
        return positive;
    }
}

