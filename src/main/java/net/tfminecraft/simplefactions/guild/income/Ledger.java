package net.tfminecraft.simplefactions.guild.income;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.entry.PlayerEntry;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.FactionModifier;
import net.tfminecraft.simplefactions.utils.DailyGuildTransfers;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.utils.PostSettlementPayouts.PlayerUuidLookup;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.war.resolution.WarReparationsObligation;
import net.tfminecraft.simplefactions.war.resolution.WarReparationsService;

public class Ledger {
    private Guild guild;

    private final Map<String, Double> citizenTaxes = new HashMap<>();

    private final Map<String, Double> loanPayments = new HashMap<>();
    private final Map<String, Double> interestPayments = new HashMap<>();

    // Pushed by the company side, because a hiring capital owns no contract object.
    private final Map<String, Double> mercenaryPayments = new HashMap<>();
    private final Map<String, Double> refunds = new HashMap<>();

    // Pushed by the games plugin as tables win, and saved with the bank balance it arrived in,
    // so a restart cannot quietly wipe a day of gambling income before it is taxed.
    private double casinoProfit;

    // Dowsing registers this as the daily upkeep of the guild's active nodes; unset means no
    // nodes plugin, so the line is 0. Settlement charges exactly what this reports.
    private static ToDoubleFunction<Guild> nodeUpkeepLookup = guild -> 0.0;

    public static void setNodeUpkeepLookup(ToDoubleFunction<Guild> lookup) {
        nodeUpkeepLookup = lookup == null ? guild -> 0.0 : lookup;
    }

    public Ledger(Guild guild) {
        this.guild = guild;
    }

    public void addCitizenTaxEntry(String p, Double tax) {
        if(p == null || p.isBlank() || tax == null || tax <= 0) return;
        if(citizenTaxes.containsKey(p)) {
            citizenTaxes.put(p, citizenTaxes.get(p)+tax);
        } else {
            citizenTaxes.put(p, tax);
        }
    }

    /** Copy for save. The live map stays on this ledger. */
    public Map<String, Double> getCitizenTaxesCopy() {
        return new HashMap<>(citizenTaxes);
    }

    /** Seeded from disk at load, so an unsettled day survives a restart. */
    public void setCitizenTaxes(Map<String, Double> taxes) {
        citizenTaxes.clear();
        if (taxes == null || taxes.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> entry : taxes.entrySet()) {
            String name = entry.getKey();
            Double tax = entry.getValue();
            if (name == null || name.isBlank() || tax == null || tax <= 0) {
                continue;
            }
            citizenTaxes.merge(name, tax, Double::sum);
        }
    }

    public void addLoanPaymentEntry(String payerGuildId, Double amount) {
        if(loanPayments.containsKey(payerGuildId)) {
            loanPayments.put(payerGuildId, loanPayments.get(payerGuildId)+amount);
        } else {
            loanPayments.put(payerGuildId, amount);
        }
    }

    public void addInterestPaymentEntry(String payerGuildId, Double amount) {
        if(interestPayments.containsKey(payerGuildId)) {
            interestPayments.put(payerGuildId, interestPayments.get(payerGuildId)+amount);
        } else {
            interestPayments.put(payerGuildId, amount);
        }
    }

    public void addMercenaryPaymentEntry(String hostGuildId, Double amount) {
        if(mercenaryPayments.containsKey(hostGuildId)) {
            mercenaryPayments.put(hostGuildId, mercenaryPayments.get(hostGuildId)+amount);
        } else {
            mercenaryPayments.put(hostGuildId, amount);
        }
    }

    public void addRefundEntry(String hostGuildId, Double amount) {
        if(refunds.containsKey(hostGuildId)) {
            refunds.put(hostGuildId, refunds.get(hostGuildId)+amount);
        } else {
            refunds.put(hostGuildId, amount);
        }
    }

    /**
     * What a guild's tables won beyond the float they were stocked with. The denars are already in
     * the bank when this is called, so this only widens what the guild owes tax on.
     */
    public void addCasinoProfitEntry(Double amount) {
        if(amount == null || amount <= 0) return;
        casinoProfit += amount;
    }

    public double getCasinoProfit() {
        return casinoProfit;
    }

