package net.tfminecraft.simplefactions.mercenary.contract;

import static net.tfminecraft.simplefactions.mercenary.contract.ContractFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.managers.inventory.ContractCreator;
import net.tfminecraft.simplefactions.mercenary.MercenaryResult;
import net.tfminecraft.simplefactions.mercenary.company.CompanyFixture;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;

/**
 * A signed contract keeps its prices and end date. Only the slot count moves,
 * and only after the hiring government accepts.
 */
class SlotAmendmentTest {
    private ContractFixture fixture;
    private ContractHandler handler;
    private long now;

    @BeforeEach
    void setUp() {
        fixture = ContractFixture.formed(4);
        handler = fixture.company.getContractHandler();
        now = System.currentTimeMillis();
        when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
        when(MercenaryLoyalty.hostFaction(fixture.company).getRelations()).thenReturn(new HashMap<>());
        Government gov = mock(Government.class);
        when(gov.isCouncilMember("Chancellor")).thenReturn(true);
        when(fixture.hirer.getGovernment()).thenReturn(gov);
        Cache.baseYear = "322 AE";
    }

    @AfterEach
    void tearDown() {
        ContractBattleGate.setFighting(null);
        ContractFixture.tearDown();
    }

    private MercenaryContract active(int slots) {
        MercenaryContract contract = fixture.offer(ContractFixture.validTerms(slots), now);
        assertTrue(contract.activate());
        return contract;
    }

    private MercenaryResult propose(MercenaryContract contract, int slots) {
        return handler.proposeSlots(contract.getId(), fixture.host.leader(), slots, now);
    }

    /* =====================================================
     * Offering a change
     * ===================================================== */

    @Test
    void anIncreaseHoldsTheExtraSlotsUntilTheHirerAccepts() {
        MercenaryContract contract = active(2);
        MercenaryResult offered = propose(contract, 4);
        assertTrue(offered.ok(), offered.message());

        assertEquals(2, contract.getSlots(), "the signed count stays until acceptance");
        assertEquals(4, contract.heldSlots(now));
        assertEquals(4, SlotReservations.promised(fixture.company, now, now + 7 * DAY));
        assertFalse(SlotReservations.canPromise(fixture.company, 1, now, now + 7 * DAY).ok());
    }

    @Test
    void aDecreaseHoldsNothingUntilTheHirerAccepts() {
        MercenaryContract contract = active(3);
        assertTrue(propose(contract, 1).ok());

        assertEquals(3, contract.heldSlots(now));
        assertEquals(1, SlotReservations.remaining(fixture.company, now, now + 7 * DAY));
        ContractHandler.Offer other = handler.offer(
                fixture.hirer, ContractKind.MERCENARY, ContractFixture.validTerms(1), now);
        assertTrue(other.ok(), other.message());
    }

    @Test
    void theSameCountASecondOfferAndAStrangerAreRefused() {
        MercenaryContract contract = active(2);
        MercenaryResult same = propose(contract, 2);
        assertFalse(same.ok());
        assertTrue(same.message().contains("already hires"));

        assertTrue(propose(contract, 3).ok());
        MercenaryResult again = propose(contract, 4);
        assertFalse(again.ok());
        assertTrue(again.message().contains("already waiting"));

        MercenaryResult stranger = handler.proposeSlots(contract.getId(), "Stranger", 1, now);
        assertFalse(stranger.ok());
        assertTrue(stranger.message().contains("guild leader"));
    }

    @Test
    void anOfferThatIsNotYetActiveCannotChangeSize() {
        MercenaryContract contract = fixture.offer(ContractFixture.validTerms(2), now);
        MercenaryResult result = propose(contract, 3);
        assertFalse(result.ok());
        assertTrue(result.message().contains("active"));
    }

    @Test
    void zeroSlotsAndMoreThanTheCompanyHasAreRefused() {
        MercenaryContract contract = active(2);
        MercenaryResult zero = propose(contract, 0);
        assertFalse(zero.ok());
        assertTrue(zero.message().contains("at least one"));

        MercenaryResult tooMany = propose(contract, 5);
        assertFalse(tooMany.ok());
        assertTrue(tooMany.message().contains("free"));
        assertNull(contract.getPendingSlots(now));
    }

    /* =====================================================
     * Accepting
     * ===================================================== */

    @Test
    void acceptingAnIncreaseChangesTheCountAndLeavesTheRestOfTheTerms() {
        MercenaryContract contract = active(2);
        contract.addDayServed();
        contract.addDayServed();
        long due = contract.getDueDate();
        assertTrue(propose(contract, 4).ok());

        MercenaryResult accepted = handler.acceptSlots(contract.getId(), fixture.hirer, "Chancellor", now);
        assertTrue(accepted.ok(), accepted.message());

        assertEquals(4, contract.getSlots());
        assertEquals(40.0, contract.getDailyPrice());
        assertEquals(200.0, contract.getBattlePrice());
        assertEquals(10.0, contract.getPricePerSlotPerDay());
        assertEquals(due, contract.getDueDate());
        assertEquals(40.0, contract.getServedDaysOwed(), "two days stay priced at the old 20d a day");
        assertNull(contract.getPendingSlots(now));

        contract.addDayServed();
        assertEquals(80.0, contract.getServedDaysOwed(), "the next day uses the new daily price");
    }

