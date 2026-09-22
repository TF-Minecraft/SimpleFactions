package me.Plugins.SimpleFactions.Objects.Handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;

public class GuildHandler {
    private Faction f;
    private Map<String, Guild> guilds = new HashMap<>();

    public GuildHandler(Faction f) {
        this.f = f;
    }

    public List<Guild> getGuilds() {
        return new ArrayList<>(guilds.values());
    }

    public List<Guild> getReleasableGuilds() {
        List<Guild> releasable = new ArrayList<>();
        for(Guild g : guilds.values()) {
            if(g.canBeElevated(null)) releasable.add(g);
        }
        return releasable;
    }

    public Guild getGuild(String id) {
        return guilds.getOrDefault(id, null);
    }

    public void removeGuild(String id) {
        removeGuild(id, true, true);
    }

    public void removeGuild(String id, boolean dissolveEmptySettlement) {
        removeGuild(id, dissolveEmptySettlement, true);
    }

    public void removeGuild(String id, boolean dissolveEmptySettlement, boolean revalidateClaims) {
        Guild g = guilds.get(id);
        int cap = (g != null && !g.isBase() && g.hasCapital()) ? g.getCapital() : -1;
        guilds.remove(id);
        clearProvinceData(id);
        if (dissolveEmptySettlement && cap != -1 && f.getSettlementHandler() != null) {
            f.getSettlementHandler().onGuildDepartedCapital(cap);
        }
        if (revalidateClaims && f.getProvinceHandler() != null) {
            f.getProvinceHandler().revalidateClaims();
        }
    }

    public Guild getGuildByMember(String member) {
        for(Guild g : guilds.values()) {
            if(g.isMember(member)) return g;
        }
        return null;
    }

    public boolean isGuildLeader(String p) {
        for(Guild g : guilds.values()) {
            if(g.isLeader(p)) return true;
        }
        return false;
    }

    public void forceKick(String member) {
        for(Guild g : guilds.values()) {
            if(g.isMember(member)) g.kick(member);
        }
    }

    private void clearProvinceData(String guildId) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin == null) {
            return;
        }
        clearProvinceData(plugin.getProvinceManager(), guildId);
        clearProvinceData(plugin.getProvinceSnapshot(), guildId);
    }

    private static void clearProvinceData(ProvinceManager manager, String guildId) {
        if (manager == null) {
            return;
        }
        manager.clearGuildData(guildId);
    }

    public void addGuild(Guild g) {
        guilds.put(g.getId(), g);
    }

    public List<String> getAllMembers() {
        List<String> members = new ArrayList<>();
        for(Guild g : guilds.values()) {
            members.addAll(g.getMembers());
        }
        Collections.sort(members);
        return members;
    }

    public double getTotalTradePower() {
        double total = 0;
        for(Guild g : guilds.values()) {
            total += g.getTradeBreakdown().getTradePower();
        }
        return total;
    }
}