    /** Seeded from disk at load, so an unsettled day survives a restart. */
    public void setCasinoProfit(double amount) {
        casinoProfit = Math.max(0, amount);
    }

    public double getIncome(Cashflow cashflow) {
        double amount = 0;
        if(skipsMoneyMovement()) return 0.0; //bankrupt guilds dont pay or receive money, they need to get our of bankrupcy first
        Faction f = guild.getFaction();
        if (f == null) return 0.0;
        switch (cashflow) {
            case GUILDS:
                if(!guild.isBase()) return 0;
                if (f.getGuildHandler() == null || f.getGuildHandler().getGuilds() == null) break;
                for(Guild g : f.getGuildHandler().getGuilds()) {
                    if(g == null || g.isBase() || g.getLedger() == null) continue;
                    amount += Math.abs(g.getLedger().getIncome(Cashflow.GUILD_PAYMENTS));
                }
                break;
            case GUILD_PAYMENTS:
                if(guild.isBase()) return 0;
                amount = -getGrossTaxableIncome();
                amount *= guild.getFaction().getTaxRate(TaxTarget.GUILDS, guild.getId(), true)/100.0;
                break;
            case DIVIDENDS:
                if(!guild.isBase()) return 0;
                amount = getDividendTaxReceived();
                break;
            case DIVIDEND_PAYMENT:
                if(guild.isBase()) return 0;
                amount = -getDividendBreakdown().tax();
                break;
            case DIVIDEND_PAYOUT:
                if(guild.isBase()) return 0;
                amount = -getDividendBreakdown().payout();
                break;
            case VASSALS:
                if(!guild.isBase()) return 0;
                for(Faction vassal : RelationManager.getSubjects(guild.getFaction())) {
                    amount += Math.abs(vassal.getOrCreateMainGuild().getLedger().getIncome(Cashflow.OVERLORD_TAX));
                }
                break;
            case CITIZENS:
                if(!guild.isBase()) return 0;
                amount = getAggregatedCitizenTax();
                break;
            case TARIFFS:
                if(!guild.isBase()) return 0;
                amount = getTotalTariffsEarned();
                break;
            case TARIFF_PAYMENTS: {
                TradeBreakdown tariffs = guild.getTradeBreakdown();
                amount = tariffs == null ? 0 : -tariffs.getTariffs();
                break;
            }
            case TRIBUTE_PAYMENTS:
                if(!guild.isBase()) return 0;
                amount = -getTributeTax();
                break;
            case TRIBUTES:
                if(!guild.isBase()) return 0;
                amount = getTributeRecieved();
                break;
            case OVERLORD_TAX:
                if(!guild.isBase()) return 0;
                if(f.getOverlord() == null) return 0;
                amount = -getOverlordTax();
                break;
            //Loans
            case LOAN_PAYMENTS: {
                if (guild.getLoanHandler() == null || guild.getLoanHandler().getLoansTaken() == null) break;
                for(Loan loan : guild.getLoanHandler().getLoansTaken()) {
                    if(loan == null || !loan.isAutoPay()) continue;
                    if(loan.isPaidOff()) continue;
                    amount -= loan.getDailyPayment(true);
                }
                break;
            }
            case LOANS:
                amount += getAggregatedLoanPayments();
                if (guild.getLoanHandler() == null || guild.getLoanHandler().getLoansGiven() == null) break;
                for(Loan loan : guild.getLoanHandler().getLoansGiven()) {
                    if(loan == null || !loan.isAutoPay()) continue;
                    if(loan.isPaidOff()) continue;
                    amount += loan.getDailyPayment(true);
                }
                break;
            //Interest
            case INTEREST_PAYMENTS: {
                if (guild.getLoanHandler() == null || guild.getLoanHandler().getLoansTaken() == null) break;
                for(Loan loan : guild.getLoanHandler().getLoansTaken()) {
                    if(loan == null || !loan.isAutoPay()) continue;
                    if(loan.isPaidOff()) continue;
                    amount -= loan.getDailyInterest();
                }
                break;
            }
            case INTEREST:
                amount += getAggregatedInterestPayments();
                if (guild.getLoanHandler() == null || guild.getLoanHandler().getLoansGiven() == null) break;
                for(Loan loan : guild.getLoanHandler().getLoansGiven()) {
                    if(loan == null || !loan.isAutoPay()) continue;
                    if(loan.isPaidOff()) continue;
                    amount += loan.getDailyInterest();
                }
                break;
            case WAR_REPARATIONS:
                if (!guild.isBase()) {
                    return 0;
                }
                amount = getWarReparationsReceived();
                break;
            case WAR_REPARATIONS_PAYMENT:
                if (!guild.isBase()) {
                    return 0;
                }
                amount = -getWarReparationsPayment();
                break;
            // No isBase() guard: the guild whose tables won declares it, and the capital picks its
            // share up through the ordinary tax on guilds.
            case GAMBLING:
                amount = casinoProfit;
                break;
            case TRADE: {
                TradeBreakdown trade = guild.getTradeBreakdown();
                amount = trade == null ? 0 : trade.getIncome();
                break;
            }
            case TRADE_UPKEEP: {
                TradeBreakdown tradeUpkeep = guild.getTradeBreakdown();
                amount = tradeUpkeep == null ? 0 : -tradeUpkeep.getUpkeep();
                break;
            }
            case INSTALLATIONS:
                if (!guild.isBase()) {
                    return 0;
                }
                if (f.getInstallationHandler() == null || f.getInstallationHandler().getAll() == null) {
                    break;
                }
                for (Installation installation : f.getInstallationHandler().getAll()) {
                    if (installation == null) continue;
                    amount -= InstallationConfigLoader.getDailyUpkeep(installation.getKind());
                }
                break;
            case MILITARY_UPKEEP:
                if (guild.isBase() && f.getMilitary() != null) {
                    amount = -f.getMilitary().getTotalUpkeep();
                }
                amount -= getCompanySlotUpkeep();
                break;
            case UPGRADES_UPKEEP:
                if (guild.getUpgrades() != null) {
                    for(Upgrade u : guild.getUpgrades()) {
                        if (u == null) continue;
                        amount -= u.getTotalUpkeep();
                    }
                }
                if (guild.getCompany() != null && guild.getCompany().isFormed()) {
                    amount -= guild.getCompany().getUpgradeUpkeep();
                }
                break;
            case NODES:
                amount = -nodeUpkeepLookup.applyAsDouble(guild);
                break;
            //Mercenary contracts
            case MERCENARY_CONTRACT:
                amount = getAggregatedContractEarnings();
                break;
            case MERCENARY_PAYMENTS:
                if(!guild.isBase()) return 0;
                for(double owed : mercenaryPayments.values()) {
                    amount -= owed;
                }
                break;
            case REFUNDS:
                if(!guild.isBase()) return 0;
                for(double owed : refunds.values()) {
                    amount += owed;
                }
                break;
            case REFUND_PAYMENTS:
                amount = -getAggregatedContractRefunds();
                break;
            case WAGE_PAYMENTS:
                amount = -getAggregatedPendingWages();
                break;
            case PENALTIES:
                if(!guild.isBase()) return 0;
                amount = -f.getPenalty();
                break;
            default:
                break;
        }
        return Formatter.formatDouble(amount);
    }

