package net.tfminecraft.simplefactions.guild.income;

import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;

/** Detached financial inputs shared by the ledgers of one realm calculation. */
public final class SettlementLedgerView {
    final Map<Guild, Ledger> realm;
    final List<Loan> given;
    final List<Loan> taken;
    final MercenaryCompany company;
    final Map<Cashflow, Double> income;
    final double wealth;
    double donation;

    public SettlementLedgerView(Map<Guild, Ledger> realm, List<Loan> given, List<Loan> taken,
            MercenaryCompany company, Map<Cashflow, Double> income, double wealth, double donation) {
        this.realm = realm;
        this.given = List.copyOf(given);
        this.taken = List.copyOf(taken);
        this.company = company;
        this.income = Map.copyOf(income);
        this.wealth = wealth;
        this.donation = donation;
    }

    public double donation() { return donation; }
}
