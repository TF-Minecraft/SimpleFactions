package net.tfminecraft.simplefactions.managers.inventory;

import net.tfminecraft.simplefactions.enums.GuildModifier;

/** Presentation only: a reduction can be a benefit, and zero has no direction. */
final class EffectDisplay {
    private static final String NEUTRAL = "#7a706a";

    private EffectDisplay() {}

    static String color(GuildModifier modifier, double value) {
        if (value == 0) return NEUTRAL;
        return (value > 0) == modifier.isPositive() ? "#4fd945" : "#cf493a";
    }

    static String rangeColor(GuildModifier modifier, double first, double second) {
        String firstColor = color(modifier, first);
        return firstColor.equals(color(modifier, second)) ? firstColor : NEUTRAL;
    }

    static String wealth(double amount) {
        if (amount == 0) return NEUTRAL + "0d";
        return (amount > 0 ? "#93c9a7+" : "#d13530") + amount + "d";
    }
}
