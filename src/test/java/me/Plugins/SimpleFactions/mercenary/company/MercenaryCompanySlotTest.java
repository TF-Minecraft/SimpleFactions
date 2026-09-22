package me.Plugins.SimpleFactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;

class MercenaryCompanySlotTest {
    private double oldSlotUpkeep;

    @BeforeEach
    void setUp() {
        oldSlotUpkeep = Cache.mercenarySlotUpkeep;
        Cache.mercenarySlotUpkeep = 8.0;
        CompanyFixture.installCompanyUpgrades();
    }

    @AfterEach
    void tearDown() {
        Cache.mercenarySlotUpkeep = oldSlotUpkeep;
        CompanyFixture.clearCompanyUpgrades();
    }

    @Test
    void anEmptySlotBlocksExpansion() {
        MercenaryCompany company = formedCompany();

        MercenaryResult result = company.canExpand();

        assertFalse(result.ok());
        assertEquals("Fill every slot before adding another.", result.message());
        assertEquals("Fill every slot before adding another.", company.getExpansionBlockedReason());
        assertTrue(company.getSlotQueue().isEmpty());
    }

    @Test
    void aFullCompanyMayExpand() {
        MercenaryCompany company = formedCompany();
        company.enlist("Sigrun");

        assertTrue(company.canExpand().ok());
        assertNull(company.getExpansionBlockedReason());
        assertTrue(company.enqueueExpansion().ok());
        assertEquals(1, company.getSlotQueue().size());
    }

    @Test
    void aQueuedSlotCountsAsUnfilled() {
        MercenaryCompany company = formedCompany();
        company.enlist("Sigrun");
        company.enqueueExpansion();

        MercenaryResult second = company.enqueueExpansion();

        assertFalse(second.ok());
        assertEquals("Fill every slot before adding another.", second.message());
        assertEquals(1, company.getSlotQueue().size());
    }

    @Test
    void aFoundingCompanyMayNotExpand() {
        MercenaryCompany company = new MercenaryCompany(
                new CompanyFixture(0).guild, "Hired Blades", CompanyFixture.companyRegiment(), 120);

        MercenaryResult result = company.canExpand();

        assertFalse(result.ok());
        assertEquals("Your company is still being founded.", result.message());
    }

    @Test
    void aFinishedQueueRaisesTheSlotCount() {
        MercenaryCompany company = formedCompany();
        company.enlist("Sigrun");
        company.enqueueExpansion();
        company.getSlotQueue().clear();
        company.addQueuedExpansion(2);

        company.tick();
        assertEquals(1, company.getSlots());
        company.tick();

        assertEquals(2, company.getSlots());
        assertTrue(company.getSlotQueue().isEmpty());
        assertEquals(1, company.getFilledSlots());
        assertTrue(company.hasFreeSlot());
    }

    @Test
    void droppingASlotIsTheOnlyWayDownAndFreesTheRoster() {
        MercenaryCompany company = formedCompany();
        company.enlist("Sigrun");
        assertEquals(1, company.getSlots());

        assertTrue(company.dropSlot());

        assertEquals(0, company.getSlots());
        assertTrue(company.getEnlisted().isEmpty());
        assertFalse(company.dropSlot());
    }

    @Test
    void slotUpkeepIsLinearInSlots() {
        MercenaryCompany company = formedCompany();

        assertEquals(8.0, company.getSlotUpkeep());
        assertEquals(8.0, company.getDailyBurn());

        company.getRegiment().setCurrentSlots(4);

        assertEquals(32.0, company.getSlotUpkeep());
        assertEquals(0.0, company.getWageUpkeep());
        assertEquals(32.0, company.getDailyBurn());
    }

    @Test
    void cancelSlotQueueRemovesQueuedExpansion() {
        MercenaryCompany company = formedCompany();
        company.enlist("Sigrun");
        company.enqueueExpansion();

        assertTrue(company.cancelSlotQueue(0));
        assertTrue(company.getSlotQueue().isEmpty());
    }

    private static MercenaryCompany formedCompany() {
        return new MercenaryCompany(
                new CompanyFixture(0).guild, "Hired Blades", CompanyFixture.companyRegiment(), 0);
    }
}
