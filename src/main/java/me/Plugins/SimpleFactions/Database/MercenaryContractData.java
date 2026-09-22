package me.Plugins.SimpleFactions.Database;

public class MercenaryContractData {
    public String id;
    /** Hiring faction id. The company is the owner of the list this sits in. */
    public String hirer;
    public String kind;
    public Integer slots;
    public Double pricePerSlotPerBattle;
    public Double pricePerSlotPerDay;
    public Integer durationDays;
    public Double absenceRefundPerSlotPerBattle;
    public Double breachRefund;
    public Long issueDate;
    public Long dueDate;
    public Integer reputationAtSigning;
    public String status;
    public Integer daysServed;
    public Boolean attendanceClean;
    public Double accruedToCompany;
    public Double accruedToHirer;
    public java.util.List<String> battleIdsCharged = new java.util.ArrayList<>();
    public java.util.List<String> battleIdsRefunded = new java.util.ArrayList<>();
}
