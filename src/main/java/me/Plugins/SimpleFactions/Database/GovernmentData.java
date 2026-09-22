package me.Plugins.SimpleFactions.Database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class GovernmentData {
    public Double power;
    
    @SerializedName("last election date")
    public Long lastElectionDate;
    
    @SerializedName("council members")
    public List<String> councilMembers = new ArrayList<>();
    
    public List<String> proposals = new ArrayList<>();

    @SerializedName("eligible voters")
    public List<String> eligibleVoters = new ArrayList<>();
    
    @SerializedName("election candidates")
    public Map<String, List<String>> electionCandidates = new HashMap<>();
    
    @SerializedName("election votes")
    public Map<String, Map<String, String>> electionVotes = new HashMap<>();

    @SerializedName("previous votes")
    public Map<String, Map<String, Integer>> previousVotes = new HashMap<>();

    @SerializedName("stability modifiers")
    public List<StabilityModifierData> stabilityModifiers = new ArrayList<>();

    public List<MovementData> movements = new ArrayList<>();

    @SerializedName("voting booths")
    public List<String> votingBooths = new ArrayList<>();
}
