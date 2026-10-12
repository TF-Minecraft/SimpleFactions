package net.tfminecraft.simplefactions.economy.settlement;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

/** Settlement reads only existing incarnations; it cannot repair or create a treasury while planning. */
public final class SettlementGuilds {
    private SettlementGuilds() {}
    public static Guild capital(Faction faction) {
        Guild guild = faction.getGuildHandler().getGuild(faction.getId());
        if (guild == null) throw new IllegalStateException("Missing ruling guild for settlement: " + faction.getId());
        return guild;
    }
}
