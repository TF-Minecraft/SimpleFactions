package me.Plugins.SimpleFactions.mercenary.company;

import me.Plugins.SimpleFactions.mercenary.contract.ContractReputationSeam;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryContract;
import me.Plugins.SimpleFactions.mercenary.contract.TerminationReason;

/**
 * Turns a termination into a reputation move. A loyalty conflict never arrives
 * here, because {@link TerminationReason#movesReputation()} filters it before the
 * seam is called: neither party caused it.
 */
public final class MercenaryReputationSeam implements ContractReputationSeam {

    @Override
    public void onTermination(MercenaryContract contract, TerminationReason reason) {
        if (contract == null || reason == null) return;
        MercenaryCompany company = contract.getCompany();
        if (company == null) return;
        company.changeReputation(deltaFor(contract, reason));
    }

    private int deltaFor(MercenaryContract contract, TerminationReason reason) {
        switch (reason) {
            case DURATION_ELAPSED:
                return MercenaryReputationCalculator.calculateCompletionBonus(contract);
            case SLOTS_BREACH:
            case COMPANY_DISBANDED:
                return MercenaryReputationCalculator.calculateBreachPenalty(contract);
            case HOST_BANKRUPT:
                return MercenaryReputationCalculator.calculateBankruptcyPenalty(contract);
            default:
                return 0;
        }
    }
}
