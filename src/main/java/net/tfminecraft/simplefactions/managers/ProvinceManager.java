package net.tfminecraft.simplefactions.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.income.BranchIncomePreview;
import net.tfminecraft.simplefactions.guild.income.EconomicPreview;
import net.tfminecraft.simplefactions.guild.income.Cashflow;
import net.tfminecraft.simplefactions.guild.income.TradeUpkeep;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.guild.hub.Highway;
import net.tfminecraft.simplefactions.guild.hub.Highway.HubSite;
import net.tfminecraft.simplefactions.guild.hub.HighwaySnapshot;
import net.tfminecraft.simplefactions.guild.hub.HubNetwork;
import net.tfminecraft.simplefactions.guild.network.InstallationAccess;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
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
import net.tfminecraft.simplefactions.objects.handler.TaxHandler;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;

public class ProvinceManager {
    private Map<Integer, Province> provinces = new HashMap<>();
    /** Set only on a preview copy, so a background flood does not walk the live faction list. */
    private List<Guild> previewGuilds;
    private List<Faction> previewFactions;
    /** Set on a copy so a preview keeps the graph and hubs it was given, off the server thread. */
    private HighwaySnapshot highwayCapture;
    private Map<Guild, Map<String, Double>> installationAccess = new HashMap<>();
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

    /** {@link #get} never returns null, so use this to check an id against provinces.txt. */
    public boolean contains(int id) {
        return provinces.containsKey(id);
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
        snap.highwayCapture = highwayCapture != null ? highwayCapture : HighwaySnapshot.current();
        return snap;
    }

    /**
     * Snapshot-only graph. The hub map remains in the signature for callers that still build hub
     * previews, but hubs do not affect trade or production. Null clears the capture.
     */
    public void setHighwayOverride(TradeGraph graph, Map<String, Set<HubSite>> hubbedByGuild) {
        if (graph == null && hubbedByGuild == null) {
            highwayCapture = null;
            return;
        }
        highwayCapture = HighwaySnapshot.of(
                graph == null ? TradeGraph.live() : graph,
                hubbedByGuild == null ? Map.of() : hubbedByGuild);
    }

    private HighwaySnapshot highway() {
        return highwayCapture != null ? highwayCapture : HighwaySnapshot.current();
    }

    private TradeGraph graphFor() {
        HighwaySnapshot snapshot = highway();
        return snapshot == null || snapshot.graph() == null ? TradeGraph.live() : snapshot.graph();
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
        InstallationAccess.beginRecalculation();
        try {
            installationAccess = new HashMap<>();
            HubNetwork.refreshIfLive(this);
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
        } finally {
            InstallationAccess.endRecalculation();
        }
    }

    /**
     * Same flood as {@link #recalculate()} without writing guild trade breakdowns.
     * {@code guilds} and {@code factions} are copies taken on the server thread.
     */
    public void recalculateQuiet(List<Guild> guilds, List<Faction> factions) {
        if (!Cache.provincesEnabled) {
            return;
        }
        previewGuilds = guilds == null ? List.of() : List.copyOf(guilds);
        previewFactions = factions == null ? List.of() : List.copyOf(factions);
        InstallationAccess.beginRecalculation();
        try {
            installationAccess = new HashMap<>();
            HubNetwork.refreshIfLive(this);
            dropExcept(previewGuilds);
            for (Guild guild : previewGuilds) {
                if (guild != null && guild.hasCapital()) {
                    recalculateGuild(guild);
                }
            }
            for (Guild guild : previewGuilds) {
                if (guild != null && guild.hasCapital()) {
                    recalculateProduction(guild);
                }
            }
            recalculateProsperity();
        } finally {
            InstallationAccess.endRecalculation();
        }
    }

    /** Drops the copies {@link #recalculateQuiet} kept for the income read that follows. */
    public void clearPreviewLists() {
        previewGuilds = null;
        previewFactions = null;
    }

