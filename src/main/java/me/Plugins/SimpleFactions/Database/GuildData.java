package me.Plugins.SimpleFactions.Database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class GuildData {
    public String id;
    public String name;
    public String leader;
    public String rgb;
    public String type;
    public Integer capital;

    public String bank;
    public String world;

    public String stance;

    @SerializedName("xPos")
    public Double xPos;

    @SerializedName("zPos")
    public Double zPos;

    public Double balance;
    
    public List<String> banner = new ArrayList<>();

    public List<String> members = new ArrayList<>();
    public List<GuildBranchData> branches = new ArrayList<>();
    public List<GuildBranchData> upgrades = new ArrayList<>();
    
    @SerializedName("upgrade queue")
    public List<UpgradeExpansionData> upgradeQueue = new ArrayList<>();

    @SerializedName("wealth modifiers")
    public List<String> wealthModifiers = new ArrayList<>();

    public List<LoanData> loans = new ArrayList<>();
    public Integer creditScore;

    public Boolean favoured = false;
    public Boolean repressed = false;

    public List<StabilityModifierData> pillageHits = new ArrayList<>();

    public Double dividendPercent;
    public List<String> dividendEligible = new ArrayList<>();

    @SerializedName("casino profit")
    public Double casinoProfit;

    @SerializedName("citizen taxes")
    public Map<String, Double> citizenTaxes;

    public MercenaryCompanyData company;
}
