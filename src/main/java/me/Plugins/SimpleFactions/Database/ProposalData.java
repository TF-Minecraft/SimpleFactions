package me.Plugins.SimpleFactions.Database;

import com.google.gson.annotations.SerializedName;

public class ProposalData {
    public String proposer;
    public String type; // "law", "tax", or "political"
    
    // For law proposals
    public String groupId;
    public String lawId;
    
    // For tax proposals
    @SerializedName("tax target")
    public String taxTarget;
    
    @SerializedName("tax id")
    public String taxId;
    
    @SerializedName("new tax")
    public Double newTax;
    
    // For political action proposals
    @SerializedName("action key")
    public String actionKey;
    
    public String target;
}