    public void recalculateForSingleGuild(Guild g, boolean save) {
        if (!Cache.provincesEnabled) {
            return;
        }
        InstallationAccess.beginRecalculation();
        try {
            installationAccess = new HashMap<>();
            if (!g.hasCapital()) return;
            HubNetwork.refreshIfLive(this);
            dropMissingGuilds();
            recalculateGuild(g);
            recalculateProduction(g);
            if(save) {
                for(Guild guild : FactionManager.getAllGuilds()) {
                    getIncome(guild);
                }
            }
            recalculateProsperity();
        } finally {
            InstallationAccess.endRecalculation();
        }
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
        carryProductionThroughGraph(guild);
    }

    private void recalculateGuild(Guild guild) {
        // 1) Clear only this guild’s data
        clearGuildData(guild.getId());

        // 2) Recalculate trade graph
        Province capital = provinces.get(guild.getCapital());
        if (capital != null) {
            capital.calculateTrade(this, guild, -1, 0);
        }
        carryTradeThroughGraph(guild);
    }

    /** Trade walks the shared graph after the capital. Passes repeat until nothing larger arrives. */
    private void carryTradeThroughGraph(Guild guild) {
        // Prosperity weighs production by a province's distance from where it came. Trade arriving
        // along the graph must not shorten that for production that still walked from the capital,
        // so the walked distances are put back afterwards. Production delivered by the graph sets its own.
        Map<Integer, Integer> walked = new HashMap<>();
        for (Province province : provinces.values()) {
            ProvinceDataEntry entry = province.getAllData().get(guild.getId());
            if (entry != null) walked.put(province.getId(), entry.getDistance());
        }
        Highway.deliver(this, guild, graphFor(), accessFor(guild));
        depositCorridors(guild);
        for (Map.Entry<Integer, Integer> distance : walked.entrySet()) {
            ProvinceDataEntry entry = provinces.get(distance.getKey()).getAllData().get(guild.getId());
            if (entry != null) entry.setDistance(distance.getValue());
        }
    }

    /** Sea and rail corridor deposits. Runs only after the highway has settled, and does not run it again. */
    private void depositCorridors(Guild guild) {
        if (guild == null) {
            return;
        }
        Highway.deposit(this, guild, graphFor(), accessFor(guild));
    }

    private void carryProductionThroughGraph(Guild guild) {
        Highway.deliverProduction(this, guild, graphFor(), accessFor(guild));
    }

    private Map<String, Double> accessFor(Guild guild) {
        return installationAccess.computeIfAbsent(guild, ignored -> {
            Map<String, Double> access = new HashMap<>();
            for (TradeGraph.Node node : graphFor().nodes()) {
                if (node.ownerFactionId() == null) continue;
                String ownerId = node.ownerFactionId().toLowerCase(Locale.ROOT);
                access.computeIfAbsent(ownerId, id -> {
                    Faction owner = factionById(id, guild.getFaction());
                    return owner == null ? 0 : InstallationAccess.of(guild.getFaction(), owner);
                });
            }
            return Map.copyOf(access);
        });
    }

    private Faction factionById(String id, Faction guildFaction) {
        if (guildFaction != null && guildFaction.getId() != null
                && guildFaction.getId().equalsIgnoreCase(id)) {
            return guildFaction;
        }
        if (previewFactions != null) {
            for (Faction faction : previewFactions) {
                if (faction != null && faction.getId() != null && faction.getId().equalsIgnoreCase(id)) {
                    return faction;
                }
            }
            return null;
        }
        return FactionManager.getByString(id);
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
        double upkeepFactor = GuildModifierOverride.resolve(guild, GuildModifier.TRADE_UPKEEP);
        double tariffs = 0;

        for (Province province : provinces.values()) {
            if(!province.getTerrain().generatesIncome()) continue;
            double provinceIncome = province.getIncome(guild);
            if(provinceIncome == 0) continue;
            //if(provinceIncome > guildTrade) provinceIncome = guildTrade;
            upkeep += provinceIncome*TradeUpkeep.rate(province.getTradeFactor(guild), upkeepFactor);
            Faction owner = previewFactions != null
                    ? ownerOf(province.getId())
                    : TitleManager.getByProvince(province.getId());
            if(owner != null) {
                if(save) guild.getTradeBreakdown().registerIncome(owner, provinceIncome);
                if(!RelationManager.sameRealm(owner, guild.getFaction())){
                    double provinceTariffs = provinceIncome*owner.getTaxRate(TaxTarget.TARIFFS, guild.getFaction().getId(), true)/100.0;
                    tariffs+=provinceTariffs;
                    if(save && provinceTariffs > 0) {
                        guild.getTradeBreakdown().registerTariffs(owner, provinceTariffs);
                    }
                }
            }
            income += provinceIncome;
        }
        if(save) {
            guild.getTradeBreakdown().setTariffs(tariffs);
            guild.getTradeBreakdown().setUpkeep(upkeep);
            income = PillageTradeHit.applyToIncome(guild, income);
            guild.getTradeBreakdown().setIncome(income);
            guild.getTradeBreakdown().setTradePower(getTotalTrade(guild));
        }
        income-=upkeep;

        // Optional rounding for display
        return Math.round(income * 100.0) / 100.0;
    }

