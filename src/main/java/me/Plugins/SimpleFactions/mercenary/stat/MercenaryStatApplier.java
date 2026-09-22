package me.Plugins.SimpleFactions.mercenary.stat;

import org.bukkit.entity.Player;

/** Where a plan actually meets MythicLib, MMOCore and the health attribute. */
public interface MercenaryStatApplier {
    /** False when the soft dependencies are missing, which makes every call a no-op. */
    boolean isAvailable();

    void apply(Player player, MercenaryStatPlan plan);

    /** Must be safe to call when nothing was ever applied. */
    void strip(Player player);
}
