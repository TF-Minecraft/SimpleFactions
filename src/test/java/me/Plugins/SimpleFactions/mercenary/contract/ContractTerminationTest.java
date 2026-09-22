package me.Plugins.SimpleFactions.mercenary.contract;

import static me.Plugins.SimpleFactions.mercenary.contract.ContractFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;

/**
 * The four termination triggers. Each has one locked outcome, and the guard in
 * {@code terminate} is what keeps a repeated trigger from paying twice.
 */
class ContractTerminationTest {
    private ContractFixture fixture;
    private Faction host;
    private RecordingSeam seam;

    @BeforeEach
    void setUp() {
        fixture = ContractFixture.formed(4);
        host = MercenaryLoyalty.hostFaction(fixture.company);
        when(host.getRelations()).thenReturn(new HashMap<>());
        when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
        seam = new RecordingSeam();
        ContractTerminationService.setReputationSeam(seam);
    }

    @AfterEach
    void tearDown() {
        ContractTerminationService.setReputationSeam(null);
        ContractFixture.tearDown();
    }

    private MercenaryContract active(int slots, long from) {
        MercenaryContract contract = fixture.offer(ContractFixture.validTerms(slots), from);
        contract.activate();
        return contract;
    }

    /* =====================================================
     * Duration elapsed
     * ===================================================== */

    @Test
    void anElapsedContractCompletesWithNoRefundButMovesReputation() {
        MercenaryContract contract = active(2, System.currentTimeMillis() - 8 * DAY);
        contract.addDayServed();
        assertTrue(contract.isElapsed());

        assertEquals(List.of(contract), ContractTerminationService.completeElapsed(fixture.company));
        assertEquals(ContractStatus.COMPLETED, contract.getStatus());
        assertEquals(1, seam.calls);
        assertEquals(TerminationReason.DURATION_ELAPSED, seam.lastReason);
        assertEquals(20.0, contract.getServedDaysOwed());
    }

    @Test
    void aRunningContractIsLeftAlone() {
        MercenaryContract contract = active(1, System.currentTimeMillis());
        assertTrue(ContractTerminationService.completeElapsed(fixture.company).isEmpty());
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertEquals(0, seam.calls);
    }

    /* =====================================================
     * Slots breach
     * ===================================================== */

    /** Slots never die in battle, so dropping one deliberately is the breach path. */
    @Test
    void droppingBelowThePromiseBreachesAndPaysTheBreachRefund() {
        MercenaryContract contract = active(4, System.currentTimeMillis());
        assertEquals(0, ContractTerminationService.checkSlotCommitments(fixture.company).size());

        fixture.company.dropSlot();

        assertEquals(ContractStatus.BREACHED, contract.getStatus());
        assertEquals(1, seam.calls);
        assertEquals(TerminationReason.SLOTS_BREACH, seam.lastReason);
    }

    @Test
    void theBreachRefundIsPaidExactlyOnceEvenWhenTheTriggerRepeats() {
        MercenaryContract contract = active(4, System.currentTimeMillis());

        assertEquals(500.0,
                ContractTerminationService.terminate(contract, TerminationReason.SLOTS_BREACH));
        assertEquals(0.0,
                ContractTerminationService.terminate(contract, TerminationReason.SLOTS_BREACH));
        assertEquals(0.0,
                ContractTerminationService.terminate(contract, TerminationReason.SLOTS_BREACH));
        assertEquals(1, seam.calls);
    }

    @Test
    void aContractStillWithinTheCompanysSizeIsNotBreachedByADrop() {
        MercenaryContract contract = active(2, System.currentTimeMillis());
        fixture.company.dropSlot();
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertEquals(0, seam.calls);
    }

    /** Kicking runs the same guarded check, so no caller needs to know the rules. */
    @Test
    void kickingRunsTheSameCheckWithoutInventingABreach() {
        fixture.company.enlist("Bjorn");
        MercenaryContract contract = active(2, System.currentTimeMillis());

        fixture.company.kick("Bjorn");

        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertEquals(0, seam.calls);
    }

    /* =====================================================
     * Host bankruptcy
     * ===================================================== */

    /**
     * A bankrupt guild is inert in both directions, so without this the contract
     * would sit there holding slots and moving no money at all.
     */
    @Test
    void aBankruptHostTerminatesRatherThanIdling() {
        MercenaryContract contract = active(2, System.currentTimeMillis());
        contract.addDayServed();
        contract.addDayServed();
        when(fixture.host.guild.isBankrupt()).thenReturn(true);

        assertEquals(List.of(contract), ContractTerminationService.checkBankruptcy(fixture.company));
        assertEquals(ContractStatus.TERMINATED, contract.getStatus());
        assertEquals(0.0,
                ContractTerminationService.terminate(contract, TerminationReason.HOST_BANKRUPT),
                "no refund is possible from a bankrupt guild");
        assertEquals(1, seam.calls);
        assertEquals(40.0, contract.getServedDaysOwed());
    }

    @Test
    void aSolventHostIsLeftAlone() {
        MercenaryContract contract = active(1, System.currentTimeMillis());
        assertTrue(ContractTerminationService.checkBankruptcy(fixture.company).isEmpty());
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
    }

    /* =====================================================
     * Loyalty conflict
     * ===================================================== */

    @Test
    void aLoyaltyConflictEndsTheContractWithNoRefundAndNoReputationChange() {
        MercenaryContract contract = active(1, System.currentTimeMillis());
        contract.addDayServed();

        List<War> illegal = List.of(warAgainstHost());
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(WarManager::getActive).thenReturn(illegal);
            assertEquals(List.of(contract),
                    ContractTerminationService.loyaltyConflicts(fixture.company));
        }

