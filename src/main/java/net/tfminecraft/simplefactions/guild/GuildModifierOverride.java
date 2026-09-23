package net.tfminecraft.simplefactions.guild;

import java.util.Map;

import net.tfminecraft.simplefactions.enums.GuildModifier;

/**
 * Frozen guild modifiers for one income preview. The trade flood reads these
 * instead of the live branch levels, and only on the thread that installed them.
 */
public final class GuildModifierOverride {
    private static final ThreadLocal<GuildModifierOverride> CURRENT = new ThreadLocal<>();

    private final Guild guild;
    private final Map<GuildModifier, Double> amounts;

    private GuildModifierOverride(Guild guild, Map<GuildModifier, Double> amounts) {
        this.guild = guild;
        this.amounts = amounts;
    }

    public static void use(Guild guild, Map<GuildModifier, Double> amounts) {
        CURRENT.set(new GuildModifierOverride(guild, amounts));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static double resolve(Guild guild, GuildModifier modifier) {
        GuildModifierOverride current = CURRENT.get();
        if (current != null && current.guild == guild) {
            Double value = current.amounts.get(modifier);
            if (value != null) {
                return value;
            }
        }
        return guild.getModifier(modifier);
    }
}