    /** The gross trade line, before upkeep and tariffs, without writing a guild breakdown. */
    public double getGrossTradeIncome(Guild guild) {
        if (!Cache.provincesEnabled) {
            return 0;
        }
        double income = 0;
        for (Province province : provinces.values()) {
            if (province.getTerrain().generatesIncome()) {
                income += province.getIncome(guild);
            }
        }
        return PillageTradeHit.applyToIncome(guild, income);
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
    private void dropExcept(List<Guild> present) {
        Set<String> ids = new HashSet<>();
        for (Guild guild : present) {
            if (guild != null && guild.getId() != null) {
                ids.add(guild.getId().toLowerCase(Locale.ROOT));
            }
        }
        for (Province province : provinces.values()) {
            List<String> remove = new ArrayList<>();
            for (String id : province.getAllData().keySet()) {
                if (id == null || !ids.contains(id.toLowerCase(Locale.ROOT))) {
                    remove.add(id);
                }
            }
            for (String id : remove) {
                province.clearGuildData(id);
            }
        }
    }

    private Faction ownerOf(int provinceId) {
        for (Faction faction : previewFactions) {
            if (faction != null && faction.hasProvince(provinceId)) {
                return faction;
            }
        }
        return null;
    }

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
     * @param targetId The faction a specific tariff applies to, or null for the base rate
     * @param newTariffRate The new tariff rate (0-100)
     * @return Map of guilds to their tariff impact (negative = lose income, positive = gain income)
     */
    public Map<Guild, Double> previewTariffRateChange(Faction faction, String targetId, double newTariffRate) {
        Map<Guild, Double> impacts = new HashMap<>();
        TaxHandler taxHandler = faction.getTaxHandler();
        
        // Initialize all guilds with 0 impact
        for (Guild guild : FactionManager.getAllGuilds()) {
            impacts.put(guild, 0.0);
        }
        
        // Loop through all provinces
        for (Province province : provinces.values()) {
            Faction owner = TitleManager.getByProvince(province.getId());
            // Only care about provinces owned by the faction changing tariffs
            if (owner == null || !owner.equals(faction)) continue;
            
            // For each guild that might trade in this province
            for (Guild guild : FactionManager.getAllGuilds()) {
                // Skip guilds in same realm (no tariffs within realm)
                if (RelationManager.sameRealm(faction, guild.getFaction())) continue;
                String guildFactionId = guild.getFaction().getId();
                // A specific tariff only hits its target; a base change skips factions with their own rate
                if (targetId != null ? !targetId.equalsIgnoreCase(guildFactionId)
                        : taxHandler.hasSpecificTax(TaxTarget.TARIFFS, guildFactionId)) continue;
                
                double provinceIncome = province.getIncome(guild);
                if (provinceIncome == 0) continue;
                
                // Calculate tariff impact delta
                double oldTariffRate = taxHandler.getTaxRate(TaxTarget.TARIFFS, guildFactionId, false);
                double oldTariff = provinceIncome * (oldTariffRate / 100.0);
                double newTariff = provinceIncome * (newTariffRate / 100.0);
                double tariffDelta = -(newTariff - oldTariff); // Negative because it reduces guild income
                
                impacts.put(guild, impacts.get(guild) + tariffDelta);
            }
        }
        
        return impacts;
    }
}
