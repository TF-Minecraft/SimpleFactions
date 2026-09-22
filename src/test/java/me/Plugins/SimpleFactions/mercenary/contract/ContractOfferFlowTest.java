package me.Plugins.SimpleFactions.mercenary.contract;

import static me.Plugins.SimpleFactions.mercenary.contract.ContractFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.government.Government;

/** The offer flow: who may sign, what a hold costs, and how a hold is released. */
class ContractOfferFlowTest {
    private ContractFixture fixture;
    private ContractHandler handler;

    @BeforeEach
    void setUp() {
        fixture = ContractFixture.formed(4);
        handler = fixture.company.getContractHandler();
        when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
        when(MercenaryLoyalty.hostFaction(fixture.company).getRelations())
                .thenReturn(new HashMap<>());
        government("Chancellor");
    }

    @AfterEach
    void tearDown() {
        ContractFixture.tearDown();
    }

    private void government(String... members) {
        Government gov = mock(Government.class);
        for (String member : members) {
            when(gov.isCouncilMember(member)).thenReturn(true);
        }
        when(fixture.hirer.getGovernment()).thenReturn(gov);
    }

    private MercenaryContract offered(int slots) {
        ContractHandler.Offer offer = handler.offer(fixture.hirer, ContractFixture.validTerms(slots));
        assertTrue(offer.ok(), offer.message());
        return offer.contract();
    }

    /* =====================================================
     * Offering
     * ===================================================== */

    @Test
    void anOfferIsWrittenAndImmediatelyHoldsItsSlots() {
        MercenaryContract contract = offered(3);
        long now = contract.getIssueDate();

        assertEquals(ContractStatus.OFFERED, contract.getStatus());
        assertEquals(3, SlotReservations.promised(fixture.company, now, now + 7 * DAY));
        assertEquals(1, SlotReservations.remaining(fixture.company, now, now + 7 * DAY));
    }

    @Test
    void anInvalidOfferIsNeverWritten() {
        ContractHandler.Offer offer = handler.offer(
                fixture.hirer, new ContractTerms(1, 10.0, 10.0, 7, 50.0, 500.0));
        assertFalse(offer.ok());
        assertNull(offer.contract());
        assertTrue(handler.getAll().isEmpty());
        assertTrue(offer.message().contains("per battle"));
    }

    @Test
    void twoOverlappingOffersCannotBothBeWritten() {
        offered(4);
        ContractHandler.Offer second = handler.offer(fixture.hirer, ContractFixture.validTerms(1));
        assertFalse(second.ok());
        assertTrue(second.message().contains("free for those dates"));
        assertEquals(1, handler.getAll().size());
    }

    /* =====================================================
     * Accepting
     * ===================================================== */

    @Test
    void acceptingMakesTheContractActiveAndKeepsTheHold() {
        MercenaryContract contract = offered(2);
        long now = contract.getIssueDate();

        MercenaryResult result = handler.accept(contract.getId(), fixture.hirer, "Chancellor");
        assertTrue(result.ok(), result.message());
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertEquals(2, SlotReservations.promised(fixture.company, now, now + 7 * DAY));
        assertEquals(List.of(contract), handler.getActive());
    }

    @Test
    void onlyAGovernmentMemberMaySign() {
        MercenaryContract contract = offered(1);
        MercenaryResult result = handler.accept(contract.getId(), fixture.hirer, "Farmhand");
        assertFalse(result.ok());
        assertTrue(result.message().contains("government"));
        assertEquals(ContractStatus.OFFERED, contract.getStatus());
    }

    @Test
    void anotherFactionCannotSignSomeoneElsesOffer() {
        MercenaryContract contract = offered(1);
        me.Plugins.SimpleFactions.Objects.Faction other = ContractFixture.faction("other");
        MercenaryResult result = handler.accept(contract.getId(), other, "Chancellor");
        assertFalse(result.ok());
        assertTrue(result.message().contains("not made to your faction"));
    }

    @Test
    void anOfferCannotBeAcceptedTwice() {
        MercenaryContract contract = offered(1);
        assertTrue(handler.accept(contract.getId(), fixture.hirer, "Chancellor").ok());
        MercenaryResult again = handler.accept(contract.getId(), fixture.hirer, "Chancellor");
        assertFalse(again.ok());
        assertTrue(again.message().contains("no longer open"));
    }

    @Test
    void acceptingAnUnknownContractIsRefused() {
        assertFalse(handler.accept("nonsense", fixture.hirer, "Chancellor").ok());
    }

    /* =====================================================
     * Declining and lapsing
     * ===================================================== */

    @Test
    void decliningReleasesTheHold() {
        MercenaryContract contract = offered(4);
        long now = contract.getIssueDate();

        assertTrue(handler.decline(contract.getId()).ok());
        assertEquals(ContractStatus.TERMINATED, contract.getStatus());
        assertEquals(0, SlotReservations.promised(fixture.company, now, now + 7 * DAY));
        assertTrue(handler.offer(fixture.hirer, ContractFixture.validTerms(4)).ok());
    }

    @Test
    void anActiveContractCannotBeDeclined() {
        MercenaryContract contract = offered(1);
        handler.accept(contract.getId(), fixture.hirer, "Chancellor");
        assertFalse(handler.decline(contract.getId()).ok());
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
    }

    @Test
    void anOfferLapsesAfterADayAndReleasesItsHold() {
        long yesterday = System.currentTimeMillis() - 2 * DAY;
        ContractHandler.Offer offer = handler.offer(
                fixture.hirer, ContractKind.MERCENARY, ContractFixture.validTerms(4), yesterday);
        assertTrue(offer.ok(), offer.message());
        MercenaryContract stale = offer.contract();
        assertTrue(stale.isOfferExpired());

        assertEquals(List.of(stale), handler.tickExpiry());
        assertEquals(ContractStatus.TERMINATED, stale.getStatus());
        assertEquals(0, SlotReservations.promised(
                fixture.company, yesterday, yesterday + 7 * DAY));
    }

    @Test
    void aLapsedOfferCannotBeAccepted() {
        long yesterday = System.currentTimeMillis() - 2 * DAY;
        MercenaryContract stale = handler.offer(
                fixture.hirer, ContractKind.MERCENARY,
                ContractFixture.validTerms(1), yesterday).contract();

        MercenaryResult result = handler.accept(stale.getId(), fixture.hirer, "Chancellor");
        assertFalse(result.ok());
        assertTrue(result.message().contains("lapsed"));
        assertEquals(ContractStatus.TERMINATED, stale.getStatus());
    }

    @Test
    void anActiveContractIsNeverSweptByTheExpiryTick() {
        MercenaryContract contract = offered(1);
        handler.accept(contract.getId(), fixture.hirer, "Chancellor");
        assertTrue(handler.tickExpiry().isEmpty());
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
    }

    /* =====================================================
     * Lookups
     * ===================================================== */

    @Test
    void aHirerFindsItsContractsByScanningTheCompany() {
        MercenaryContract contract = offered(1);
        handler.accept(contract.getId(), fixture.hirer, "Chancellor");

        assertEquals(List.of(contract), handler.getForFaction(fixture.hirer));
        assertTrue(handler.hasActiveFor(fixture.hirer));
        assertFalse(handler.hasActiveFor(ContractFixture.faction("stranger")));
        assertNotNull(handler.getById(contract.getId()));
    }
}
