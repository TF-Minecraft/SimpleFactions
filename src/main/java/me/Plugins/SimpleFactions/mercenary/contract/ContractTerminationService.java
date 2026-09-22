package me.Plugins.SimpleFactions.mercenary.contract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/**
 * One service, five triggers, each with its locked outcome. Everything funnels
 * through {@link #terminate}, whose status guard is what makes a refund fire
 * exactly once even when the trigger repeats.
 */
public final class ContractTerminationService {
    private static ContractReputationSeam reputation = ContractReputationSeam.NONE;

    private ContractTerminationService() {
    }

    /** Phase 5 swaps in the real calculator; tests swap in a recorder. */
    public static void setReputationSeam(ContractReputationSeam seam) {
        reputation = seam == null ? ContractReputationSeam.NONE : seam;
    }

    public static ContractReputationSeam getReputationSeam() {
        return reputation;
    }

    /**
     * @return the refund owed to the hirer, or 0. Phase 5 moves the money; this
     *         phase only decides the figure, and only on the first call.
     */
    public static double terminate(MercenaryContract contract, TerminationReason reason) {
        if (contract == null || reason == null) return 0;
        if (!contract.finish(reason.getOutcome())) return 0;
        if (reason.movesReputation()) {
            reputation.onTermination(contract, reason);
        }
        return reason.paysBreachRefund() ? contract.getBreachRefund() : 0;
    }

    /* =====================================================
     * Triggers
     * ===================================================== */

    /** Contracts whose window has run out. Reputation rises only on clean attendance. */
    public static List<MercenaryContract> completeElapsed(MercenaryCompany company) {
        List<MercenaryContract> finished = new ArrayList<>();
        if (company == null) return finished;
        for (MercenaryContract c : company.getContractHandler().getActive()) {
            if (!c.isElapsed()) continue;
            terminate(c, TerminationReason.DURATION_ELAPSED);
            finished.add(c);
        }
        return finished;
    }

    /**
     * Called from the single slot-loss path. Mercenary slots never die in battle, so
     * a kick or a manual slot decrease is the only way a company can end up owing
     * more slots than it owns.
     */
    public static List<MercenaryContract> checkSlotCommitments(MercenaryCompany company) {
        List<MercenaryContract> breached = new ArrayList<>();
        if (company == null) return breached;
        for (MercenaryContract c : company.getContractHandler().getActive()) {
            if (c.getSlots() <= company.getSlots()) continue;
            terminate(c, TerminationReason.SLOTS_BREACH);
            breached.add(c);
        }
        return breached;
    }

    /**
     * A bankrupt guild moves no money in either direction, so a contract left alone
     * would silently reserve slots forever. Terminate it explicitly instead.
     */
    public static List<MercenaryContract> checkBankruptcy(MercenaryCompany company) {
        List<MercenaryContract> ended = new ArrayList<>();
        if (company == null) return ended;
        Guild guild = company.getGuild();
        if (guild == null || !guild.isBankrupt()) return ended;
        for (MercenaryContract c : company.getContractHandler().getActive()) {
            terminate(c, TerminationReason.HOST_BANKRUPT);
            ended.add(c);
        }
        return ended;
    }

    /** Neither party caused this one, so no refund and no reputation change. */
    public static List<MercenaryContract> loyaltyConflicts(MercenaryCompany company) {
        List<MercenaryContract> ended = new ArrayList<>();
        if (company == null) return ended;
        for (MercenaryContract c : company.getContractHandler().getActive()) {
            Faction hirer = c.getHirer();
            if (hirer == null) continue;
            if (MercenaryLoyalty.canServe(company, hirer).ok()) continue;
            terminate(c, TerminationReason.LOYALTY_CONFLICT);
            ended.add(c);
        }
        return ended;
    }

    /**
     * A company may not serve both sides of one war. The elder contract (earlier
     * issue date) stays; later ones on the opposite side end as a loyalty conflict.
     */
    public static List<MercenaryContract> resolveDoubleHire(MercenaryCompany company) {
        return resolveDoubleHire(company, WarManager.getActive());
    }

    public static List<MercenaryContract> resolveDoubleHire(
            MercenaryCompany company, Collection<War> wars) {
        List<MercenaryContract> ended = new ArrayList<>();
        if (company == null || wars == null) return ended;
        List<MercenaryContract> active = company.getContractHandler().getActive();
        if (active.size() < 2) return ended;
        for (War war : wars) {
            if (war == null) continue;
            MercenaryContract elder = null;
            for (MercenaryContract c : active) {
                if (c.getHirer() == null || !war.isParticipating(c.getHirer())) continue;
                if (elder == null || isJuniorTo(elder, c)) elder = c;
            }
            if (elder == null || elder.getHirer() == null) continue;
            Side elderSide = war.getSide(elder.getHirer());
            Side opposite = war.getOppositeSide(elder.getHirer());
            if (elderSide == null || opposite == null) continue;
            for (MercenaryContract c : active) {
                if (c == elder || ended.contains(c)) continue;
                Faction hirer = c.getHirer();
                if (hirer == null || !opposite.isParticipating(hirer)) continue;
                terminate(c, TerminationReason.LOYALTY_CONFLICT);
                ended.add(c);
            }
        }
        return ended;
    }

    private static boolean isJuniorTo(MercenaryContract currentElder, MercenaryContract candidate) {
        if (candidate.getIssueDate() < currentElder.getIssueDate()) return true;
        if (candidate.getIssueDate() > currentElder.getIssueDate()) return false;
        String a = currentElder.getId();
        String b = candidate.getId();
        if (a == null) return true;
        if (b == null) return false;
        return b.compareToIgnoreCase(a) < 0;
    }

    /* =====================================================
     * Daily sweep
     * ===================================================== */

    /** Driven from {@code FactionManager.settleIncome()} beside the loan day tick. */
    public static void tickDay() {
        for (Guild g : FactionManager.getAllGuilds()) {
            if (g == null) continue;
            MercenaryCompany company = g.getCompany();
            if (company == null || !company.isFormed()) continue;
            for (MercenaryContract c : company.getContractHandler().getActive()) {
                c.addDayServed();
            }
            completeElapsed(company);
            checkBankruptcy(company);
        }
    }
}
