package net.tfminecraft.simplefactions.mercenary.company;

import java.util.Collection;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.utils.Formatter;

/** Server-wide lookups over mercenary companies. */
public final class MercenaryCompanies {
    private MercenaryCompanies() {
    }

    /** A player may only be enlisted in one company anywhere on the server. */
    public static MercenaryCompany findByMember(String player) {
        return findByMember(player, FactionManager.getAllGuilds());
    }

    public static MercenaryCompany findByMember(String player, Collection<Guild> guilds) {
        if (player == null || guilds == null) return null;
        for (Guild g : guilds) {
            if (g == null) continue;
            MercenaryCompany company = g.getCompany();
            if (company != null && company.isEnlisted(player)) return company;
        }
        return null;
    }

    public static MercenaryCompany findByName(String name) {
        return findByName(name, FactionManager.getAllGuilds());
    }

    public static MercenaryCompany findByName(String name, Collection<Guild> guilds) {
        if (name == null || guilds == null) return null;
        for (Guild g : guilds) {
            if (g == null) continue;
            MercenaryCompany company = g.getCompany();
            if (company != null && company.getName() != null
                    && Formatter.formatId(company.getName()).equalsIgnoreCase(Formatter.formatId(name))) {
                return company;
            }
        }
        return null;
    }
}