    public double getTotalTariffsEarned() {
        double total = 0;
        for(Guild g : FactionManager.getAllGuilds()) {
            total += g.getTradeBreakdown().getTariffsByFaction(guild.getFaction());
        }
        return total;
    }

    public List<Map.Entry<String, Double>> getCitizenTaxEntriesDescending() {
        return citizenTaxes.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .toList();
    }


    private double getAggregatedCitizenTax() {
        double total = 0;
        for(Double d : citizenTaxes.values()) {
            total+=d;
        }
        return total;
    }

    private double getAggregatedLoanPayments() {
        double total = 0;
        for(Double d : loanPayments.values()) {
            total+=d;
        }
        return total;
    }

    private double getAggregatedInterestPayments() {
        double total = 0;
        for(Double d : interestPayments.values()) {
            total+=d;
        }
        return total;
    }

    private MercenaryCompany getFormedCompany() {
        MercenaryCompany company = guild.getCompany();
        if(company == null || !company.isFormed()) return null;
        return company;
    }

    /** Slot upkeep the host guild owes for its own company, zero for everyone else. */
    private double getCompanySlotUpkeep() {
        MercenaryCompany company = getFormedCompany();
        return company == null ? 0 : company.getSlotUpkeep();
    }

    /**
     * Battle prices and day prices accrued onto this guild's own contracts. Absolute
     * denars written at signing, so this can never be a share of another ledger.
     */
    private double getAggregatedContractEarnings() {
        MercenaryCompany company = getFormedCompany();
        if(company == null) return 0;
        double total = 0;
        for(MercenaryContract c : company.getContractHandler().getAll()) {
            total += c.getAccruedToCompany();
        }
        return total;
    }

