package net.tfminecraft.simplefactions.mercenary.contract;

/**
 * Where the Phase 5 reputation calculator will hook in. The deltas themselves are
 * computed there, in the shape of
 * {@link net.tfminecraft.simplefactions.guild.loans.CreditCalculator}; this phase only
 * needs the call site to exist and to be silent on a loyalty conflict.
 */
public interface ContractReputationSeam {
    /** Does nothing until Phase 5 replaces it. */
    ContractReputationSeam NONE = (contract, reason) -> {
    };

    void onTermination(MercenaryContract contract, TerminationReason reason);
}
