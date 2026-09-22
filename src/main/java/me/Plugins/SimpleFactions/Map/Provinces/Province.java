package me.Plugins.SimpleFactions.Map.Provinces;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.Utils.ModifierMerger;
import me.Plugins.SimpleFactions.enums.FactionModifiers;
import me.Plugins.SimpleFactions.enums.GuildModifier;
import me.Plugins.SimpleFactions.enums.Region;
import me.Plugins.SimpleFactions.enums.Scope;
import me.Plugins.SimpleFactions.enums.Terrain;

public class Province {
    private int id;
    private Map<String, ProvinceDataEntry> data = new HashMap<>();
    private Terrain terrain;
    private int fertility;
    private int centerX;
    private int centerZ;
    private double prosperity = 0;
    private final Set<Integer> neighbours = new HashSet<>();

    public Province() {
        this.id = 0;
        terrain = Terrain.UNKNOWN;
        fertility = 0;
    }

    public Province(int id, String terrain, int fertility) {
        this(id, terrain, fertility, 0, 0);
    }

    public Province(int id, String terrain, int fertility, int centerX, int centerZ) {
        this.id = id;
        try {
            this.terrain = Terrain.valueOf(terrain.toUpperCase());
        } catch (Exception e) {
            this.terrain = Terrain.UNKNOWN;
        }
        this.fertility = fertility;
        this.centerX = centerX;
        this.centerZ = centerZ;
    }

    public int getId() { return id; }
    public boolean isValid() { return id != 0; }
    public Terrain getTerrain() { return terrain; }
    public int getFertility() { return fertility; }
    public int getCenterX() { return centerX; }
    public int getCenterZ() { return centerZ; }
    public ProvinceDataEntry getData(String id) {
        if(data.containsKey(id)) return data.get(id);
        Guild guild = FactionManager.getGuildByString(id);
        if(guild != null) return new ProvinceDataEntry(guild);
        return null;
    }
    public void setData(String id, ProvinceDataEntry entry) {
        data.put(id, entry);
    }
    public void clearGuildData(String guildId) {
        data.remove(guildId);
    }

    public void dropMissingGuilds() {
        data.entrySet().removeIf(e -> FactionManager.getGuildByString(e.getKey()) == null);
    }

    public void calculateTrade(
            ProvinceManager manager,
            Guild guild,
            double prev,
            int distance
    ) {
        double amount;
        double carry = guild.getModifier(GuildModifier.TRADE_CARRY);
        double effectiveDistance = distance / Math.pow(carry, 1.1);
        double factor = Math.pow(getTradeCarry(), effectiveDistance);
        if (prev == -1) {
            // Capital province
            amount = guild.getModifier(GuildModifier.TRADE_POWER);
        } else {
            amount = prev *factor;
        }

        if(amount < 0.5) return;

        ProvinceDataEntry entry = data.get(guild.getId());

        // Stop if we already have equal or better trade
        if (entry != null && entry.getTrade() >= amount) {
            return;
        }

        if (entry == null) {
            entry = new ProvinceDataEntry(guild);
            data.put(guild.getId(), entry);
        }

        entry.setTrade(amount);
        entry.setDistance(distance);

        for (Integer n : neighbours) {
            Province neighbour = manager.get(n);
            if (neighbour != null) {
                neighbour.calculateTrade(manager, guild, amount, distance+1);
            }
        }
    }

    public void calculateProduction(
            ProvinceManager manager,
            Guild guild,
            ProvinceDataEntry prev,
            int distance
    ) {
        double amount;
        double terrainFactor = Math.pow(getTradeCarry(), 0.5);
        double factor = terrainFactor*getTradeFactor(guild);

        if (prev == null) {
            // Capital province
            amount = guild.getModifier(GuildModifier.PRODUCTION);
        } else {
            amount = prev.getProduction()*factor;
        }
        if(amount < 0.1) return;

        ProvinceDataEntry entry = data.get(guild.getId());

        // Stop if we already have equal or better trade
        if (entry != null && entry.getProduction() >= amount) {
            return;
        }

        if (entry == null) {
            entry = new ProvinceDataEntry(guild);
            data.put(guild.getId(), entry);
        }

        entry.setProduction(amount);

        for (Integer n : neighbours) {
            Province neighbour = manager.get(n);
            if (neighbour != null) {
                neighbour.calculateProduction(manager, guild, entry, distance+1);
            }
        }
    }

    public double getTradeFactor(Guild guild) {
        double trade = getGuildTrade(guild);
        if(trade == 0) return 0.05;
        double K = 2.5 / Math.pow(getTradeCarry(), 0.5);
        return (trade / (trade + K));
    }

