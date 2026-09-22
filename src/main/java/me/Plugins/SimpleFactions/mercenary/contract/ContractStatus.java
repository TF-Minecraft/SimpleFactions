package me.Plugins.SimpleFactions.mercenary.contract;

/**
 * Where a contract is in its life. Transitions are one-way so refunds and
 * reputation fire exactly once, the same discipline
 * {@link me.Plugins.SimpleFactions.Guild.loans.LoanStatus} keeps.
 */
public enum ContractStatus {
    /** Signed by the company, waiting for the hiring faction. Holds a soft slot reservation. */
    OFFERED,
    /** Accepted and running. Holds a hard slot reservation. */
    ACTIVE,
    /** Ran to the end of its duration. */
    COMPLETED,
    /** The company fell below the slots it promised. */
    BREACHED,
    /** Ended for a reason neither party chose, or an offer that was refused. */
    TERMINATED;

    /** Only these two hold slots; everything else has released them. */
    public boolean reservesSlots() {
        return this == OFFERED || this == ACTIVE;
    }

    public boolean isFinished() {
        return this == COMPLETED || this == BREACHED || this == TERMINATED;
    }
}
