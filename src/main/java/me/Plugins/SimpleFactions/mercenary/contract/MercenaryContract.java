package me.Plugins.SimpleFactions.mercenary.contract;

import java.util.UUID;

import me.Plugins.SimpleFactions.Database.MercenaryContractData;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/**
 * A company's promise of slots to a faction for a window of days, modelled on
 * {@link me.Plugins.SimpleFactions.Guild.loans.Loan}. Every figure is written
 * once at signing and never recomputed from live income.
 *
 * <p>There is deliberately no war on a contract. A faction hires a company, not
 * a war, so the company serves in every war that faction is in for the duration,
 * exactly as the faction's own military does.
 */
public class MercenaryContract {
    /** How long an unaccepted offer holds its slots before lapsing. */
    public static final long OFFER_WINDOW_MS = 86400000L;

    private final String id;
    private final MercenaryCompany company;
    private final String hirerFactionId;
    private final ContractKind kind;
    private final ContractTerms terms;
    private final long issueDate;
    private final long dueDate;
    private final int reputationAtSigning;

    private ContractStatus status;
    /** Days the company has actually served. Always owed, whatever ends the contract. */
    private int daysServed;
    /** Set false by Phase 4 the first time a slot fails attendance. */
    private boolean attendanceClean = true;
    private double accruedToCompany;
    private double accruedToHirer;
    private final java.util.Set<String> battleIdsCharged = new java.util.LinkedHashSet<>();
    private final java.util.Set<String> battleIdsRefunded = new java.util.LinkedHashSet<>();

    public MercenaryContract(
            MercenaryCompany company,
            Faction hirer,
            ContractKind kind,
            ContractTerms terms,
            long issueDate) {
        this.id = UUID.randomUUID().toString();
        this.company = company;
        this.hirerFactionId = hirer == null ? null : hirer.getId();
        this.kind = kind == null ? ContractKind.MERCENARY : kind;
        this.terms = terms;
        this.issueDate = issueDate;
        this.dueDate = issueDate + terms.durationMillis();
        this.reputationAtSigning = company == null ? 0 : company.getReputation();
        this.status = ContractStatus.OFFERED;
    }

    public MercenaryContract(MercenaryCompany company, MercenaryContractData data) {
        this.id = data.id;
        this.company = company;
        this.hirerFactionId = data.hirer;
        this.kind = data.kind == null ? ContractKind.MERCENARY : ContractKind.valueOf(data.kind);
        this.terms = new ContractTerms(
                data.slots == null ? 0 : data.slots,
                data.pricePerSlotPerBattle == null ? 0 : data.pricePerSlotPerBattle,
                data.pricePerSlotPerDay == null ? 0 : data.pricePerSlotPerDay,
                data.durationDays == null ? 0 : data.durationDays,
                data.absenceRefundPerSlotPerBattle == null ? 0 : data.absenceRefundPerSlotPerBattle,
                data.breachRefund == null ? 0 : data.breachRefund);
        this.issueDate = data.issueDate == null ? 0 : data.issueDate;
        this.dueDate = data.dueDate == null ? 0 : data.dueDate;
        this.reputationAtSigning = data.reputationAtSigning == null ? 0 : data.reputationAtSigning;
        this.status = data.status == null
                ? ContractStatus.OFFERED : ContractStatus.valueOf(data.status);
        this.daysServed = data.daysServed == null ? 0 : data.daysServed;
        this.attendanceClean = data.attendanceClean == null || data.attendanceClean;
        this.accruedToCompany = data.accruedToCompany == null ? 0 : data.accruedToCompany;
        this.accruedToHirer = data.accruedToHirer == null ? 0 : data.accruedToHirer;
        if (data.battleIdsCharged != null) this.battleIdsCharged.addAll(data.battleIdsCharged);
        if (data.battleIdsRefunded != null) this.battleIdsRefunded.addAll(data.battleIdsRefunded);
    }

    /* =====================================================
     * Identity
     * ===================================================== */

    public String getId() {
        return id;
    }

    public MercenaryCompany getCompany() {
        return company;
    }

    public String getHirerFactionId() {
        return hirerFactionId;
    }

    /** Resolved late, because a contract can load before its hiring faction does. */
    public Faction getHirer() {
        return hirerFactionId == null ? null : FactionManager.getByString(hirerFactionId);
    }

    public boolean isHirer(Faction faction) {
        return faction != null && faction.getId() != null
                && faction.getId().equalsIgnoreCase(hirerFactionId);
    }

    public ContractKind getKind() {
        return kind;
    }

    public ContractTerms getTerms() {
        return terms;
    }

    public int getReputationAtSigning() {
        return reputationAtSigning;
    }

    /* =====================================================
     * Figures
     * ===================================================== */

    public int getSlots() {
        return terms.slots();
    }

    public double getPricePerSlotPerBattle() {
        return terms.pricePerSlotPerBattle();
    }

    public double getPricePerSlotPerDay() {
        return terms.pricePerSlotPerDay();
    }

    public int getDurationDays() {
        return terms.durationDays();
    }

