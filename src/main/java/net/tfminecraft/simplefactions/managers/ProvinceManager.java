package net.tfminecraft.simplefactions.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.income.BranchIncomePreview;
import net.tfminecraft.simplefactions.guild.income.EconomicPreview;
import net.tfminecraft.simplefactions.guild.income.Cashflow;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.war.resolution.PillageTradeHit;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;

public class ProvinceManager {
    private Map<Integer, Province> provinces = new HashMap<>();
    private long stateVersion = 0;
    private long lastCalculatedVersion = -1;

    public void markDirty() {
        stateVersion++;
    }

    public void recalculateIfNeeded() {
        if (stateVersion == lastCalculatedVersion) return;

        recalculate();
        lastCalculatedVersion = stateVersion;
    }

    public List<Province> getProvinces() { return new ArrayList<>(provinces.values()); }

    public Province get(int id) {
        return provinces.getOrDefault(id, new Province());
    }

    public void start(Map<Integer, Province> map) {
        provinces = map;
    }

    public ProvinceManager createSnapshotShell() {
        ProvinceManager snap = new ProvinceManager();
        Map<Integer, Province> map = new HashMap<>();

        for (Province p : provinces.values()) {
            map.put(p.getId(), p.cloneShell());
        }

        snap.start(map);
        return snap;
    }

    public void clearGuildData(String guildId) {
        if (guildId == null) {
            return;
        }
        for (Province p : provinces.values()) {
            p.clearGuildData(guildId);
        }
    }

    public void dropMissingGuilds() {
        for (Province p : provinces.values()) {
            p.dropMissingGuilds();
        }
    }

    public void recalculate() {
        if (!Cache.provincesEnabled) {
            return;
        }
        dropMissingGuilds();
        for(Guild g : FactionManager.getAllGuilds()) {
            if (!g.hasCapital()) continue;
            recalculateGuild(g);
        }
        for(Guild g : FactionManager.getAllGuilds()) {
            if (!g.hasCapital()) continue;
            recalculateProduction(g);
        }
        recalculateProsperity();
        for(Guild guild : FactionManager.getAllGuilds()) getIncome(guild);
    }

    public void recalculateForSingleGuild(Guild g, boolean save) {
        if (!Cache.provincesEnabled) {
            return;
        }
        if (!g.hasCapital()) return;
        dropMissingGuilds();
        recalculateGuild(g);
        recalculateProduction(g);
        if(save) {
            for(Guild guild : FactionManager.getAllGuilds()) {
                getIncome(guild);
            }
        }
        recalculateProsperity();
    }

    private void recalculateProsperity() {
        for (Province p : provinces.values()) {
            p.calculateProsperity();
        }
    }

    private void recalculateProduction(Guild guild) {
        Province capital = provinces.get(guild.getCapital());
        if (capital != null) {
            capital.calculateProduction(this, guild, null, 0);
        }
    }

    private void recalculateGuild(Guild guild) {
        // 1) Clear only this guild’s data
        clearGuildData(guild.getId());

        // 2) Recalculate trade graph
        Province capital = provinces.get(guild.getCapital());
        if (capital != null) {
            capital.calculateTrade(this, guild, -1, 0);
        }
    }

    public double getIncome(Guild guild, boolean save) {
        if (!Cache.provincesEnabled) {
            if (save) {
                guild.getTradeBreakdown().clear();
            }
            return 0;
        }
        if(save) guild.getTradeBreakdown().clear();
        double income = 0;
        double upkeep = 0;
        double trade = 0;
        double upkeepFactor = GuildModifierOverride.resolve(guild, GuildModifier.TRADE_UPKEEP);
        double tariffs = 0;

        for (Province province : provinces.values()) {
            if(!province.getTerrain().generatesIncome()) continue;
            double provinceIncome = province.getIncome(guild);
            if(provinceIncome == 0) continue;
            //if(provinceIncome > guildTrade) provinceIncome = guildTrade;
            upkeep += provinceIncome*province.getTradeFactor(guild)*upkeepFactor;
            Faction owner = TitleManager.getByProvince(province.getId());
            if(owner != null) {
                if(save) guild.getTradeBreakdown().registerIncome(owner, provinceIncome);
                if(owner.getTaxHandler().hasTariffs() && !RelationManager.sameRealm(owner, guild.getFaction())){
                    double provinceTariffs = provinceIncome*owner.getTaxRate(TaxTarget.TARIFFS, guild.getFaction().getId(), true)/100.0;
                    tariffs+=provinceTariffs;
                    if(save) {
                        guild.getTradeBreakdown().registerTariffs(owner, provinceTariffs);
                    }
                }
            }
            income += provinceIncome;
            
            if(owner == null) continue;
            
            trade += getTotalTrade(guild);
        }
        if(save) {
            guild.getTradeBreakdown().setTariffs(tariffs);
            guild.getTradeBreakdown().setUpkeep(upkeep);
            income = PillageTradeHit.applyToIncome(guild, income);
            guild.getTradeBreakdown().setIncome(income);
            guild.getTradeBreakdown().setTradePower(trade);
        }
        income-=upkeep;

        // Optional rounding for display
        return Math.round(income * 100.0) / 100.0;
    }

