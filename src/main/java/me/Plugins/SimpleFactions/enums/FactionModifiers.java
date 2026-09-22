package me.Plugins.SimpleFactions.enums;

public enum FactionModifiers {

    TRIBUTE(false, false),
    TAX_MULTIPLIER(false, false),
    LEVY(false, false),
    NODE_SPEED(true, false),
    MILITARY_UPKEEP(false, false),
    PRESTIGE(true, false),
    PRESTIGE_BONUS(true, false),
    DE_JURE(false, false),
    STABILITY_INFLUENCE(true, false),
    TRADE_POWER(true, true),
    PRODUCTION(true, true),
    DIPLOMATIC_CAPACITY_MULTIPLIER(true, false),
    ADMIN_POWER_MULTIPLIER(true, false),
    ADMIN_POWER_GAIN_MULTIPLIER(true, false);

    private final boolean positiveIsGood;
    private final boolean affectsEconomy;

    FactionModifiers(boolean positiveIsGood, boolean affectsEconomy) {
        this.positiveIsGood = positiveIsGood;
        this.affectsEconomy = affectsEconomy;
    }

    public boolean isPositiveGood() {
        return positiveIsGood;
    }

    public boolean affectsEconomy() {
        return affectsEconomy;
    }
}
