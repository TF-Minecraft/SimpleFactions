package me.Plugins.SimpleFactions.mercenary.contract;

/**
 * The ways a contract can end, each carrying the outcome locked in
 * docs/planning/war-companies/00-index.md section 5. Days already served are paid
 * in every one of them, so that is not a per-reason flag.
 */
public enum TerminationReason {
    /** Ran its course. Reputation rises only if attendance was clean throughout. */
    DURATION_ELAPSED(ContractStatus.COMPLETED, false, true),
    /** The company fell below the slots it promised. Refund and a large hit. */
    SLOTS_BREACH(ContractStatus.BREACHED, true, true),
    /**
     * The host guild went bankrupt. A bankrupt guild is inert in both directions by
     * design, so no refund is even possible; the punishment is social.
     */
    HOST_BANKRUPT(ContractStatus.TERMINATED, false, true),
    /** An ally joined, a vassalage landed, or a government changed. Nobody's fault. */
    LOYALTY_CONFLICT(ContractStatus.TERMINATED, false, false),
    /**
     * The company itself vanished (leader's active character lost the mercenary
     * trait). Same money and reputation as a slot breach: the hirer bought slots
     * that can no longer be delivered.
     */
    COMPANY_DISBANDED(ContractStatus.BREACHED, true, true);

    private final ContractStatus outcome;
    private final boolean paysBreachRefund;
    private final boolean movesReputation;

    TerminationReason(ContractStatus outcome, boolean paysBreachRefund, boolean movesReputation) {
        this.outcome = outcome;
        this.paysBreachRefund = paysBreachRefund;
        this.movesReputation = movesReputation;
    }

    public ContractStatus getOutcome() {
        return outcome;
    }

    public boolean paysBreachRefund() {
        return paysBreachRefund;
    }

    public boolean movesReputation() {
        return movesReputation;
    }
}