    /** Absence refunds this guild's own company owes back to its hirers. */
    private double getAggregatedContractRefunds() {
        MercenaryCompany company = getFormedCompany();
        if(company == null) return 0;
        double total = 0;
        for(MercenaryContract c : company.getContractHandler().getAll()) {
            total += c.getAccruedToHirer();
        }
        return total;
    }

    /** Wages this guild's own company owes its enlisted players. */
    private double getAggregatedPendingWages() {
        MercenaryCompany company = getFormedCompany();
        if(company == null) return 0;
        double total = 0;
        for(Double d : company.getPendingWages().values()) {
            total += d;
        }
        return total;
    }

    public double getNetIncome() {
        double net = 0.0;
        if(skipsMoneyMovement()) return 0.0; //bankrupt guilds dont pay or receive money, they need to get our of bankrupcy first

        for (Cashflow cf : Cashflow.values()) {
            switch (cf) {

                // -------- POSITIVE / INCOME --------
                case TRADE:
                case CITIZENS:
                case TARIFFS:
                case GAMBLING:
                case GUILDS:
                case VASSALS:
                case TRIBUTES:
                case DIVIDENDS:
                case WAR_REPARATIONS:
                case LOANS:
                case INTEREST:
                case MERCENARY_CONTRACT:
                case REFUNDS:
                    net += getIncome(cf);
                    break;

                // -------- NEGATIVE / COSTS --------
                case TRADE_UPKEEP:
                case UPGRADES_UPKEEP:
                case INSTALLATIONS:
                case MILITARY_UPKEEP:
                case NODES:
                case PENALTIES:
                case GUILD_PAYMENTS:
                case OVERLORD_TAX:
                case TRIBUTE_PAYMENTS:
                case TARIFF_PAYMENTS:
                case DIVIDEND_PAYMENT:
                case DIVIDEND_PAYOUT:
                case WAR_REPARATIONS_PAYMENT:
                case LOAN_PAYMENTS:
                case INTEREST_PAYMENTS:
                case MERCENARY_PAYMENTS:
                case REFUND_PAYMENTS:
                case WAGE_PAYMENTS:
                    net += getIncome(cf); // already negative
                    break;

                default:
                    break;
            }
        }

        return Formatter.formatDouble(net);
    }
    
    public double getInflationDelta() {
        double delta = 0.0;

        for (Cashflow cashflow : Cashflow.values()) {
            if (!cashflow.affectsInflation()) continue;

            delta += getIncome(cashflow);
        }

        return delta;
    }

    /**
     * Net income excluding the three dividend cashflows. Load-bearing: a
     * percentage of {@link #getNetIncome()} would be circular because net
     * income contains {@link Cashflow#DIVIDEND_PAYOUT}. Guild tax is already
     * inside this number as {@link Cashflow#GUILD_PAYMENTS}.
     */
    public double getDividendBase() {
        if (skipsMoneyMovement()) {
            return 0.0;
        }
        double net = 0.0;
        for (Cashflow cf : Cashflow.values()) {
            if (isDividendCashflow(cf)) {
                continue;
            }
            switch (cf) {
                case TRADE:
                case CITIZENS:
                case TARIFFS:
                case GAMBLING:
                case GUILDS:
                case VASSALS:
                case TRIBUTES:
                case WAR_REPARATIONS:
                case LOANS:
                case INTEREST:
                case MERCENARY_CONTRACT:
                case REFUNDS:
                    net += getIncome(cf);
                    break;
                case TRADE_UPKEEP:
                case UPGRADES_UPKEEP:
                case INSTALLATIONS:
                case MILITARY_UPKEEP:
                case NODES:
                case PENALTIES:
                case GUILD_PAYMENTS:
                case OVERLORD_TAX:
                case TRIBUTE_PAYMENTS:
                case TARIFF_PAYMENTS:
                case WAR_REPARATIONS_PAYMENT:
                case LOAN_PAYMENTS:
                case INTEREST_PAYMENTS:
                case MERCENARY_PAYMENTS:
                case REFUND_PAYMENTS:
                case WAGE_PAYMENTS:
                    net += getIncome(cf);
                    break;
                default:
                    break;
            }
        }
        return Formatter.formatDouble(net);
    }

