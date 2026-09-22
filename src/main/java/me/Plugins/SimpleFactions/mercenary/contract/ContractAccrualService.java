package me.Plugins.SimpleFactions.mercenary.contract;

import java.util.List;
import java.util.UUID;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.PostSettlementPayouts.PlayerUuidLookup;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryReputationCalculator;
import me.Plugins.SimpleFactions.mercenary.company.WageSettings;

/**
 * Accrues absolute denars onto the contract, then hands the totals to the two
 * ledgers that need them. Every figure here is a number written into the contract
 * at signing, never a share of anyone's income, which is what keeps these
 * cashflows structurally incapable of recursing.
 */
public final class ContractAccrualService {
    private ContractAccrualService() {
    }

    /* =====================================================
     * Battle legs
     * ===================================================== */

    public static void onBattleStarted(String battleId, Integer warId) {
        War war = warId == null ? null : WarManager.getById(warId);
        if (war == null || battleId == null) return;
        charge(war, battleId, war.getAttackers());
        charge(war, battleId, war.getDefenders());
    }

    public static void onBattleEnded(String battleId, Integer warId) {
        War war = warId == null ? null : WarManager.getById(warId);
        if (war == null || battleId == null) return;
        resolve(war, battleId, war.getAttackers());
        resolve(war, battleId, war.getDefenders());
    }

    private static void charge(War war, String battleId, Side side) {
        for (MercenaryEngagements.Engagement engagement : MercenaryEngagements.on(war, side)) {
            MercenaryContract contract = engagement.contract();
            if (contract == null || !contract.isActive()) continue;
            contract.accrueBattleCharge(battleId, contract.getBattlePrice());
        }
    }

    /**
     * One pass at battle end settles the absence refund, the per-battle wage for
     * the soldiers who actually showed up, and the reputation hit for the slots
     * that did not. All three are gated on the same once-per-battle flag, so a
     * replayed event changes nothing.
     */
    private static void resolve(War war, String battleId, Side side) {
        for (MercenaryEngagements.Engagement engagement : MercenaryEngagements.on(war, side)) {
            MercenaryContract contract = engagement.contract();
            if (contract == null) continue;
            AttendanceService.Result result = AttendanceService.result(contract, battleId);
            if (result.snapshotMissing()) continue;
            double amount = result.absent() * contract.getAbsenceRefundPerSlotPerBattle();
            if (!contract.accrueAbsenceRefund(battleId, amount)) continue;
            payBattleWages(engagement.company(), contract, result);
            applyAbsenceReputation(engagement.company(), contract, result);
        }
    }

    /** A battle with partial attendance costs reputation in proportion to who was missing. */
    private static void applyAbsenceReputation(
            MercenaryCompany company, MercenaryContract contract, AttendanceService.Result result) {
        if (company == null || result.absent() <= 0) return;
        company.changeReputation(
                MercenaryReputationCalculator.calculateAbsencePenalty(contract, result.absent()));
    }

    /**
     * The battle share is only earned by passing attendance for that battle, so it
     * is paid per attending player rather than divided over the promised slots.
     */
    private static void payBattleWages(
            MercenaryCompany company, MercenaryContract contract, AttendanceService.Result result) {
        if (company == null || result.attendedIds().isEmpty()) return;
        PlayerUuidLookup uuids = MercenaryEngagements.uuidLookup();
        if (uuids == null) return;
        WageSettings wages = company.getWageSettings();
        for (String player : company.getEnlisted()) {
            UUID id = uuids.uuidOf(player);
            if (id == null || !result.attendedIds().contains(id)) continue;
            company.accrueWage(player, wages.activeShareOf(contract.getPricePerSlotPerBattle(), player));
        }
    }

    /* =====================================================
     * Daily leg
     * ===================================================== */

    /**
     * Runs once at the top of the daily settlement, before any guild populates its
     * transfers. Accrues the day price and a day of payroll, then pushes the whole
     * of both contract buckets onto the hiring capital, which owns no contract
     * object and therefore cannot compute its own bill.
     *
     * <p>Rebuilding the push from the persisted buckets rather than pushing when a
     * battle resolves means a restart mid-day cannot lose a charge.
     */
    public static void accrueDailyAndPush() {
        for (Guild host : FactionManager.getAllGuilds()) {
            if (host == null || host.isBankrupt()) continue;
            MercenaryCompany company = host.getCompany();
            if (company == null || !company.isFormed()) continue;

            accruePeacetimeWages(company);
            for (MercenaryContract contract : company.getContractHandler().getActive()) {
                Faction hirer = contract.getHirer();
                Guild capital = hirer == null ? null : hirer.getOrCreateMainGuild();
                if (capital == null || capital.isBankrupt()) continue;
                contract.accrueDayPrice();
                accrueDayWages(company, contract);
            }
            pushToHirers(host, company);
        }
    }

    /** Paid to every enlisted player whether or not the company holds a contract. */
    private static void accruePeacetimeWages(MercenaryCompany company) {
        WageSettings wages = company.getWageSettings();
        for (String player : company.getEnlisted()) {
            company.accrueWage(player, wages.peacetimeFor(player));
        }
    }

    /** The day share belongs to the players covering the slots the contract bought. */
    private static void accrueDayWages(MercenaryCompany company, MercenaryContract contract) {
        WageSettings wages = company.getWageSettings();
        List<String> enlisted = company.getEnlisted();
        int covered = Math.min(contract.getSlots(), enlisted.size());
        for (int i = 0; i < covered; i++) {
            String player = enlisted.get(i);
            company.accrueWage(player, wages.activeShareOf(contract.getPricePerSlotPerDay(), player));
        }
    }

    private static void pushToHirers(Guild host, MercenaryCompany company) {
        for (MercenaryContract contract : company.getContractHandler().getAll()) {
            Faction hirer = contract.getHirer();
            Guild capital = hirer == null ? null : hirer.getOrCreateMainGuild();
            if (capital == null) continue;
            double owed = contract.getAccruedToCompany();
            if (owed > 0) capital.getLedger().addMercenaryPaymentEntry(host.getId(), owed);
            double refund = contract.getAccruedToHirer();
            if (refund > 0) capital.getLedger().addRefundEntry(host.getId(), refund);
        }
    }
}
