package net.tfminecraft.simplefactions.economy.settlement;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

/** Settlement reads only existing incarnations; it cannot repair or create a treasury while planning. */
public final class SettlementGuilds {
    private SettlementGuilds() {}
    public static Guild capital(Faction faction) {
        Guild guild = ruling(faction);
        if (guild == null) throw new IllegalStateException("Missing ruling guild for settlement: " + faction.getId());
        return guild;
    }

    /** The realm's ruling guild, whose incarnation names the realm and its treasury; null when missing. */
    public static Guild ruling(Faction faction) {
        return faction == null ? null : faction.getGuildHandler().getGuild(faction.getId());
    }
}