    public DividendBreakdown getDividendBreakdown() {
        if (skipsMoneyMovement() || guild.isBase()) {
            return DividendBreakdown.none();
        }
        return breakdownForPool(unclampedPool(getDividendBase()));
    }

    public DividendBreakdown breakdownForPool(double pool) {
        if (skipsMoneyMovement() || guild.isBase() || pool <= 0) {
            return DividendBreakdown.none();
        }
        double base = getDividendBase();
        double clampedPool = Formatter.formatDouble(Math.max(0.0, pool));
        List<String> eligible = guild.getDividendEligibleMembers();
        int count = eligible == null ? 0 : eligible.size();
        if (count == 0) {
            return new DividendBreakdown(base, 0.0, 0.0, 0.0, 0, 0.0);
        }
        Faction f = guild.getFaction();
        double taxRate = f == null ? 0.0 : f.getTaxRate(TaxTarget.DIVIDENDS, guild.getId(), true);
        double tax = Formatter.formatDouble(clampedPool * taxRate / 100.0);
        tax = Math.min(tax, clampedPool);
        double payout = Formatter.formatDouble(clampedPool - tax);
        double perMember = Formatter.formatDouble(payout / count);
        return new DividendBreakdown(base, clampedPool, tax, payout, count, perMember);
    }

    private double unclampedPool(double base) {
        double percent = guild.getDividendPercent();
        if (percent <= 0 || base <= 0) {
            return 0.0;
        }
        return Formatter.formatDouble(base * percent / 100.0);
    }

    private double getDividendTaxReceived() {
        if (!guild.isBase()) {
            return 0.0;
        }
        Faction faction = guild.getFaction();
        if (faction == null || faction.getGuildHandler() == null) {
            return 0.0;
        }
        double total = 0.0;
        for (Guild g : faction.getGuildHandler().getGuilds()) {
            if (g == null || g.isBase()) {
                continue;
            }
            total += Math.abs(g.getLedger().getIncome(Cashflow.DIVIDEND_PAYMENT));
        }
        return total;
    }

    private static boolean isDividendCashflow(Cashflow cashflow) {
        return cashflow == Cashflow.DIVIDENDS
                || cashflow == Cashflow.DIVIDEND_PAYMENT
                || cashflow == Cashflow.DIVIDEND_PAYOUT;
    }

    //Taxes
    public double getOverlordTax() {
        Faction f = guild.getFaction();
        Faction overlord = f.getOverlord();
        if (overlord == null) return 0.0;

        double base = getGrossTaxableIncome();
        return base * f.getOverlordTaxRate(overlord);
    }

    public double getTributeTax() {
        Faction f = guild.getFaction();
        double base = getInternalTaxableIncome();
        double paid = 0.0;
        for (FactionModifier mod : f.getModifiers()) {
            if (mod.getFrom() == null) continue;
            if (!mod.getType().equals(FactionModifiers.TRIBUTE)) continue;
            paid += base * (mod.getAmount() / 100.0);
        }
        return paid;
    }

    public double getTributeRecieved() {
        double total = 0.0;
        for(Faction f : FactionManager.factions) {
            if(f.getId().equals(guild.getFaction().getId())) continue;
            for(FactionModifier mod : f.getModifiers()) {
                if(mod.getFrom() == null) continue;
                if(!mod.getType().equals(FactionModifiers.TRIBUTE)) continue;
                if(!mod.getFrom().getId().equals(guild.getFaction().getId())) continue;

                double base = f.getOrCreateMainGuild().getLedger().getInternalTaxableIncome();
                total += base * (mod.getAmount() / 100.0);
            }
        }
        return total;
    }

    public double getWarReparationsPayment() {
        if (!guild.isBase()) {
            return 0.0;
        }
        Faction f = guild.getFaction();
        double base = getReparationsTaxableIncome();
        double paid = 0.0;
        for (WarReparationsObligation obligation : WarReparationsService.activeObligations(f)) {
            paid += base * (obligation.getIncomePercent() / 100.0);
        }
        return paid;
    }

