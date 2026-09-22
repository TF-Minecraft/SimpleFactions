package me.Plugins.SimpleFactions.Managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;

import me.Plugins.SimpleFactions.Guild.Branch.Branch;
import me.Plugins.SimpleFactions.Guild.income.Cashflow;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Map.Provinces.ProvinceDataEntry;
import me.Plugins.SimpleFactions.Objects.Bracket;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.TaxHandler;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.War.resolution.PillageTradeHit;
import me.Plugins.SimpleFactions.enums.GuildModifier;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;

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
        double upkeepFactor = guild.getModifier(GuildModifier.TRADE_UPKEEP);
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
        ProvinceManager live = this;
        ProvinceManager snap = SimpleFactions.getInstance().getProvinceSnapshot();
        
        // Save original ledger states before preview
        Map<String, Double> originalNetIncomes = new HashMap<>();
        for(Guild guild : FactionManager.getAllGuilds()) {
            if (guild.hasCapital()) {
                originalNetIncomes.put(guild.getId(), guild.getLedger().getNetIncome());
            }
        }
        
        // Apply law change to snapshot
        snap.copyAllDataFrom(live);
        Law old = group.getCurrent();
        TaxHandler tax = f.getTaxHandler();
        tax.saveState();
        f.applyLaw(law, group);
        // Full recalculation - resolves all interdependencies
        snap.recalculate();
        
        // Collect deltas
        Map<Guild, Double> map = new HashMap<>();
        for(Guild guild : FactionManager.getAllGuilds()) {
            if (!guild.hasCapital()) continue;
            double delta = guild.getLedger().getNetIncome() - originalNetIncomes.get(guild.getId());
            map.put(guild, Math.round(delta * 100.0) / 100.0);
        }
        // Restore original state
        group.setCurrent(old);
        tax.restoreState();
        snap.recalculate(); // Recalculate snapshot back to live state
        
        return map;
    }

    public Map<Guild, Double> previewFavourRepressIncomeExact(Faction f, Guild g, boolean favour) {
        ProvinceManager live = this;
        ProvinceManager snap = SimpleFactions.getInstance().getProvinceSnapshot();
        
        // Save original ledger states before preview
        Map<String, Double> originalNetIncomes = new HashMap<>();
        for(Guild guild : FactionManager.getAllGuilds()) {
            if (guild.hasCapital()) {
                originalNetIncomes.put(guild.getId(), guild.getLedger().getNetIncome());
            }
        }
        
        // Apply favour/repress change to snapshot
        snap.copyAllDataFrom(live);
        if(favour) {
            f.getGovernment().toggleFavour(g);
        } else {
            f.getGovernment().toggleRepress(g);
        }
        TaxHandler tax = f.getTaxHandler();
        tax.saveState();
        // Full recalculation - resolves all interdependencies
        snap.recalculate();

        // Full recalculation - resolves all interdependencies
        snap.recalculate();
        
        // Collect deltas
        Map<Guild, Double> map = new HashMap<>();
        for(Guild guild : FactionManager.getAllGuilds()) {
            if (!guild.hasCapital()) continue;
            double delta = guild.getLedger().getNetIncome() - originalNetIncomes.get(guild.getId());
            map.put(guild, Math.round(delta * 100.0) / 100.0);
        }
        // Restore original state
        if(favour) {
            f.getGovernment().toggleFavour(g);
        } else {
            f.getGovernment().toggleRepress(g);
        }
        tax.restoreState();
        snap.recalculate(); // Recalculate snapshot back to live state
        
        return map;
    }

    public Map<Guild, Double> previewTradeAgreementIncomeExact(Faction origin, Faction target, RelationType agreement) {
        ProvinceManager live = this;
        ProvinceManager snap = SimpleFactions.getInstance().getProvinceSnapshot();
        
        // Save original ledger states before preview
        Map<String, Double> originalNetIncomes = new HashMap<>();
        for(Guild guild : FactionManager.getAllGuilds()) {
            if (guild.hasCapital()) {
                originalNetIncomes.put(guild.getId(), guild.getLedger().getNetIncome());
            }
        }
        
        // Apply favour/repress change to snapshot
        snap.copyAllDataFrom(live);
        RelationType current = origin.getDiplomacyHandler().getTradeRelation(target.getId());
        RelationType targetCurrent = target.getDiplomacyHandler().getTradeRelation(origin.getId());
        if(agreement != null) {
            origin.getDiplomacyHandler().setTradeRelation(target, agreement);
            if(agreement.hasLink()) {
                target.getDiplomacyHandler().setTradeRelation(origin, agreement.getLink());
            }
        } else {
            origin.getDiplomacyHandler().removeTradeRelation(target.getId());
            if(current.isMutual()) {
                target.getDiplomacyHandler().removeTradeRelation(origin.getId());
            }
        }
        // Full recalculation - resolves all interdependencies
        snap.recalculate();

        // Full recalculation - resolves all interdependencies
        snap.recalculate();
        
        // Collect deltas
        Map<Guild, Double> map = new HashMap<>();
        for(Guild guild : FactionManager.getAllGuilds()) {
            if (!guild.hasCapital()) continue;
            double delta = guild.getLedger().getNetIncome() - originalNetIncomes.get(guild.getId());
            map.put(guild, Math.round(delta * 100.0) / 100.0);
        }
        // Restore original state
        if(current != null) {
            origin.getDiplomacyHandler().setTradeRelation(target, current);
        } else {
            origin.getDiplomacyHandler().removeTradeRelation(target.getId());
        }
        if(targetCurrent != null) {
            target.getDiplomacyHandler().setTradeRelation(origin, targetCurrent);
        } else {
            target.getDiplomacyHandler().removeTradeRelation(origin.getId());
        }
        snap.recalculate(); // Recalculate snapshot back to live state
        
        return map;
    }

    public double previewUpgradeIncomeExact(Guild guild, Branch branch) {
        ProvinceManager live = this;
        ProvinceManager snap = SimpleFactions.getInstance().getProvinceSnapshot();

        double liveIncomeBefore = live.getIncome(guild, false);

        snap.copyAllDataFrom(live);
        branch.levelUp();
        snap.recalculateForSingleGuild(guild, false);
        
        double snapTradeAfter = snap.getIncome(guild, false);
        double tradeIncomeChange = snapTradeAfter - liveIncomeBefore;
        
        // Apply the same tax rate to the income change to estimate net impact
        Faction f = guild.getFaction();
        double guildTaxRate = f.getTaxRate(TaxTarget.GUILDS, guild.getId(), true) / 100.0;
        double estimatedNetChange = tradeIncomeChange * (1.0 - guildTaxRate);

        branch.levelDown();
        
        return Math.round(estimatedNetChange * 100.0) / 100.0;
    }

    public double previewDowngradeIncomeExact(Guild guild, Branch branch) {
        ProvinceManager live = this;
        ProvinceManager snap = SimpleFactions.getInstance().getProvinceSnapshot();

        double liveIncomeBefore = live.getIncome(guild);

        // Sync snapshot to live state
        snap.copyAllDataFrom(live);

        // Apply upgrade in snapshot context
        branch.levelDown();
        snap.recalculateForSingleGuild(guild, false);
        double snapIncomeAfter = snap.getIncome(guild, false);
        double tradeIncomeChange = snapIncomeAfter - liveIncomeBefore;
        
        // Apply the same tax rate to the income change to estimate net impact
        Faction f = guild.getFaction();
        double guildTaxRate = f.getTaxRate(TaxTarget.GUILDS, guild.getId(), true) / 100.0;
        double estimatedNetChange = tradeIncomeChange * (1.0 - guildTaxRate);

        // Revert upgrade
        branch.levelUp();

        return Math.round(estimatedNetChange * 100.0) / 100.0;
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