        assertEquals(ContractStatus.TERMINATED, contract.getStatus());
        assertEquals(0, seam.calls, "neither party caused it");
        assertEquals(0.0,
                ContractTerminationService.terminate(contract, TerminationReason.LOYALTY_CONFLICT));
        assertEquals(10.0, contract.getServedDaysOwed());
    }

    /* =====================================================
     * Days served are always owed
     * ===================================================== */

    @Test
    void everyPathStillOwesTheDaysAlreadyServed() {
        for (TerminationReason reason : TerminationReason.values()) {
            MercenaryContract contract = active(2, System.currentTimeMillis());
            contract.addDayServed();
            contract.addDayServed();
            contract.addDayServed();

            ContractTerminationService.terminate(contract, reason);

            assertEquals(3, contract.getDaysServed(), reason.name());
            assertEquals(60.0, contract.getServedDaysOwed(), reason.name());
            fixture.company.getContractHandler().remove(contract.getId());
        }
    }

    @Test
    void onlyTheBreachRowPaysARefundAndOnlyTheConflictRowIsSilent() {
        assertFalse(TerminationReason.DURATION_ELAPSED.paysBreachRefund());
        assertTrue(TerminationReason.SLOTS_BREACH.paysBreachRefund());
        assertTrue(TerminationReason.COMPANY_DISBANDED.paysBreachRefund());
        assertFalse(TerminationReason.HOST_BANKRUPT.paysBreachRefund());
        assertFalse(TerminationReason.LOYALTY_CONFLICT.paysBreachRefund());

        assertTrue(TerminationReason.DURATION_ELAPSED.movesReputation());
        assertTrue(TerminationReason.SLOTS_BREACH.movesReputation());
        assertTrue(TerminationReason.COMPANY_DISBANDED.movesReputation());
        assertTrue(TerminationReason.HOST_BANKRUPT.movesReputation());
        assertFalse(TerminationReason.LOYALTY_CONFLICT.movesReputation());

        assertEquals(ContractStatus.COMPLETED, TerminationReason.DURATION_ELAPSED.getOutcome());
        assertEquals(ContractStatus.BREACHED, TerminationReason.SLOTS_BREACH.getOutcome());
        assertEquals(ContractStatus.BREACHED, TerminationReason.COMPANY_DISBANDED.getOutcome());
        assertEquals(ContractStatus.TERMINATED, TerminationReason.HOST_BANKRUPT.getOutcome());
        assertEquals(ContractStatus.TERMINATED, TerminationReason.LOYALTY_CONFLICT.getOutcome());
    }

    /* =====================================================
     * The daily sweep
     * ===================================================== */

    @Test
    void theDailySweepAgesActiveContractsAndClosesTheElapsedOnes() {
        MercenaryContract running = active(1, System.currentTimeMillis());
        MercenaryContract done = active(1, System.currentTimeMillis() - 8 * DAY);

        ContractTerminationService.tickDay();

        assertEquals(1, running.getDaysServed());
        assertEquals(ContractStatus.ACTIVE, running.getStatus());
        assertEquals(1, done.getDaysServed(), "the last day of service is still counted");
        assertEquals(ContractStatus.COMPLETED, done.getStatus());
    }

    @Test
    void theDailySweepClosesABankruptHost() {
        MercenaryContract contract = active(1, System.currentTimeMillis());
        when(fixture.host.guild.isBankrupt()).thenReturn(true);

        ContractTerminationService.tickDay();

        assertEquals(ContractStatus.TERMINATED, contract.getStatus());
        assertEquals(1, contract.getDaysServed());
    }

    /* =====================================================
     * Guards
     * ===================================================== */

    @Test
    void terminateIgnoresNothingAndUnknownReasons() {
        assertEquals(0.0, ContractTerminationService.terminate(null, TerminationReason.SLOTS_BREACH));
        MercenaryContract contract = active(1, System.currentTimeMillis());
        assertEquals(0.0, ContractTerminationService.terminate(contract, null));
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
    }

    @Test
    void anOfferIsNeverTerminatedByAnyOfTheFourTriggers() {
        MercenaryContract offer = fixture.offer(
                ContractFixture.validTerms(4), System.currentTimeMillis() - 8 * DAY);

        assertTrue(ContractTerminationService.completeElapsed(fixture.company).isEmpty());
        assertTrue(ContractTerminationService.checkSlotCommitments(fixture.company).isEmpty());
        when(fixture.host.guild.isBankrupt()).thenReturn(true);
        assertTrue(ContractTerminationService.checkBankruptcy(fixture.company).isEmpty());
        assertEquals(ContractStatus.OFFERED, offer.getStatus());
    }

    /* =====================================================
     * Helpers
     * ===================================================== */

    private War warAgainstHost() {
        Side opposing = mock(Side.class);
        when(opposing.isParticipating(host)).thenReturn(true);
        when(opposing.getLeader()).thenReturn(host);
        when(opposing.getMainParticipants()).thenReturn(new ArrayList<>());
        War war = mock(War.class);
        when(war.isParticipating(fixture.hirer)).thenReturn(true);
        when(war.getOppositeSide(fixture.hirer)).thenReturn(opposing);
        return war;
    }

    private static final class RecordingSeam implements ContractReputationSeam {
        private int calls;
        private TerminationReason lastReason;

        @Override
        public void onTermination(MercenaryContract contract, TerminationReason reason) {
            calls++;
            lastReason = reason;
        }
    }
}