    public double getWarReparationsReceived() {
        if (!guild.isBase()) {
            return 0.0;
        }
        Faction self = guild.getFaction();
        if (self == null || self.getId() == null) {
            return 0.0;
        }
        double total = 0.0;
        for (Faction f : FactionManager.factions) {
            if (f == null || f.getId().equals(self.getId())) {
                continue;
            }
            Guild payerGuild = f.getOrCreateMainGuild();
            if (payerGuild == null || payerGuild.getLedger() == null) {
                continue;
            }
            double base = payerGuild.getLedger().getReparationsTaxableIncome();
            for (WarReparationsObligation obligation : WarReparationsService.activeObligations(f)) {
                if (!self.getId().equalsIgnoreCase(obligation.getPayeeFactionId())) {
                    continue;
                }
                total += base * (obligation.getIncomePercent() / 100.0);
            }
        }
        return total;
    }

    double getReparationsTaxableIncome() {
        return getInternalTaxableIncome();
    }

    /**
     * Positive gross-counted income excluding cross-faction transfers (tribute,
     * war reparations, vassal guild rollups). Used as the base for tribute and
     * reparations so ledger queries cannot recurse between factions.
     */
    double getInternalTaxableIncome() {
        if (skipsMoneyMovement()) {
            return 0.0;
        }
        double total = 0.0;
        for (Cashflow cf : Cashflow.values()) {
            if (!cf.isGrossCounted() || isCrossFactionGrossCashflow(cf)) {
                continue;
            }
            double amount = getIncome(cf);
            if (amount <= 0) {
                continue;
            }
            total += amount;
        }
        return total;
    }

    private static boolean isCrossFactionGrossCashflow(Cashflow cashflow) {
        return cashflow == Cashflow.TRIBUTES
                || cashflow == Cashflow.WAR_REPARATIONS
                || cashflow == Cashflow.VASSALS
                || cashflow == Cashflow.GUILDS;
    }

    public double getGrossTaxableIncome() {
        if(skipsMoneyMovement()) return 0.0; //bankrupt guilds dont pay or receive money, they need to get our of bankrupcy first
        double total = 0.0;
        for (Cashflow cf : Cashflow.values()) {
            if(!cf.isGrossCounted()) continue;
            double amount = getIncome(cf);
            if(amount <= 0) continue;
            total += amount;
        }
        return total;
    }

    public void clearDailyIncome() {
        citizenTaxes.clear();
    }

    /**
     * Bankruptcy freezes a guild. A missing bank is not bankruptcy, but it also
     * cannot pay or receive, so settlement must skip it instead of throwing.
     */
    private boolean skipsMoneyMovement() {
        if (guild.isBankrupt()) {
            return true;
        }
        return guild.getBank() == null;
    }

    public void populateDailyTransfers(DailyGuildTransfers buffer) {
        if(guild.isBankrupt() && guild.getLoanHandler() != null && guild.getLoanHandler().getLoansTaken() != null) {
            for(Loan loan : guild.getLoanHandler().getLoansTaken()) {
                if(loan == null || !loan.isAutoPay()) continue;
                if(loan.isPaidOff()) continue;
                loan.setAutoPay(false);
            }
        }
        if (!skipsMoneyMovement() && !guild.isBase()) {
            double pool = getDividendBreakdown().pool();
            if (pool > 0) {
                buffer.setPendingDividendPool(guild, pool);
            }
        }
        for (Cashflow cf : Cashflow.values()) {
            applySettlementFor(cf, buffer);
        }
        if (!skipsMoneyMovement()) {
            citizenTaxes.clear();
        }
        loanPayments.clear();
        interestPayments.clear();
        // Taxed once, on the day it was won.
        casinoProfit = 0;
        // Rebuilt from the persisted buckets by every pre-pass, so clearing them for a
        // bankrupt hirer too keeps yesterday's bill from being paid twice.
        mercenaryPayments.clear();
        refunds.clear();
        clearSettledContractBuckets();
    }