    public double getAbsenceRefundPerSlotPerBattle() {
        return terms.absenceRefundPerSlotPerBattle();
    }

    public double getBreachRefund() {
        return terms.breachRefund();
    }

    public double getDailyPrice() {
        return terms.pricePerSlotPerDay() * terms.slots();
    }

    public double getBattlePrice() {
        return terms.pricePerSlotPerBattle() * terms.slots();
    }

    /* =====================================================
     * Window
     * ===================================================== */

    public long getIssueDate() {
        return issueDate;
    }

    public long getDueDate() {
        return dueDate;
    }

    public int getDaysRemaining() {
        long diff = dueDate - System.currentTimeMillis();
        return (int) (diff / (24L * 60L * 60L * 1000L));
    }

    public boolean isElapsed() {
        return System.currentTimeMillis() >= dueDate;
    }

    /** Derived rather than stored: an offer lapses a day after it was written. */
    public long getOfferExpiry() {
        return issueDate + OFFER_WINDOW_MS;
    }

    public boolean isOfferExpired() {
        return isOffered() && System.currentTimeMillis() >= getOfferExpiry();
    }

    /** Half-open, so two back-to-back contracts do not read as overlapping. */
    public boolean overlaps(long from, long to) {
        return issueDate < to && from < dueDate;
    }

    /* =====================================================
     * Lifecycle
     * ===================================================== */

    public ContractStatus getStatus() {
        return status;
    }

    public boolean isOffered() {
        return status == ContractStatus.OFFERED;
    }

    public boolean isActive() {
        return status == ContractStatus.ACTIVE;
    }

    public boolean reservesSlots() {
        return status.reservesSlots();
    }

    /** Only an offer may become active, so a finished contract cannot be revived. */
    public boolean activate() {
        if (status != ContractStatus.OFFERED) return false;
        status = ContractStatus.ACTIVE;
        return true;
    }

    /** True the first time only, so a repeated trigger cannot pay a refund twice. */
    public boolean finish(ContractStatus outcome) {
        if (outcome == null || !outcome.isFinished()) return false;
        if (status.isFinished()) return false;
        status = outcome;
        return true;
    }

    public int getDaysServed() {
        return daysServed;
    }

    public void addDayServed() {
        daysServed++;
    }

    public void setDaysServed(int daysServed) {
        this.daysServed = Math.max(0, daysServed);
    }

    /** Days served are owed whatever ends the contract; Phase 5 moves the money. */
    public double getServedDaysOwed() {
        return daysServed * getDailyPrice();
    }

    public boolean hasCleanAttendance() {
        return attendanceClean;
    }

    public void markAttendanceFailure() {
        attendanceClean = false;
    }

    public double getAccruedToCompany() {
        return accruedToCompany;
    }

    public double getAccruedToHirer() {
        return accruedToHirer;
    }

    public boolean accrueBattleCharge(String battleId, double amount) {
        if (battleId == null || !battleIdsCharged.add(battleId)) return false;
        accruedToCompany += amount;
        return true;
    }

    public boolean accrueAbsenceRefund(String battleId, double amount) {
        if (battleId == null || !battleIdsRefunded.add(battleId)) return false;
        accruedToHirer += amount;
        return true;
    }

    /**
     * The day price, owed every day the contract is active whether or not a battle
     * is fought. Unlike the battle legs this needs no idempotency set, because the
     * daily settlement pre-pass is the only caller and it runs once a day.
     */
    public void accrueDayPrice() {
        accruedToCompany += getDailyPrice();
    }

    /** Called once the settlement pass has moved the money, so a day cannot be paid twice. */
    public void clearAccrued() {
        accruedToCompany = 0;
        accruedToHirer = 0;
    }

    public java.util.Set<String> getBattleIdsCharged() {
        return java.util.Collections.unmodifiableSet(battleIdsCharged);
    }

    public java.util.Set<String> getBattleIdsRefunded() {
        return java.util.Collections.unmodifiableSet(battleIdsRefunded);
    }

    /* =====================================================
     * Persistence
     * ===================================================== */

    public MercenaryContractData serialize() {
        MercenaryContractData data = new MercenaryContractData();
        data.id = id;
        data.hirer = hirerFactionId;
        data.kind = kind.name();
        data.slots = terms.slots();
        data.pricePerSlotPerBattle = terms.pricePerSlotPerBattle();
        data.pricePerSlotPerDay = terms.pricePerSlotPerDay();
        data.durationDays = terms.durationDays();
        data.absenceRefundPerSlotPerBattle = terms.absenceRefundPerSlotPerBattle();
        data.breachRefund = terms.breachRefund();
        data.issueDate = issueDate;
        data.dueDate = dueDate;
        data.reputationAtSigning = reputationAtSigning;
        data.status = status.name();
        data.daysServed = daysServed;
        data.attendanceClean = attendanceClean;
        data.accruedToCompany = accruedToCompany;
        data.accruedToHirer = accruedToHirer;
        data.battleIdsCharged = new java.util.ArrayList<>(battleIdsCharged);
        data.battleIdsRefunded = new java.util.ArrayList<>(battleIdsRefunded);
        return data;
    }
}
