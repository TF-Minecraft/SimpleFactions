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
import net.tfminecraft.simplefactions.guild.income.TradeBreakdown;
import net.tfminecraft.simplefactions.guild.income.TradeIncome;
import net.tfminecraft.simplefactions.guild.income.TradeInputs;
import net.tfminecraft.simplefactions.guild.income.TradeUpkeep;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.guild.hub.Highway;
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
import net.tfminecraft.simplefactions.economy.FrozenReads;
import net.tfminecraft.simplefactions.war.resolution.PillageTradeHit;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.government.stability.BankruptcyMemo;
import net.tfminecraft.simplefactions.government.stability.StabilityMemo;
import net.tfminecraft.simplefactions.objects.handler.TaxHandler;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;

public class ProvinceManager {
    private Map<Integer, Province> provinces = new HashMap<>();
    /** Set only on a preview copy, so a background flood does not walk the live faction list. */
    private List<Guild> previewGuilds;
    private List<Faction> previewFactions;
    /** Set on a copy so a preview keeps the graph it was given, off the server thread. */
    private TradeGraph highwayCapture;
    private Map<Guild, Map<String, Double>> installationAccess = new HashMap<>();
    private long stateVersion = 0;
    private long lastCalculatedVersion = -1;
    private Map<Guild, Boolean> lastCalculatedBankruptcy = Map.of();
    /** Bumped whenever live province data is rewritten, so a flood computed on a copy can tell it is stale. */
    private long dataGeneration = 0;

    public void markDirty() {
        stateVersion++;
    }

    public void recalculateIfNeeded() {
        if (!Cache.provincesEnabled) return;
        // Realm treasury sign changes stability even when no map mutation marks us dirty.
        if (stateVersion == lastCalculatedVersion && realmBankruptcy().equals(lastCalculatedBankruptcy)) return;
        recalculate();
    }

    private Map<Guild, Boolean> realmBankruptcy() {
        Map<Guild, Boolean> bankruptcy = new HashMap<>();
        for (Guild guild : FactionManager.getAllGuilds()) {
            if (guild.isBase()) bankruptcy.put(guild, guild.isBankrupt());
        }
        return bankruptcy;
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
        dataGeneration++;
        provinces = map;
    }

    public ProvinceManager createSnapshotShell() {
        ProvinceManager snap = new ProvinceManager();
        Map<Integer, Province> map = new HashMap<>();

        for (Province p : provinces.values()) {
            map.put(p.getId(), p.cloneShell());
        }

        snap.start(map);
        snap.highwayCapture = highwayCapture != null ? highwayCapture : TradeGraph.live();
        return snap;
    }

    /** Snapshot-only graph. Null clears the capture so later reads use the live graph. */
    public void setHighwayOverride(TradeGraph graph) {
        highwayCapture = graph;
    }

    private TradeGraph graphFor() {
        return highwayCapture != null ? highwayCapture : TradeGraph.live();
    }