    /**
     * The accrued buckets are the durable record of what is owed, so a day cannot be
     * paid twice once they are emptied. A bankrupt guild moved nothing above and keeps
     * its buckets, because its debts survive the bankruptcy that froze them.
     */
    private void clearSettledContractBuckets() {
        if (skipsMoneyMovement()) return;
        MercenaryCompany company = getFormedCompany();
        if (company == null) return;
        for (MercenaryContract c : company.getContractHandler().getAll()) {
            c.clearAccrued();
        }
        company.clearPendingWages();
    }

    private void applySettlementFor(Cashflow cf, DailyGuildTransfers buffer) {
        if(skipsMoneyMovement()) return; //bankrupt guilds dont pay or receive money, they need to get our of bankrupcy first
        switch (cf) {
            // --------- INTERNAL (single guild) ----------
            // These should NOT be computed by reading getIncome() from some other guild.
            // They are simply added to this guild's daily delta.
            // TRADE is paid here and nowhere else, so the bank moves by what the ledger shows.
            case TRADE:
            case TRADE_UPKEEP:
            // INSTALLATIONS: withdrawn in Faction.newDay(), so getIncome() is ledger GUI display only
            case UPGRADES_UPKEEP:
            case PENALTIES:
            case CITIZENS:
            // Dowsing reports the nodes active right now, so a node is charged for each day it runs.
            case NODES:
                buffer.addExternalDelta(guild, getIncome(cf));
                return;

            // Banked by the games plugin the moment a table won it, so adding a delta here would
            // pay the guild twice. It is a tax base and a ledger line, nothing more.
            case GAMBLING:
                return;

            // The faction share of military upkeep is withdrawn in Faction.newDay();
            // only the company's slot upkeep settles here.
            case MILITARY_UPKEEP: {
                double slots = getCompanySlotUpkeep();
                if (slots <= 0) return;
                buffer.addExternalDelta(guild, -slots);
                return;
            }

            // --------- TRANSFERS (guild -> guild) ----------
            case GUILD_PAYMENTS: {
                if (guild.isBase()) return; // base doesn't pay guild tax
                Faction faction = guild.getFaction();
                if (faction == null) return;
                Guild capital = faction.getOrCreateMainGuild();
                double amount = Math.abs(getIncome(Cashflow.GUILD_PAYMENTS));
                buffer.add(guild, capital, amount);
                return;
            }

            case OVERLORD_TAX: {
                if (!guild.isBase()) return; //only base pays
                Faction overlord = guild.getFaction().getOverlord();
                if (overlord == null) return;
                Guild overlordCapital = overlord.getOrCreateMainGuild();
                double amount = Math.abs(getIncome(Cashflow.OVERLORD_TAX));
                buffer.add(guild, overlordCapital, amount);
                return;
            }

            case TRIBUTE_PAYMENTS: {
                if(!guild.isBase()) return; //only base pays
                Faction f = guild.getFaction();
                if (f == null || f.getModifiers() == null) return;
                double base = getInternalTaxableIncome();

                for (FactionModifier mod : f.getModifiers()) {
                    if (mod.getFrom() == null) continue;
                    if (!mod.getType().equals(FactionModifiers.TRIBUTE)) continue;

                    Faction receiverFaction = mod.getFrom();
                    if (receiverFaction == null) continue;
                    Guild receiverGuild = receiverFaction.getOrCreateMainGuild();

                    double amount = base * (mod.getAmount() / 100.0);
                    if (amount <= 0) continue;

                    buffer.add(guild, receiverGuild, amount);
                }
                return;
            }

            case WAR_REPARATIONS_PAYMENT: {
                if (!guild.isBase()) {
                    return;
                }
                Faction f = guild.getFaction();
                if (f == null) return;
                double base = getReparationsTaxableIncome();
                for (WarReparationsObligation obligation : WarReparationsService.activeObligations(f)) {
                    if (obligation == null) continue;
                    Faction receiverFaction = FactionManager.getByString(obligation.getPayeeFactionId());
                    if (receiverFaction == null) {
                        continue;
                    }
                    Guild receiverGuild = receiverFaction.getOrCreateMainGuild();
                    double amount = base * (obligation.getIncomePercent() / 100.0);
                    if (amount <= 0) {
                        continue;
                    }
                    buffer.add(guild, receiverGuild, amount);
                }
                return;
            }

            //Taxes and Tariffs
            case TARIFF_PAYMENTS: {
                TradeBreakdown tariffs = guild.getTradeBreakdown();
                if (tariffs == null || tariffs.getTariffsByFactionMap() == null) return;
                for(Map.Entry<Faction, Double> entry : tariffs.getTariffsByFactionMap().entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) continue;
                    Faction receiverFaction = entry.getKey();
                    Guild receiverGuild = receiverFaction.getOrCreateMainGuild();
                    double amount = entry.getValue();
                    if(amount <= 0) continue;
                    buffer.add(guild, receiverGuild, amount);
                }
                break;
            }

            //Loans
            case LOAN_PAYMENTS: {
                if (guild.getLoanHandler() == null || guild.getLoanHandler().getLoansTaken() == null) return;
                for(Loan loan : guild.getLoanHandler().getLoansTaken()) {
                    double amount = 0;
                    if(loan == null || !loan.isAutoPay()) continue;
                    if(loan.isPaidOff()) continue;
                    amount += loan.getDailyPayment(true);
                    if(amount <= 0) continue;
                    loan.setTempPayment(amount);
                    buffer.add(guild, loan.getIssuer(), amount);
                }
                break;
            }

            //Interest
            case INTEREST_PAYMENTS: {
                if (guild.getLoanHandler() == null || guild.getLoanHandler().getLoansTaken() == null) return;
                for(Loan loan : guild.getLoanHandler().getLoansTaken()) {
                    double amount = 0;
                    if(loan == null || !loan.isAutoPay()) continue;
                    if(loan.isPaidOff()) continue;
                    amount += loan.getDailyInterest();
                    if(amount <= 0) continue;
                    loan.setTempInterestPayment(amount);
                    buffer.add(guild, loan.getIssuer(), amount);
                }
                break;
            }

            case LOANS:
                buffer.addExternalDelta(guild, getAggregatedLoanPayments());
                break;
            case INTEREST:
                buffer.addExternalDelta(guild, getAggregatedInterestPayments());
                break;

            //Mercenary contracts. The hiring capital pays from a pushed map because it
            //owns no contract object; the host guild pays its refunds and wages from its own.
            case MERCENARY_PAYMENTS: {
                if (!guild.isBase()) return;
                for (Map.Entry<String, Double> entry : mercenaryPayments.entrySet()) {
                    double amount = entry.getValue() == null ? 0 : entry.getValue();
                    if (amount <= 0) continue;
                    Guild host = FactionManager.getGuildByString(entry.getKey());
                    if (host == null || host == guild) continue;
                    buffer.add(guild, host, amount);
                }
                return;
            }

            case REFUND_PAYMENTS: {
                MercenaryCompany company = getFormedCompany();
                if (company == null) return;
                for (MercenaryContract c : company.getContractHandler().getAll()) {
                    double amount = c.getAccruedToHirer();
                    if (amount <= 0) continue;
                    Faction hirer = c.getHirer();
                    if (hirer == null) continue;
                    Guild capital = hirer.getOrCreateMainGuild();
                    if (capital == null || capital == guild) continue;
                    buffer.add(guild, capital, amount);
                }
                return;
            }

            case WAGE_PAYMENTS: {
                MercenaryCompany company = getFormedCompany();
                if (company == null) return;
                PlayerUuidLookup uuids = MercenaryEngagements.uuidLookup();
                if (uuids == null) return;
                for (Map.Entry<String, Double> entry : company.getPendingWages().entrySet()) {
                    double amount = entry.getValue() == null ? 0 : entry.getValue();
                    if (amount <= 0) continue;
                    java.util.UUID id = uuids.uuidOf(entry.getKey());
                    if (id == null) continue;
                    buffer.addPlayerPayout(guild, id, amount);
                }
                return;
            }

            // Display only - dividends settle in PostSettlementPayouts after other movements,
            // and the mercenary receiving halves are moved by the paying side above.
            case DIVIDEND_PAYOUT:
            case DIVIDEND_PAYMENT:
            case GUILDS:
            case VASSALS:
            case DIVIDENDS:
            case TRIBUTES:
            case TARIFFS:
            case WAR_REPARATIONS:
            case MERCENARY_CONTRACT:
            case REFUNDS:
            default:
                return;
        }
    }
}
