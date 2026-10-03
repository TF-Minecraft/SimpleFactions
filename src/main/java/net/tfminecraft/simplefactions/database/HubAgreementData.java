package net.tfminecraft.simplefactions.database;

import com.google.gson.annotations.SerializedName;

public class HubAgreementData {
    public String faction;
    public String installation;
    public Integer rate;

    /** Denars, whole cents. */
    @SerializedName("daily fee")
    public Double dailyFee;

    @SerializedName("days remaining")
    public Integer daysRemaining;

    @SerializedName("guild renews")
    public Boolean guildRenews;

    @SerializedName("host renews")
    public Boolean hostRenews;

    @SerializedName("next rate")
    public Integer nextRate;

    @SerializedName("next fee")
    public Double nextFee;

    /** Set once the guild leader has been told that a dormant hub still costs its fee. */
    @SerializedName("dormant notice")
    public Boolean dormantNotice;
}
