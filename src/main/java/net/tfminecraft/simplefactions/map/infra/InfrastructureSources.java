package net.tfminecraft.simplefactions.map.infra;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;

public final class InfrastructureSources {
    private InfrastructureSources() {}

    public static Map<Integer, Double> collect(
            Map<Integer, Province> provinces, Collection<Guild> guilds, Collection<Faction> factions,
            Set<Integer> trackProvinces) {
        Map<Integer, Double> sources = new HashMap<>();
        for (Guild guild : guilds) {
            if (guild == null || !guild.isBase() || !guild.hasCapital()) continue;
            add(provinces, sources, guild.getCapital(), GuildModifierOverride.resolve(guild, GuildModifier.INFRASTRUCTURE));
        }
        for (Faction faction : factions) {
            if (faction == null || faction.getInstallationHandler() == null) continue;
            for (Installation installation : faction.getInstallationHandler().getAll()) {
                double amount = switch (installation.getKind()) {
                    case TRAIN_STATION -> Cache.infrastructureStation;
                    case PORT -> Cache.infrastructurePort;
                    case AIRPORT -> Cache.infrastructureAirport;
                    default -> 0;
                };
                add(provinces, sources, installation.getProvince(), amount);
            }
        }
        for (Integer id : trackProvinces) {
            add(provinces, sources, id, Cache.infrastructureTrack);
        }
        return sources;
    }

    private static void add(Map<Integer, Province> provinces, Map<Integer, Double> sources, int id, double amount) {
        Province province = provinces.get(id);
        if (province == null || province.isSea() || amount <= 0) return;
        sources.merge(id, amount, Double::sum);
    }
}
