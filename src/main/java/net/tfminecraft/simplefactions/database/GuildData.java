package net.tfminecraft.simplefactions.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class GuildData {
    public String id;
    public String name;
    public String leader;

    /** The leader's roleplay character name and the player it was read from. */
    @com.google.gson.annotations.SerializedName("leader character")
    public String leaderCharacter;

    @com.google.gson.annotations.SerializedName("leader character of")
    public String leaderCharacterOf;
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
    /** Accepted loan agreement ids and when their books expire. */
    public Map<String, Long> usedLoanOffers;

    public Boolean favoured = false;
    public Boolean repressed = false;

    public List<StabilityModifierData> pillageHits = new ArrayList<>();

    public Double dividendPercent;
    public List<String> dividendEligible = new ArrayList<>();

    @SerializedName("casino profit")
    public Double casinoProfit;
    public Double vehicleFeeIncome;

    @SerializedName("citizen taxes")
    public Map<String, Double> citizenTaxes;

    @SerializedName("ledger last day")
    public Map<String, Map<String, Double>> ledgerLastDay;
    @SerializedName("ledger lifetime")
    public Map<String, Map<String, Double>> ledgerLifetime;
    @SerializedName("deposits today")
    public Map<String, Double> depositsToday;

    public MercenaryCompanyData company;

    @SerializedName("supply hubs")
    public List<SupplyHubData> supplyHubs = new ArrayList<>();

    @SerializedName("hub agreements")
    public List<HubAgreementData> hubAgreements = new ArrayList<>();

    @SerializedName("hub offers")
    public List<HubOfferData> hubOffers = new ArrayList<>();

    @SerializedName("supply hub tutorial dismissals")
    public List<String> supplyHubTutorialDismissals = new ArrayList<>();
}
