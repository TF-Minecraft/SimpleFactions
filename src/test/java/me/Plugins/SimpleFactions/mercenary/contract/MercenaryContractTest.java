package me.Plugins.SimpleFactions.mercenary.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Database.MercenaryCompanyData;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.company.CompanyFixture;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/** The contract object: its validation rules, its figures, and its round trip. */
class MercenaryContractTest {
    private ContractFixture fixture;
    private long now;

    @BeforeEach
    void setUp() {
        fixture = ContractFixture.formed(4);
        now = System.currentTimeMillis();
    }

    @AfterEach
    void tearDown() {
        ContractFixture.tearDown();
    }

    private MercenaryResult validate(ContractTerms terms) {
        return ContractValidator.validate(terms, fixture.company, now);
    }

    /* =====================================================
     * Validation, one message per rule
     * ===================================================== */

    @Test
    void validTermsAreAccepted() {
        assertTrue(validate(ContractFixture.validTerms(2)).ok());
    }

    @Test
    void priceBelowEitherMinimumIsRefusedByItsOwnRule() {
        MercenaryResult battle = validate(new ContractTerms(1, 49.99, 10.0, 7, 50.0, 500.0));
        assertFalse(battle.ok());
        assertTrue(battle.message().contains("per battle"));

        MercenaryResult day = validate(new ContractTerms(1, 50.0, 9.99, 7, 50.0, 500.0));
        assertFalse(day.ok());
        assertTrue(day.message().contains("per day"));
    }

    @Test
    void pricesExactlyAtTheMinimumAreAccepted() {
        assertTrue(validate(new ContractTerms(1,
                Cache.mercenaryMinPricePerBattle,
                Cache.mercenaryMinPricePerDay,
                7,
                Cache.mercenaryMinPricePerBattle,
                500.0)).ok());
    }

    @Test
    void durationOutsideOneToFourteenDaysIsRefused() {
        MercenaryResult tooShort = validate(new ContractTerms(1, 50.0, 10.0, 0, 50.0, 500.0));
        assertFalse(tooShort.ok());
        assertTrue(tooShort.message().contains("at least one day"));

        MercenaryResult tooLong = validate(new ContractTerms(1, 50.0, 10.0, 15, 50.0, 500.0));
        assertFalse(tooLong.ok());
        assertTrue(tooLong.message().contains("14 days"));

        assertTrue(validate(new ContractTerms(1, 50.0, 10.0, 1, 50.0, 500.0)).ok());
        assertTrue(validate(new ContractTerms(1, 50.0, 10.0, 14, 50.0, 500.0)).ok());
    }

    /**
     * The rule that keeps no-showing unprofitable. A refund under the battle price
     * would pay a company more per head for staying home than for fighting.
     */
    @Test
    void absenceRefundBelowTheBattlePriceIsRefused() {
        MercenaryResult under = validate(new ContractTerms(1, 60.0, 10.0, 7, 59.99, 500.0));
        assertFalse(under.ok());
        assertTrue(under.message().contains("absence refund"));
        assertTrue(under.message().contains("at least"));
    }

    @Test
    void absenceRefundEqualToTheBattlePriceIsAccepted() {
        assertTrue(validate(new ContractTerms(1, 60.0, 10.0, 7, 60.0, 500.0)).ok());
        assertTrue(validate(new ContractTerms(1, 60.0, 10.0, 7, 75.0, 500.0)).ok());
    }

    @Test
    void negativeBreachRefundIsRefused() {
        MercenaryResult result = validate(new ContractTerms(1, 50.0, 10.0, 7, 50.0, -1.0));
        assertFalse(result.ok());
        assertTrue(result.message().contains("breach refund"));
    }

    @Test
    void slotsOutsideTheCompanySizeAreRefused() {
        MercenaryResult none = validate(new ContractTerms(0, 50.0, 10.0, 7, 50.0, 500.0));
        assertFalse(none.ok());
        assertTrue(none.message().contains("at least one slot"));

        MercenaryResult tooMany = validate(new ContractTerms(5, 50.0, 10.0, 7, 50.0, 500.0));
        assertFalse(tooMany.ok());
        assertTrue(tooMany.message().contains("only has 4 slots"));

        assertTrue(validate(ContractFixture.validTerms(4)).ok());
    }

    @Test
    void anUnformedCompanyCannotBeHired() {
        ContractFixture.tearDown();
        CompanyFixture.installMercenaryPrototype();
        CompanyFixture.installCompanyUpgrades();
        CompanyFixture host = new CompanyFixture(10000);
        me.Plugins.SimpleFactions.mercenary.company.MercenaryCompanyService
                .requestFormation(host.guild, host.leader(), "Still Forming");

        MercenaryResult result = ContractValidator.validate(
                ContractFixture.validTerms(1), host.company(), now);
        assertFalse(result.ok());
        assertTrue(result.message().contains("not open for hire"));
    }

