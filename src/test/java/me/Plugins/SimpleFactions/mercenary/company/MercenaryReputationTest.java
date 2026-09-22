package me.Plugins.SimpleFactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.mercenary.contract.MercenaryContract;
import me.Plugins.SimpleFactions.mercenary.contract.TerminationReason;

/**
 * Reputation is the credit score system for violence: weighted factors clamped to
 * 0..1 against a per-event maximum, so severity scales rather than snapping.
 */
class MercenaryReputationTest {

    private final MercenaryReputationSeam seam = new MercenaryReputationSeam();

    @BeforeEach
    void setUp() {
        MercenaryEligibility.reset();
        CompanyFixture.clearCompanyUpgrades();
    }

    @AfterEach
    void tearDown() {
        MercenaryEligibility.reset();
        CompanyFixture.clearCompanyUpgrades();
    }

    /* =====================================================
     * The score itself
     * ===================================================== */

    @Test
    void aFreshCompanyStartsAtFifty() {
        assertEquals(50, company().getReputation(), "the same default the loan credit score uses");
    }

    @Test
    void theScoreClampsAtBothEnds() {
        MercenaryCompany company = company();

        company.changeReputation(-500);
        assertEquals(0, company.getReputation());

        company.changeReputation(500);
        assertEquals(100, company.getReputation());
    }

    /* =====================================================
     * Events
     * ===================================================== */

    @Test
    void aCleanCompletionRaisesReputation() {
        int bonus = MercenaryReputationCalculator.calculateCompletionBonus(contract(10, 14, true));
        assertTrue(bonus > 0, "a long, large, spotless job should be worth something");
    }

    @Test
    void aCompletionWithAMissedBattleEarnsNothingExtra() {
        assertEquals(0, MercenaryReputationCalculator.calculateCompletionBonus(contract(10, 14, false)));
    }

    @Test
    void aBigJobCompletedBeatsASmallOne() {
        int big = MercenaryReputationCalculator.calculateCompletionBonus(contract(10, 14, true));
        int small = MercenaryReputationCalculator.calculateCompletionBonus(contract(1, 1, true));
        assertTrue(big > small, "reputation should reward the harder contract");
    }

    @Test
    void halfTheSlotsAbsentCostsAboutHalfTheMaximum() {
        MercenaryContract contract = contract(4, 7, false);

        assertEquals(-5, MercenaryReputationCalculator.calculateAbsencePenalty(contract, 2));
        assertEquals(-10, MercenaryReputationCalculator.calculateAbsencePenalty(contract, 4));
        assertEquals(0, MercenaryReputationCalculator.calculateAbsencePenalty(contract, 0));
    }

    @Test
    void anEarlyBreachHurtsMoreThanALateOne() {
        int early = MercenaryReputationCalculator.calculateBreachPenalty(contract(2, 14, true, 1));
        int late = MercenaryReputationCalculator.calculateBreachPenalty(contract(2, 14, true, 13));

        assertTrue(early < late, "walking out on day one is close to fraud");
        assertTrue(early <= -30, "and it should be a large hit: " + early);
    }

    @Test
    void bankruptcyTakesTheLargeHitToo() {
        assertTrue(MercenaryReputationCalculator.calculateBankruptcyPenalty(contract(2, 14, true, 1)) <= -30);
    }

    /* =====================================================
     * The seam
     * ===================================================== */

    @Test
    void aLoyaltyConflictChangesNothing() {
        MercenaryCompany company = company();
        MercenaryContract contract = contract(2, 14, true, 1);
        when(contract.getCompany()).thenReturn(company);

        seam.onTermination(contract, TerminationReason.LOYALTY_CONFLICT);

        assertEquals(50, company.getReputation(), "neither party caused it");
    }

    @Test
    void theSeamAppliesTheRightEventForEachReason() {
        MercenaryContract contract = contract(2, 14, true, 1);

        assertTrue(afterTermination(contract, TerminationReason.DURATION_ELAPSED) > 50);
        assertTrue(afterTermination(contract, TerminationReason.SLOTS_BREACH) < 50);
        assertTrue(afterTermination(contract, TerminationReason.COMPANY_DISBANDED) < 50);
        assertTrue(afterTermination(contract, TerminationReason.HOST_BANKRUPT) < 50);
    }

    @Test
    void aScoreAtTheFloorCannotBePushedFurtherByARepeatedEvent() {
        MercenaryCompany company = company();
        company.changeReputation(-45);
        MercenaryContract contract = contract(2, 14, true, 1);
        when(contract.getCompany()).thenReturn(company);

        seam.onTermination(contract, TerminationReason.SLOTS_BREACH);
        seam.onTermination(contract, TerminationReason.SLOTS_BREACH);

        assertEquals(0, company.getReputation());
    }

    /* =====================================================
     * Display
     * ===================================================== */

    @Test
    void theBandChangesAtItsBoundaries() {
        assertEquals("Notorious", MercenaryReputationCalculator.band(34));
        assertEquals("Unproven", MercenaryReputationCalculator.band(35));
        assertEquals("Unproven", MercenaryReputationCalculator.band(59));
        assertEquals("Reliable", MercenaryReputationCalculator.band(60));
        assertEquals("Reliable", MercenaryReputationCalculator.band(79));
        assertEquals("Trusted", MercenaryReputationCalculator.band(80));
    }

    @Test
    void theDisplayCarriesTheNumberAndTheBand() {
        String display = MercenaryReputationCalculator.display(50);
        assertTrue(display.contains("50/100"), display);
        assertTrue(display.contains("Unproven"), display);
    }

    /* =====================================================
     * Harness
     * ===================================================== */

    private int afterTermination(MercenaryContract contract, TerminationReason reason) {
        MercenaryCompany company = company();
        when(contract.getCompany()).thenReturn(company);
        seam.onTermination(contract, reason);
        return company.getReputation();
    }

    private MercenaryCompany company() {
        return new MercenaryCompany(null, "Hired Blades", null, 0);
    }

    private MercenaryContract contract(int slots, int days, boolean clean) {
        return contract(slots, days, clean, days);
    }

    private MercenaryContract contract(int slots, int days, boolean clean, int daysServed) {
        MercenaryContract contract = mock(MercenaryContract.class);
        when(contract.getSlots()).thenReturn(slots);
        when(contract.getDurationDays()).thenReturn(days);
        when(contract.getDaysServed()).thenReturn(daysServed);
        when(contract.hasCleanAttendance()).thenReturn(clean);
        return contract;
    }
}