    @Test
    void acceptingADecreaseFreesTheSlotsForSomeoneElse() {
        MercenaryContract contract = active(3);
        assertTrue(propose(contract, 1).ok());
        assertTrue(handler.acceptSlots(contract.getId(), fixture.hirer, "Chancellor", now).ok());

        assertEquals(1, contract.getSlots());
        assertEquals(10.0, contract.getDailyPrice());
        assertEquals(3, SlotReservations.remaining(fixture.company, now, now + 7 * DAY));
    }

    @Test
    void aBattleInProgressKeepsTheOldCount() {
        MercenaryContract contract = active(2);
        assertTrue(propose(contract, 4).ok());
        ContractBattleGate.setFighting(hirer -> true);

        MercenaryResult blocked = handler.acceptSlots(contract.getId(), fixture.hirer, "Chancellor", now);
        assertFalse(blocked.ok());
        assertTrue(blocked.message().contains("battle"));
        assertEquals(2, contract.getSlots());
        assertEquals(4, contract.getPendingSlots(now));

        ContractBattleGate.setFighting(hirer -> false);
        assertTrue(handler.acceptSlots(contract.getId(), fixture.hirer, "Chancellor", now).ok());
        assertEquals(4, contract.getSlots());
    }

    @Test
    void onlyTheHiringGovernmentMayAcceptOrDecline() {
        MercenaryContract contract = active(2);
        assertTrue(propose(contract, 3).ok());

        MercenaryResult stranger = handler.acceptSlots(contract.getId(), fixture.hirer, "Stranger", now);
        assertFalse(stranger.ok());
        assertTrue(stranger.message().contains("government"));
        assertEquals(2, contract.getSlots());

        MercenaryResult declined = handler.declineSlots(contract.getId(), fixture.hirer, "Stranger", now);
        assertFalse(declined.ok());
        assertEquals(3, contract.heldSlots(now));
    }

    @Test
    void decliningOrWithdrawingReleasesTheExtraHold() {
        MercenaryContract contract = active(2);
        assertTrue(propose(contract, 4).ok());
        assertTrue(handler.declineSlots(contract.getId(), fixture.hirer, "Chancellor", now).ok());
        assertEquals(2, contract.getSlots());
        assertEquals(2, SlotReservations.promised(fixture.company, now, now + 7 * DAY));

        assertTrue(propose(contract, 4).ok());
        assertTrue(handler.withdrawSlots(contract.getId(), fixture.host.leader(), now).ok());
        assertEquals(2, SlotReservations.remaining(fixture.company, now, now + 7 * DAY));
    }

    @Test
    void aSlotChangeLapsesAfterADay() {
        MercenaryContract contract = active(2);
        assertTrue(propose(contract, 4).ok());

        assertTrue(handler.tickAmendments(now + MercenaryContract.OFFER_WINDOW_MS - 1).isEmpty());
        assertEquals(4, contract.heldSlots(now));

        assertEquals(List.of(contract), handler.tickAmendments(now + MercenaryContract.OFFER_WINDOW_MS));
        assertNull(contract.getPendingSlots(now + DAY));
        assertEquals(2, contract.heldSlots(now + DAY));
    }

    @Test
    void shrinkingTheCompanyDropsAnIncreaseItCanNoLongerCover() {
        MercenaryContract contract = active(2);
        assertTrue(propose(contract, 4).ok());

        assertTrue(fixture.company.adminAdjustSlots(-1).ok());
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertEquals(2, contract.getSlots());
        assertNull(contract.getPendingSlots(now));
        assertEquals(3, fixture.company.getSlots());
    }

    @Test
    void anAcceptedChangeAndAWaitingChangeBothSurviveReload() {
        MercenaryContract contract = active(2);
        contract.addDayServed();
        assertTrue(propose(contract, 3).ok());

        MercenaryCompany waiting = new MercenaryCompany(
                fixture.host.guild, fixture.company.serialize(), CompanyFixture.companyRegiment());
        MercenaryContract pending = waiting.getContractHandler().getById(contract.getId());
        assertEquals(2, pending.getSlots());
        assertEquals(3, pending.getPendingSlots(now));
        assertEquals(3, pending.heldSlots(now));

        assertTrue(handler.acceptSlots(contract.getId(), fixture.hirer, "Chancellor", now).ok());
        MercenaryCompany accepted = new MercenaryCompany(
                fixture.host.guild, fixture.company.serialize(), CompanyFixture.companyRegiment());
        MercenaryContract copy = accepted.getContractHandler().getById(contract.getId());
        assertEquals(3, copy.getSlots());
        assertEquals(20.0, copy.getServedDaysOwed());
        assertNull(copy.getPendingSlots(now));
    }

    @Test
    void theContractScreenShowsTheWaitingTotals() {
        MercenaryContract contract = active(2);
        assertTrue(propose(contract, 4).ok());
        ContractCreator creator = new ContractCreator();

        List<String> list = creator.buildContractLore(contract);
        assertTrue(list.stream().anyMatch(line -> line.contains("Slot change waiting") && line.contains("4")));

        List<String> detail = creator.buildDetailLore(contract);
        assertTrue(detail.stream().anyMatch(line -> line.contains("2") && line.contains("4")));
        assertTrue(detail.stream().anyMatch(line ->
                line.contains("Per day if accepted") && line.contains("40.00")));
        assertTrue(detail.stream().anyMatch(line ->
                line.contains("Per battle if accepted") && line.contains("200.00")));
    }
}
