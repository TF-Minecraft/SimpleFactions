package me.Plugins.SimpleFactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Database.MercenaryCompanyData;
import me.Plugins.SimpleFactions.Guild.upgrade.Upgrade;

class MercenaryCompanyPersistenceTest {

    @BeforeEach
    void setUp() {
        CompanyFixture.installCompanyUpgrades();
    }

    @AfterEach
    void tearDown() {
        CompanyFixture.clearCompanyUpgrades();
    }

    @Test
    void midFormationSurvivesSaveAndLoad() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = new MercenaryCompany(
                fixture.guild, "Hired Blades", CompanyFixture.companyRegiment(), 120);
        company.tick();
        company.tick();

        MercenaryCompany loaded = reload(fixture, company);

        assertTrue(loaded.isForming());
        assertEquals(118, loaded.getFormationRemaining());
        assertEquals(0, loaded.getSlots());
        assertEquals("Hired Blades", loaded.getName());
        assertEquals(List.of("white.base"), loaded.getBannerPatterns());
        assertEquals(50, loaded.getReputation());
    }

    @Test
    void formedCompanyRoundTripsSlotsRosterAndUpgrades() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = new MercenaryCompany(
                fixture.guild, "Hired Blades", CompanyFixture.companyRegiment(), 0);
        company.enlist("Sigrun");
        assertTrue(company.enqueueExpansion().ok());
        company.tick();
        Upgrade health = company.getUpgrade("company_health");
        health.setLevel(3);
        assertTrue(company.enqueueUpgrade(company.getUpgrade("company_mana")));

        MercenaryCompanyData data = company.serialize();
        assertEquals("mercenary.1", data.slots);
        assertEquals(List.of("mercenary.86399"), data.slotQueue);

        MercenaryCompany loaded = new MercenaryCompany(
                fixture.guild, data, CompanyFixture.companyRegiment());

        assertTrue(loaded.isFormed());
        assertEquals(1, loaded.getSlots());
        assertEquals(List.of("Sigrun"), loaded.getEnlisted());
        assertEquals(1, loaded.getSlotQueue().size());
        assertEquals(86399, loaded.getSlotQueue().get(0).getTimeLeft());
        assertEquals(3, loaded.getUpgrade("company_health").getLevel());
        assertEquals(1, loaded.getUpgradeQueue().size());
        assertEquals("company_mana", loaded.getUpgradeQueue().get(0).getUpgrade().getId());
        assertEquals(4, loaded.getUpgradeQueue().get(0).getTimeLeft());
    }

    @Test
    void wageTermsAndUnpaidPayrollSurviveARestart() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = new MercenaryCompany(
                fixture.guild, "Hired Blades", CompanyFixture.companyRegiment(), 0);
        company.enlist("Sigrun");
        company.getWageSettings().setActivePercent(20);
        company.getWageSettings().setPeacetimePerDay(4.0);
        company.getWageSettings().setActiveOverride("Sigrun", 50.0);
        company.getWageSettings().setPeacetimeOverride("Sigrun", 12.0);
        // A battle share accrues the moment a battle resolves, hours before it is paid.
        company.accrueWage("Sigrun", 10.0);

        MercenaryCompany loaded = reload(fixture, company);

        assertEquals(20.0, loaded.getWageSettings().getActivePercent());
        assertEquals(4.0, loaded.getWageSettings().getPeacetimePerDay());
        assertEquals(50.0, loaded.getWageSettings().activePercentFor("Sigrun"));
        assertEquals(12.0, loaded.getWageSettings().peacetimeFor("Sigrun"));
        assertEquals(10.0, loaded.getPendingWages().get("Sigrun"));
    }

    @Test
    void aReputationHitSurvivesARestart() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = new MercenaryCompany(
                fixture.guild, "Hired Blades", CompanyFixture.companyRegiment(), 0);
        company.changeReputation(-15);

        assertEquals(35, reload(fixture, company).getReputation());
    }

    @Test
    void loadedCompanyKeepsFollowingTheGuildLeader() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = new MercenaryCompany(
                fixture.guild, "Hired Blades", CompanyFixture.companyRegiment(), 0);

        MercenaryCompany loaded = reload(fixture, company);
        fixture.setLeader("Sigrun");

        assertEquals("Sigrun", loaded.getLeader());
        assertFalse(loaded.isLeader("Ivar"));
    }

    private static MercenaryCompany reload(CompanyFixture fixture, MercenaryCompany company) {
        return new MercenaryCompany(
                fixture.guild, company.serialize(), CompanyFixture.companyRegiment());
    }
}
