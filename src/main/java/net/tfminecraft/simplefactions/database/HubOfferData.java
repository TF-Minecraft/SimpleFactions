package net.tfminecraft.simplefactions.database;

import com.google.gson.annotations.SerializedName;

public class HubOfferData {
    public String faction;
    public String installation;
    public Integer rate;

    @SerializedName("daily fee")
    public Double dailyFee;

    /** {@code guild} or {@code host}. */
    public String awaiting;

    @SerializedName("last actor")
    public String lastActor;

    @SerializedName("created at")
    public Long createdAt;

    /** {@code new} or {@code next}. */
    public String kind;
}