    public double getIncome(Guild guild) {
        return getIncome(guild, true);
    }

    public double getTotalTrade(Guild guild) {
        double total = 0;
        for(Province p : provinces.values()) {
            total += p.getGuildTrade(guild);
        }
        return total;
    }

    public Map<Guild, Double> previewLawIncomeExact(Faction f, LawGroup group, Law law) {
        return EconomicPreview.law(this, f, group, law);
    }

    public Map<Guild, Double> previewFavourRepressIncomeExact(Faction f, Guild g, boolean favour) {
        return EconomicPreview.favour(this, g, favour);
    }

    public Map<Guild, Double> previewTradeAgreementIncomeExact(Faction origin, Faction target, RelationType agreement) {
        return EconomicPreview.trade(this, origin, target, agreement);
    }

    public double previewUpgradeIncomeExact(Guild guild, Branch branch) {
        return BranchIncomePreview.estimate(this, guild, branch, 1);
    }

    public double previewDowngradeIncomeExact(Guild guild, Branch branch) {
        return BranchIncomePreview.estimate(this, guild, branch, -1);
    }

    //Simulation
    public void copyAllDataFrom(ProvinceManager source) {
        for (Province src : source.provinces.values()) {
            Province dst = provinces.get(src.getId());
            if (dst == null) continue;

            dst.clearData();

            for (Map.Entry<String, ProvinceDataEntry> e : src.getAllData().entrySet()) {
                dst.setData(e.getKey(), e.getValue().copy());
            }

            dst.setProsperity(src.getProsperity());
        }
    }

    /**
     * Previews the economic impact of changing a faction's tariff rate.
     * Efficiently calculates tariff deltas without traversing the trade graph,
     * since tariffs don't affect trade distribution.
     * 
     * @param faction The faction changing its tariff rate
     * @param newTariffRate The new tariff rate (0-100)
     * @return Map of guilds to their tariff impact (negative = lose income, positive = gain income)
     */
    public Map<Guild, Double> previewTariffRateChange(Faction faction, double newTariffRate) {
        Map<Guild, Double> impacts = new HashMap<>();
        
        // Initialize all guilds with 0 impact
        for (Guild guild : FactionManager.getAllGuilds()) {
            impacts.put(guild, 0.0);
        }
        
        double oldTariffRate = faction.getTaxHandler().getTariffs();
        
        // Loop through all provinces
        for (Province province : provinces.values()) {
            Faction owner = TitleManager.getByProvince(province.getId());
            // Only care about provinces owned by the faction changing tariffs
            if (owner == null || !owner.equals(faction)) continue;
            
            // For each guild that might trade in this province
            for (Guild guild : FactionManager.getAllGuilds()) {
                // Skip guilds in same realm (no tariffs within realm)
                if (RelationManager.sameRealm(faction, guild.getFaction())) continue;
                
                double provinceIncome = province.getIncome(guild);
                if (provinceIncome == 0) continue;
                
                // Calculate tariff impact delta
                double oldTariff = provinceIncome * (oldTariffRate / 100.0);
                double newTariff = provinceIncome * (newTariffRate / 100.0);
                double tariffDelta = -(newTariff - oldTariff); // Negative because it reduces guild income
                
                impacts.put(guild, impacts.get(guild) + tariffDelta);
            }
        }
        
        return impacts;
    }
}
