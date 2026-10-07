package net.tfminecraft.simplefactions.mercenary.stat;

import org.bukkit.entity.Player;

/** Where a plan actually meets MythicLib, MMOCore and the health attribute. */
public interface MercenaryStatApplier {
    /** False when the soft dependencies needed to apply a plan are missing. */
    boolean isAvailable();

    void apply(Player player, MercenaryStatPlan plan);

    /** Safe even without soft dependencies; still removes owned Bukkit attributes. */
    void strip(Player player);
}
