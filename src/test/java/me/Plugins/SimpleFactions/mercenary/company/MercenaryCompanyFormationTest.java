package me.Plugins.SimpleFactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;

class MercenaryCompanyFormationTest {
    private double oldCost;
    private int oldSeconds;

    @BeforeEach
    void setUp() {
        oldCost = Cache.mercenaryFormationCost;
        oldSeconds = Cache.mercenaryFormationSeconds;
        Cache.mercenaryFormationCost = 100.0;
        Cache.mercenaryFormationSeconds = 2;
        CompanyFixture.installMercenaryPrototype();
    }

    @AfterEach
    void tearDown() {
        Cache.mercenaryFormationCost = oldCost;
        Cache.mercenaryFormationSeconds = oldSeconds;
        CompanyFixture.clearRegiments();
    }

    @Test
    void formationDebitsOnceAndOpensWithOneSlot() {
        CompanyFixture fixture = new CompanyFixture(500.0);

        MercenaryResult result = MercenaryCompanyService
                .requestFormation(fixture.guild, "Ivar", "Hired Blades");

        assertTrue(result.ok());
        assertEquals(400.0, fixture.balance());
        MercenaryCompany company = fixture.company();
        assertNotNull(company);
        assertTrue(company.isForming());
        assertEquals(0, company.getSlots());

        company.tick();
        company.tick();

        assertTrue(company.isFormed());
        assertEquals(1, company.getSlots());
        assertEquals(400.0, fixture.balance());
    }

    @Test
    void secondRequestWhileFoundingIsRefusedWithoutCharging() {
        CompanyFixture fixture = new CompanyFixture(500.0);
        MercenaryCompanyService.requestFormation(fixture.guild, "Ivar", "Hired Blades");

        MercenaryResult second = MercenaryCompanyService
                .requestFormation(fixture.guild, "Ivar", "Second Blades");

        assertFalse(second.ok());
        assertEquals("Your guild is already founding a mercenary company.", second.message());
        assertEquals(400.0, fixture.balance());
    }

    @Test
    void secondCompanyRefusedOnceTheFirstExists() {
        CompanyFixture fixture = new CompanyFixture(500.0);
        MercenaryCompanyService.requestFormation(fixture.guild, "Ivar", "Hired Blades");
        fixture.company().tick();
        fixture.company().tick();

        MercenaryResult second = MercenaryCompanyService
                .requestFormation(fixture.guild, "Ivar", "Second Blades");

        assertFalse(second.ok());
        assertEquals("Your guild already has a mercenary company.", second.message());
        assertEquals(400.0, fixture.balance());
    }

    @Test
    void onlyTheGuildLeaderMayFound() {
        CompanyFixture fixture = new CompanyFixture(500.0);

        MercenaryResult result = MercenaryCompanyService
                .requestFormation(fixture.guild, "Sigrun", "Hired Blades");

        assertFalse(result.ok());
        assertEquals("Only the guild leader can found a mercenary company.", result.message());
        assertEquals(500.0, fixture.balance());
        assertNull(fixture.company());
    }

    @Test
    void tooPoorToFoundIsRefusedWithoutCharging() {
        CompanyFixture fixture = new CompanyFixture(99.0);

        MercenaryResult result = MercenaryCompanyService
                .requestFormation(fixture.guild, "Ivar", "Hired Blades");

        assertFalse(result.ok());
        assertEquals("Your guild bank needs 100.00d to found a company.", result.message());
        assertEquals(99.0, fixture.balance());
        assertNull(fixture.company());
    }

    @Test
    void unnamedCompanyIsRefused() {
        CompanyFixture fixture = new CompanyFixture(500.0);

        MercenaryResult result = MercenaryCompanyService
                .requestFormation(fixture.guild, "Ivar", "   ");

        assertFalse(result.ok());
        assertEquals("Give the company a name.", result.message());
        assertEquals(500.0, fixture.balance());
    }

    @Test
    void missingRegimentConfigRefusesFormation() {
        CompanyFixture.clearRegiments();
        CompanyFixture fixture = new CompanyFixture(500.0);

        MercenaryResult result = MercenaryCompanyService
                .requestFormation(fixture.guild, "Ivar", "Hired Blades");

        assertFalse(result.ok());
        assertEquals("Mercenary companies are not configured on this server.", result.message());
        assertEquals(500.0, fixture.balance());
    }

    @Test
    void leadershipFollowsTheGuildLeader() {
        CompanyFixture fixture = new CompanyFixture(500.0);
        MercenaryCompanyService.requestFormation(fixture.guild, "Ivar", "Hired Blades");
        MercenaryCompany company = fixture.company();

        assertEquals("Ivar", company.getLeader());
        assertTrue(company.isLeader("ivar"));

        fixture.setLeader("Sigrun");

        assertEquals("Sigrun", company.getLeader());
        assertFalse(company.isLeader("Ivar"));
        assertTrue(company.isLeader("Sigrun"));
    }
}
