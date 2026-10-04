package net.tfminecraft.simplefactions.guild.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.Highway.HubSite;
import net.tfminecraft.simplefactions.guild.hub.HighwaySnapshot;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService;
import net.tfminecraft.simplefactions.guild.network.NetworkSummary.GuildLabel;
import net.tfminecraft.simplefactions.guild.network.NetworkSummary.Summary;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;

/** Reads the current snapshot into {@link NetworkSummary}. Does not recalculate trade. */
public final class CurrentNetworks {
    private CurrentNetworks() { }

    public static List<Summary> now() {
        return from(HighwaySnapshot.current());
    }

    public static List<Summary> from(HighwaySnapshot snapshot) {
        HighwaySnapshot source = snapshot == null ? HighwaySnapshot.current() : snapshot;
        return NetworkSummary.summarize(
                source.graph(),
                hubs(source),
                guilds(),
                factions(),
                Cache.chapterName,
                CurrentNetworks::trade);
    }

    private static Map<String, Set<HubSite>> hubs(HighwaySnapshot snapshot) {
        Map<String, Set<HubSite>> hubbed = new HashMap<>();
        for (Guild guild : SupplyHubService.allGuilds()) {
            if (guild == null || guild.getId() == null) continue;
            Set<HubSite> sites = snapshot.hubbed(guild.getId());
            if (sites.isEmpty()) continue;
            hubbed.put(guild.getId(), sites);
        }
        return hubbed;
    }

    private static List<GuildLabel> guilds() {
        List<GuildLabel> labels = new ArrayList<>();
        for (Guild guild : SupplyHubService.allGuilds()) {
            if (guild == null || guild.getId() == null) continue;
            labels.add(new GuildLabel(guild.getId(), guild.getName()));
        }
        return labels;
    }

    private static Map<String, String> factions() {
        Map<String, String> names = new HashMap<>();
        if (FactionManager.factions == null) return names;
        for (Faction faction : FactionManager.factions) {
            if (faction == null || faction.getId() == null) continue;
            names.put(faction.getId(), faction.getName());
        }
        return names;
    }

    private static double trade(String guildId, int provinceId) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin == null || plugin.getProvinceManager() == null || guildId == null) return 0;
        Province province = plugin.getProvinceManager().get(provinceId);
        if (province == null) return 0;
        Guild guild = FactionManager.getGuildByString(guildId);
        return guild == null ? 0 : province.getRawGuildTrade(guild);
    }
}