    public double getIncome(Guild guild) {
        double trade = getGuildTrade(guild);
        if (trade <= 0) return 0;

        double totalTrade = getTotalTrade();
        if (totalTrade <= 0) return 0;

        // 1) Reservation (competition / stealing)
        double share = trade / totalTrade;
        double reserved = prosperity * share;

        return reserved;
    }

    public void calculateTariffs() {

    }

    public void calculateProsperity() {
        double total = 0;
        int participants = 0;
        for(ProvinceDataEntry entry : data.values()) {
            List<FactionModifier> modifiers = getModifiersForGuild(entry.getGuild());
            double carry = entry.getGuild().getModifier(GuildModifier.TRADE_CARRY);
            double distance = entry.getDistance(); // ensure double

            double weight =
                Math.max(0.0, 1.0 + carry * 0.2 - distance * 0.1);
            double productionWeight = Math.min(1.5, 1+entry.getProduction()/100.0);
            double mod = getModifierForGuild(entry.getGuild(), FactionModifiers.PRODUCTION, modifiers);

            total += entry.getProduction()*mod* weight*productionWeight;

            if (getGuildTrade(entry.getGuild()) > 0.5) { // threshold
                participants++;
            }
        }

        double exponent = 1 + Math.min(0.1, participants * 0.005);

        total = Math.pow(total, exponent);
        total = Math.round(total * 100.0) / 100.0;
        this.prosperity = total;
    }
    public double getTradeCarry() {
        return Cache.tradeCarry.getOrDefault(terrain, 0.5);
    }
    public Set<Integer> getNeighbours() {
        return Collections.unmodifiableSet(neighbours);
    }

    public void addNeighbour(int provinceId) {
        neighbours.add(provinceId);
    }

    public double getTotalTrade() {
        double total = 0;
        for (ProvinceDataEntry entry : data.values()) {
            total += entry.getTrade();
        }
        return total;
    }

    public double getGuildTrade(Guild guild) {
        ProvinceDataEntry entry = data.get(guild.getId());
        return entry == null ? 0 : entry.getTrade()*getModifierForGuild(guild, FactionModifiers.TRADE_POWER, getModifiersForGuild(guild));
    }

    public double getTradeShare(Guild guild) {
        if(!data.containsKey(guild.getId())) return 0;
        return getGuildTrade(guild)/getTotalTrade();
    }

    public double getProsperity() {
        return prosperity;
    }

    public Province cloneShell() {
        Province p = new Province(id, terrain.name(), fertility, centerX, centerZ);
        p.neighbours.addAll(this.neighbours);
        return p;
    }

    public void clearData() {
        data.clear();
    }

    public Map<String, ProvinceDataEntry> getAllData() {
        return data;
    }

    public void setProsperity(double p) {
        this.prosperity = p;
    }

    public Faction getOwner() {
        return TitleManager.getByProvince(id);
    }

    public List<FactionModifier> getModifiersForGuild(Guild guild) {
        List<FactionModifier> mods = new ArrayList<>();
        Faction owner = getOwner();
        Faction host = guild.getFaction();
        if(owner == null) {
            mods.addAll(host.getModifiers(guild.getId(), Scope.DOMESTIC_GUILDS, Region.WILDERNESS));
        } else if(!owner.getId().equalsIgnoreCase(host.getId())){
            if(RelationManager.isOnOverlordPath(owner, host)) {
                mods.addAll(host.getModifiers(guild.getId(), Scope.DOMESTIC_GUILDS, Region.VASSAL_TERRITORY));
                mods.addAll(owner.getModifiers(guild.getId(), Scope.OVERLORD_GUILDS, Region.OUR_TERRITORY));
            } else if(RelationManager.isOnOverlordPath(host, owner)) {
                mods.addAll(owner.getModifiers(guild.getId(), Scope.VASSAL_GUILDS, Region.OUR_TERRITORY));
            } else {
                mods.addAll(host.getModifiers(guild.getId(), Scope.DOMESTIC_GUILDS, Region.FOREIGN_TERRITORY));
                mods.addAll(owner.getModifiers(guild.getId(), Scope.FOREIGN_GUILDS, Region.OUR_TERRITORY));
            }
            if(owner.getDiplomacyHandler().hasTradeRelation(host.getId())) {
                mods.addAll(owner.getDiplomacyHandler().getTradeModifiersFor(host.getId()));
            }
        } else {
            mods.addAll(host.getModifiers(guild.getId(), Scope.DOMESTIC_GUILDS, Region.OUR_TERRITORY));
        }
        return ModifierMerger.merge(mods);
    }

    public double getModifierForGuild(Guild guild, FactionModifiers mod, List<FactionModifier> list) {
        for(FactionModifier m : list) {
            if(m.getType().equals(mod)) return Math.max(0, 1+(m.getAmount()/100.0));
        }
        return 1;
    }

    public boolean isSea() {
        return terrain == Terrain.SEA || terrain == Terrain.WATER;
    }
}