    /* =====================================================
     * Figures
     * ===================================================== */

    @Test
    void figuresAreAbsoluteAndScaleWithSlots() {
        MercenaryContract contract = fixture.offer(ContractFixture.validTerms(3), now);

        assertEquals(3, contract.getSlots());
        assertEquals(30.0, contract.getDailyPrice());
        assertEquals(150.0, contract.getBattlePrice());
        assertEquals(7, contract.getDurationDays());
        assertEquals(now + 7 * ContractFixture.DAY, contract.getDueDate());
        assertEquals(ContractKind.MERCENARY, contract.getKind());
        assertEquals(50, contract.getReputationAtSigning());
        assertTrue(contract.isHirer(fixture.hirer));
    }

    @Test
    void servedDaysAreOwedWhateverEndsTheContract() {
        MercenaryContract contract = fixture.offer(ContractFixture.validTerms(2), now);
        contract.activate();
        contract.addDayServed();
        contract.addDayServed();
        contract.addDayServed();

        assertEquals(3, contract.getDaysServed());
        assertEquals(60.0, contract.getServedDaysOwed());

        contract.finish(ContractStatus.BREACHED);
        assertEquals(60.0, contract.getServedDaysOwed());
    }

    /* =====================================================
     * Status transitions are one-way
     * ===================================================== */

    @Test
    void onlyAnOfferMayActivate() {
        MercenaryContract contract = fixture.offer(ContractFixture.validTerms(1), now);
        assertTrue(contract.isOffered());
        assertTrue(contract.activate());
        assertTrue(contract.isActive());
        assertFalse(contract.activate());
    }

    @Test
    void aFinishedContractCannotBeRevivedOrFinishedTwice() {
        MercenaryContract contract = fixture.offer(ContractFixture.validTerms(1), now);
        contract.activate();

        assertTrue(contract.finish(ContractStatus.BREACHED));
        assertEquals(ContractStatus.BREACHED, contract.getStatus());
        assertFalse(contract.finish(ContractStatus.BREACHED));
        assertFalse(contract.finish(ContractStatus.COMPLETED));
        assertFalse(contract.activate());
        assertFalse(contract.reservesSlots());
    }

    @Test
    void finishRejectsAnUnfinishedOutcome() {
        MercenaryContract contract = fixture.offer(ContractFixture.validTerms(1), now);
        assertFalse(contract.finish(ContractStatus.ACTIVE));
        assertFalse(contract.finish(ContractStatus.OFFERED));
        assertFalse(contract.finish(null));
        assertEquals(ContractStatus.OFFERED, contract.getStatus());
    }

    /* =====================================================
     * Persistence
     * ===================================================== */

    @Test
    void roundTripPreservesEveryFigure() {
        MercenaryContract original = fixture.offer(
                new ContractTerms(3, 75.0, 12.5, 9, 80.0, 450.0), now);
        original.activate();
        original.addDayServed();
        original.addDayServed();
        original.markAttendanceFailure();

        MercenaryCompanyData data = fixture.company.serialize();
        assertEquals(1, data.contracts.size());

        MercenaryCompany reloaded = new MercenaryCompany(
                fixture.host.guild, data, CompanyFixture.companyRegiment());
        MercenaryContract copy = reloaded.getContractHandler().getById(original.getId());

        assertNotNull(copy);
        assertEquals(original.getId(), copy.getId());
        assertEquals(original.getHirerFactionId(), copy.getHirerFactionId());
        assertEquals(ContractKind.MERCENARY, copy.getKind());
        assertEquals(3, copy.getSlots());
        assertEquals(75.0, copy.getPricePerSlotPerBattle());
        assertEquals(12.5, copy.getPricePerSlotPerDay());
        assertEquals(9, copy.getDurationDays());
        assertEquals(80.0, copy.getAbsenceRefundPerSlotPerBattle());
        assertEquals(450.0, copy.getBreachRefund());
        assertEquals(original.getIssueDate(), copy.getIssueDate());
        assertEquals(original.getDueDate(), copy.getDueDate());
        assertEquals(original.getReputationAtSigning(), copy.getReputationAtSigning());
        assertEquals(ContractStatus.ACTIVE, copy.getStatus());
        assertEquals(2, copy.getDaysServed());
        assertFalse(copy.hasCleanAttendance());
    }

    @Test
    void aFinishedContractSurvivesTheRoundTripFinished() {
        MercenaryContract original = fixture.offer(ContractFixture.validTerms(1), now);
        original.activate();
        original.finish(ContractStatus.COMPLETED);

        MercenaryCompany reloaded = new MercenaryCompany(
                fixture.host.guild, fixture.company.serialize(), CompanyFixture.companyRegiment());
        MercenaryContract copy = reloaded.getContractHandler().getById(original.getId());

        assertEquals(ContractStatus.COMPLETED, copy.getStatus());
        assertFalse(copy.finish(ContractStatus.BREACHED));
    }
}
