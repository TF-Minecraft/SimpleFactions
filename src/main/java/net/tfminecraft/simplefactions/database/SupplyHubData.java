package net.tfminecraft.simplefactions.database;

import com.google.gson.annotations.SerializedName;

public class SupplyHubData {
    public String faction;
    public String installation;

    @SerializedName("created at")
    public Long createdAt;
}
