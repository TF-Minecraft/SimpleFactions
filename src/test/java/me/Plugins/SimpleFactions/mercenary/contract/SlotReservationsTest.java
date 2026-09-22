package me.Plugins.SimpleFactions.mercenary.contract;

import static me.Plugins.SimpleFactions.mercenary.contract.ContractFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.mercenary.MercenaryResult;

/**
 * The reservation calendar. The whole point is that capacity is a question about
 * a window, never a running total, so back-to-back selling stays legal.
 */
class SlotReservationsTest {
    private ContractFixture fixture;
    private long day0;

    @BeforeEach
    void setUp() {
        fixture = ContractFixture.formed(4);
        day0 = System.currentTimeMillis();
    }

    @AfterEach
    void tearDown() {
        ContractFixture.tearDown();
    }

    private MercenaryContract active(int slots, int startDay, int days) {
        MercenaryContract contract = fixture.offer(
                new ContractTerms(slots, 50.0, 10.0, days, 50.0, 500.0),
                day0 + startDay * DAY);
        contract.activate();
        return contract;
    }

    private MercenaryResult canPromise(int slots, int startDay, int days) {
        long from = day0 + startDay * DAY;
        return SlotReservations.canPromise(fixture.company, slots, from, from + days * DAY);
    }

    @Test
    void anEmptyCalendarOffersEverySlot() {
        assertEquals(0, SlotReservations.promised(fixture.company, day0, day0 + 7 * DAY));
        assertEquals(4, SlotReservations.remaining(fixture.company, day0, day0 + 7 * DAY));
    }

    @Test
    void backToBackWindowsMaySellEverySlotTwice() {
        active(4, 0, 7);
        assertTrue(canPromise(4, 7, 7).ok(), "a window starting as the last one ends is free");
        assertEquals(4, SlotReservations.remaining(
                fixture.company, day0 + 7 * DAY, day0 + 14 * DAY));
    }

    @Test
    void anIdenticalOverlappingWindowIsRefused() {
        active(4, 0, 7);
        MercenaryResult result = canPromise(1, 0, 7);
        assertFalse(result.ok());
        assertTrue(result.message().contains("only 0 of its 4 slots free"));
    }

    @Test
    void partialOverlapStillCountsTheWholePromise() {
        active(3, 0, 7);
        // Days 5 to 12 overlap the tail of the first contract, so only one slot is free.
        assertEquals(3, SlotReservations.promised(
                fixture.company, day0 + 5 * DAY, day0 + 12 * DAY));
        assertEquals(1, SlotReservations.remaining(
                fixture.company, day0 + 5 * DAY, day0 + 12 * DAY));
        assertTrue(canPromise(1, 5, 7).ok());
        assertFalse(canPromise(2, 5, 7).ok());
    }

    @Test
    void promisesFromSeveralOverlappingContractsAddUp() {
        active(1, 0, 10);
        active(2, 2, 4);
        assertEquals(3, SlotReservations.promised(fixture.company, day0 + 3 * DAY, day0 + 4 * DAY));
        assertTrue(canPromise(1, 3, 1).ok());
        assertFalse(canPromise(2, 3, 1).ok());
    }

    @Test
    void anEnclosedWindowIsStillAnOverlap() {
        active(4, 0, 10);
        assertFalse(canPromise(1, 3, 2).ok());
    }

    /** An unaccepted offer holds the slots so a company cannot sell them twice over. */
    @Test
    void anOfferHoldsASoftReservation() {
        fixture.offer(new ContractTerms(4, 50.0, 10.0, 7, 50.0, 500.0), day0);
        assertEquals(4, SlotReservations.promised(fixture.company, day0, day0 + 7 * DAY));
        assertFalse(canPromise(1, 0, 7).ok());
    }

    @Test
    void decliningOrExpiringAnOfferReleasesTheHold() {
        MercenaryContract offer =
                fixture.offer(new ContractTerms(4, 50.0, 10.0, 7, 50.0, 500.0), day0);
        assertFalse(canPromise(1, 0, 7).ok());

        offer.finish(ContractStatus.TERMINATED);

        assertEquals(0, SlotReservations.promised(fixture.company, day0, day0 + 7 * DAY));
        assertTrue(canPromise(4, 0, 7).ok());
    }

    @Test
    void aFinishedContractNoLongerReservesAnything() {
        MercenaryContract contract = active(4, 0, 7);
        contract.finish(ContractStatus.COMPLETED);
        assertEquals(0, SlotReservations.promised(fixture.company, day0, day0 + 7 * DAY));
        assertTrue(canPromise(4, 0, 7).ok());
    }

    @Test
    void capacityMatchesTheAcceptedContracts() {
        active(1, 0, 7);
        active(2, 0, 7);
        assertEquals(3, SlotReservations.promised(fixture.company, day0, day0 + 7 * DAY));
        assertEquals(1, SlotReservations.remaining(fixture.company, day0, day0 + 7 * DAY));
        assertEquals(1, fixture.company.getSlots()
                - SlotReservations.promised(fixture.company, day0, day0 + 7 * DAY));
    }

    @Test
    void theValidatorRefusesAnOverPromiseWithTheCapacityMessage() {
        active(4, 0, 7);
        MercenaryResult result = ContractValidator.validate(
                ContractFixture.validTerms(1), fixture.company, day0);
        assertFalse(result.ok());
        assertTrue(result.message().contains("free for those dates"));
    }
}
