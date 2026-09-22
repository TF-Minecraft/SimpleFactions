package me.Plugins.SimpleFactions.Database;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MercenaryCompanyData {
    public String name;
    public List<String> banner = new ArrayList<>();
    public Integer formationRemaining;
    public Integer reputation;
    /** regimentId + "." + currentSlots, matching the faction military format. */
    public String slots;
    /** regimentId + "." + timeLeft per queued slot. */
    public List<String> slotQueue = new ArrayList<>();
    public List<String> enlisted = new ArrayList<>();
    public List<GuildBranchData> upgrades = new ArrayList<>();
    public List<UpgradeExpansionData> upgradeQueue = new ArrayList<>();
    /** Contracts live on the company, the way loans live on the issuing guild. */
    public List<MercenaryContractData> contracts = new ArrayList<>();
    public Double activeWagePercent;
    public Double peacetimeWagePerDay;
    public Map<String, Double> activeWageOverrides = new LinkedHashMap<>();
    public Map<String, Double> peacetimeWageOverrides = new LinkedHashMap<>();
    /** Wages accrued but not yet settled, keyed by player name. */
    public Map<String, Double> pendingWages = new LinkedHashMap<>();
}
