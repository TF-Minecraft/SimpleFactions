package net.tfminecraft.simplefactions.database;

import com.google.gson.annotations.SerializedName;

public class ProposalData {
    public String proposer;
    public String type; // "law", "tax", "fee", or "political"
    
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
    
    // For fee proposals
    @SerializedName("fee kind")
    public String feeKind;

    /** Null for the general rate. */
    @SerializedName("fee vehicle")
    public String feeVehicle;

    @SerializedName("new fee")
    public Double newFee;

    // For political action proposals
    @SerializedName("action key")
    public String actionKey;
    
    public String target;
}
