package net.tfminecraft.simplefactions.database;

import net.tfminecraft.simplefactions.espionage.EspionageState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.google.gson.annotations.SerializedName;

public class FactionData {
    public EspionageState espionage;
    public String id;
    public String name;
    public String rgb;
    public String leader;

    /**
     * The leader's roleplay character name, for the web map, and the player
     * it was read from. See {@code LeaderCharacters}.
     */
    @SerializedName("leader character")
    public String leaderCharacter;

    @SerializedName("leader character of")
    public String leaderCharacterOf;

    @SerializedName("ruler title")
    public String rulerTitle;

    public String government;
    public String culture;
    public String religion;

    @SerializedName("citizen tax")
    public Double citizenTax;

    @SerializedName("guild tax")
    public Double guildTax;

    @SerializedName("vassal tax")
    public Double vassalTax;

    @SerializedName("dividend tax")
    public Double dividendTax;

    public Double tariffs;

    @SerializedName("specific taxes")
    public HashMap<String, HashMap<String, Double>> specificTaxes = new HashMap<>();

    @SerializedName("vehicle fees")
    public HashMap<String, Double> vehicleFees;

    @SerializedName("vehicle type fees")
    public HashMap<String, HashMap<String, Double>> vehicleTypeFees;

    public Integer capital;

    @SerializedName("extra node capacity")
    public Double extraNodeCapacity;

    public List<String> banner = new ArrayList<>();
    public List<Number> provinces = new ArrayList<>();
    public List<String> titles = new ArrayList<>();
    public List<String> relations = new ArrayList<>();
    @SerializedName("trade relation")
    public List<String> tradeRelations = new ArrayList<>();
    @SerializedName("treaty relation")
    public List<String> treatyRelations = new ArrayList<>();

    @SerializedName("tier index")
    public Double tierIndex;

    public List<String> military = new ArrayList<>();

    @SerializedName("military queue")
    public List<String> militaryQueue = new ArrayList<>();

    @SerializedName("prestige modifiers")
    public List<String> prestigeModifiers = new ArrayList<>();

    public String rank;

    @SerializedName("founded at")
    public Long foundedAt;

    @SerializedName("capital moves")
    public Integer capitalMoves;

    public String overlord;

    public List<GuildData> guilds = new ArrayList<>();

    public List<SettlementData> settlements = new ArrayList<>();

    public List<InstallationData> installations = new ArrayList<>();

    @SerializedName("installation queue")
    public InstallationConstructionData installationQueue;

    public GovernmentData governmentData;

    @SerializedName("faction modifiers")
    public List<String> factionModifiers = new ArrayList<>();

    public List<String> laws = new ArrayList<>();

    @SerializedName("law changed at")
    public Map<String, Long> lawChangedAt = new HashMap<>();

    @SerializedName("war reparations")
    public List<WarReparationsObligationData> warReparationsObligations = new ArrayList<>();
}
