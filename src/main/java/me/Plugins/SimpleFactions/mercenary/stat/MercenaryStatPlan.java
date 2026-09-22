package me.Plugins.SimpleFactions.mercenary.stat;

import me.Plugins.SimpleFactions.enums.GuildModifier;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/** The stat bonuses a company's upgrades are worth right now. */
public record MercenaryStatPlan(double maxHealth, double maxMana, double manaRegen) {
    public static final MercenaryStatPlan EMPTY = new MercenaryStatPlan(0, 0, 0);

    public static MercenaryStatPlan of(MercenaryCompany company) {
        if (company == null) return EMPTY;
        return new MercenaryStatPlan(
                company.getModifier(GuildModifier.MAX_HEALTH),
                company.getModifier(GuildModifier.MAX_MANA),
                company.getModifier(GuildModifier.MANA_REGEN));
    }

    public boolean isEmpty() {
        return maxHealth <= 0 && maxMana <= 0 && manaRegen <= 0;
    }
}
