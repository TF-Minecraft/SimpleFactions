package net.tfminecraft.simplefactions.economy.settlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.simplefactions.database.LoanData;
import net.tfminecraft.simplefactions.database.MercenaryCompanyData;
import net.tfminecraft.simplefactions.war.resolution.WarReparationsObligation;

/** Exact financial after-values stored with the immutable posting. */
public final class RealmSettlementChanges {
    public final List<FactionUpkeepPlan> upkeep = new ArrayList<>();
    public final List<GuildChange> guilds = new ArrayList<>();
    public final Map<String, List<WarReparationsObligation>> reparations = new LinkedHashMap<>();
    public final Map<String, Long> deathPayments = new LinkedHashMap<>();
    public final Map<UUID, Map<String, Double>> playerIncome = new LinkedHashMap<>();
    public com.google.gson.JsonObject vehicleChanges = new com.google.gson.JsonObject();

    public static final class GuildChange {
        public String id;
        public UUID incarnation;
        public double donation;
        public Map<String, Double> citizenTaxes;
        public double casinoProfit;
        public double vehicleFeeIncome;
        public int creditScore;
        public List<LoanData> loans = new ArrayList<>();
        public MercenaryCompanyData company;
        public Map<Integer, Integer> branches = new LinkedHashMap<>();
        public List<String> dividendEligible;
        public Map<String, Map<String, Double>> ledgerLastDay;
        public Map<String, Map<String, Double>> ledgerLifetime;
    }
}