    public void clearGuildData(String guildId) {
        if (guildId == null) {
            return;
        }
        dataGeneration++;
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
        // A partial or failed flood must not leave the old full snapshot marked fresh.
        lastCalculatedVersion = -1;
        dataGeneration++;
        InstallationAccess.beginRecalculation();
        BankruptcyMemo.Run memo = BankruptcyMemo.begin();
        StabilityMemo.Run stability = StabilityMemo.begin();
        RecalcLookups.Run lookups = RecalcLookups.begin();
        try {
            installationAccess = new HashMap<>();
            TradeGraph.refreshIfLive(this);
            dropMissingGuilds();
            for(Guild g : FactionManager.getAllGuilds()) {
                recalculateGuild(g);
            }
            for(Guild g : FactionManager.getAllGuilds()) {
                if (!g.hasCapital()) continue;
                recalculateProduction(g);
            }
            recalculateProsperity();
            for(Guild guild : FactionManager.getAllGuilds()) getIncome(guild);
            lastCalculatedBankruptcy = realmBankruptcy();
            lastCalculatedVersion = stateVersion;
        } finally {
            RecalcLookups.end(lookups);
            StabilityMemo.end(stability);
            BankruptcyMemo.end(memo);
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
        BankruptcyMemo.Run memo = BankruptcyMemo.begin();
        StabilityMemo.Run stability = StabilityMemo.begin();
        RecalcLookups.Run lookups = RecalcLookups.begin();
        try {
            installationAccess = new HashMap<>();
            TradeGraph.refreshIfLive(this);
            dropExcept(previewGuilds);
            for (Guild guild : previewGuilds) {
                if (guild != null) {
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
            RecalcLookups.end(lookups);
            StabilityMemo.end(stability);
            BankruptcyMemo.end(memo);
            InstallationAccess.endRecalculation();
        }
    }

    /**
     * A full flood on a copy, for running off the server thread. Create it on the server thread,
     * {@link BackgroundFlood#run() run} it anywhere, then {@link #adopt} it on the server thread.
     */
    public static final class BackgroundFlood {
        private final ProvinceManager copy;
        private final List<Guild> guilds;
        private final List<Faction> factions;
        private final FrozenReads reads = FrozenReads.capture();
        private final Map<Guild, Boolean> bankruptcy;
        private final long generation;
        private final long version;
        private final long inputs = TradeInputs.fingerprint();
        private final Map<Guild, TradeBreakdown> breakdowns = new HashMap<>();
        private volatile boolean done;
        private volatile boolean cancelled;

        private BackgroundFlood(ProvinceManager live) {
            copy = live.createSnapshotShell();
            copy.copyAllDataFrom(live);
            guilds = List.copyOf(FactionManager.getAllGuilds());
            factions = FactionManager.getCopy();
            bankruptcy = live.realmBankruptcy();
            generation = live.dataGeneration;
            version = live.stateVersion;
        }

        /** Stops a run that is no longer wanted at the next guild; it then never adopts. */
        public void cancel() {
            cancelled = true;
        }

        public void run() {
            if (cancelled) {
                return;
            }
            reads.read(() -> {
                copy.recalculateQuiet(guilds, factions);
                try {
                    for (Guild guild : guilds) {
                        if (cancelled) {
                            return null;
                        }
                        TradeBreakdown breakdown = new TradeBreakdown();
                        copy.fillBreakdown(guild, breakdown);
                        breakdowns.put(guild, breakdown);
                    }
                } finally {
                    copy.clearPreviewLists();
                }
                return null;
            });
            done = !cancelled;
        }
    }

    /** Server thread. Refreshes the live trade graph so the copy floods over current track. */
    public BackgroundFlood prepareBackgroundFlood() {
        TradeGraph.refreshIfLive(this);
        return new BackgroundFlood(this);
    }

    /**
     * Server thread. Installs a finished flood unless live province data, tariffs, guild tax or
     * realms changed since it was prepared; then the caller should flood again or rely on the newer data.
     */
    public boolean adopt(BackgroundFlood flood) {
        if (!Cache.provincesEnabled || flood == null || !flood.done
                || flood.generation != dataGeneration || flood.version != stateVersion
                || flood.inputs != TradeInputs.fingerprint()) {
            return false;
        }
        copyAllDataFrom(flood.copy);
        for (Map.Entry<Guild, TradeBreakdown> entry : flood.breakdowns.entrySet()) {
            entry.getKey().getTradeBreakdown().copyFrom(entry.getValue());
        }
        dataGeneration++;
        lastCalculatedBankruptcy = flood.bankruptcy;
        lastCalculatedVersion = flood.version;
        return true;
    }

    /** What {@link #getIncome(Guild, boolean)} writes, into a breakdown that is not the guild's. */
    private void fillBreakdown(Guild guild, TradeBreakdown breakdown) {
        if (!Cache.provincesEnabled) {
            return;
        }
        TradeIncome trade = measureIncome(guild, breakdown);
        breakdown.setTariffs(trade.tariffs());
        breakdown.setUpkeep(trade.upkeep());
        breakdown.setIncome(trade.gross());
        breakdown.setTradePower(getTotalTrade(guild));
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
        // A partial or failed flood must not leave the old full snapshot marked fresh.
        lastCalculatedVersion = -1;
        dataGeneration++;
        InstallationAccess.beginRecalculation();
        BankruptcyMemo.Run memo = BankruptcyMemo.begin();
        StabilityMemo.Run stability = StabilityMemo.begin();
        RecalcLookups.Run lookups = RecalcLookups.begin();
        try {
            installationAccess = new HashMap<>();
            TradeGraph.refreshIfLive(this);
            dropMissingGuilds();
            recalculateGuild(g);
            recalculateProduction(g);
            recalculateProsperity();
            if(save) {
                for(Guild guild : FactionManager.getAllGuilds()) {
                    getIncome(guild);
                }
            }
        } finally {
            RecalcLookups.end(lookups);
            StabilityMemo.end(stability);
            BankruptcyMemo.end(memo);
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
        // Clear former trade and production even when the guild has lost its capital.
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
        for (Map.Entry<Integer, Integer> distance : walked.entrySet()) {
            ProvinceDataEntry entry = provinces.get(distance.getKey()).getAllData().get(guild.getId());
            if (entry != null) entry.setDistance(distance.getValue());
        }
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
        TradeIncome trade = measureIncome(guild, save ? guild.getTradeBreakdown() : null);
        if(save) {
            guild.getTradeBreakdown().setTariffs(trade.tariffs());
            guild.getTradeBreakdown().setUpkeep(trade.upkeep());
            guild.getTradeBreakdown().setIncome(trade.gross());
            guild.getTradeBreakdown().setTradePower(getTotalTrade(guild));
        }
        double income = trade.gross() - trade.upkeep();

        // Optional rounding for display
        return Math.round(income * 100.0) / 100.0;
    }

    /**
     * The guild's gross trade, trade upkeep, and tariffs on these provinces, without writing its
     * trade breakdown. Settlement taxes the gross line and deducts upkeep and tariffs from it.
     */
    public TradeIncome getTradeIncome(Guild guild) {
        if (!Cache.provincesEnabled) {
            return TradeIncome.NONE;
        }
        return measureIncome(guild, null);
    }

    /** One pass over the provinces. A non-null breakdown also receives the per-owner lines. */
    private TradeIncome measureIncome(Guild guild, TradeBreakdown breakdown) {
        double income = 0;
        double upkeep = 0;
        double upkeepFactor = GuildModifierOverride.resolve(guild, GuildModifier.TRADE_UPKEEP);
        double tariffs = 0;
        Map<String, Double> tariffsByOwner = new HashMap<>();

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
                if(breakdown != null) breakdown.registerIncome(owner, provinceIncome);
                if(!RelationManager.sameRealm(owner, guild.getFaction())){
                    double provinceTariffs = provinceIncome*owner.getTaxRate(TaxTarget.TARIFFS, guild.getFaction().getId(), true)/100.0;
                    tariffs+=provinceTariffs;
                    if(provinceTariffs > 0) {
                        tariffsByOwner.merge(java.util.Objects.toString(owner.getId(), ""), provinceTariffs, Double::sum);
                        if(breakdown != null) breakdown.registerTariffs(owner, provinceTariffs);
                    }
                }
            }
            income += provinceIncome;
        }
        income = PillageTradeHit.applyToIncome(guild, income);
        return new TradeIncome(income, upkeep, tariffs, tariffsByOwner);
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
