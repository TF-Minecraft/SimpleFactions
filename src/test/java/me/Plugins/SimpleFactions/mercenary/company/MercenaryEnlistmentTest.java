package me.Plugins.SimpleFactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;

class MercenaryEnlistmentTest {

    @BeforeEach
    void setUp() {
        MercenaryEligibility.reset();
        CompanyFixture.installCompanyUpgrades();
    }

    @AfterEach
    void tearDown() {
        MercenaryEligibility.reset();
        CompanyFixture.clearCompanyUpgrades();
    }

    @Test
    void joiningFillsASlot() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = formed(fixture);

        MercenaryResult result = MercenaryCompanyService
                .join(company, "Sigrun", List.of(fixture.guild));

        assertTrue(result.ok());
        assertEquals("You joined Hired Blades.", result.message());
        assertEquals(List.of("Sigrun"), company.getEnlisted());
        assertEquals(1, company.getFilledSlots());
        assertFalse(company.hasFreeSlot());
    }

    @Test
    void aSecondCompanyIsRefusedAnywhereOnTheServer() {
        CompanyFixture host = new CompanyFixture(0);
        CompanyFixture rival = new CompanyFixture(0);
        MercenaryCompany served = formed(host);
        MercenaryCompany other = formed(rival, "Iron Wolves");
        MercenaryCompanyService.join(served, "Sigrun", List.of(host.guild, rival.guild));

        MercenaryResult result = MercenaryCompanyService
                .join(other, "Sigrun", List.of(host.guild, rival.guild));

        assertFalse(result.ok());
        assertEquals("You already serve in Hired Blades.", result.message());
        assertTrue(other.getEnlisted().isEmpty());
    }

    @Test
    void rejoiningTheSameCompanyIsRefused() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = formed(fixture);
        company.getRegiment().setCurrentSlots(2);
        MercenaryCompanyService.join(company, "Sigrun", List.of(fixture.guild));

        MercenaryResult result = MercenaryCompanyService
                .join(company, "Sigrun", List.of(fixture.guild));

        assertFalse(result.ok());
        assertEquals("You already serve in that company.", result.message());
        assertEquals(1, company.getEnlisted().size());
    }

    @Test
    void joiningOverCapacityIsRefused() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = formed(fixture);
        MercenaryCompanyService.join(company, "Sigrun", List.of(fixture.guild));

        MercenaryResult result = MercenaryCompanyService
                .join(company, "Bjorn", List.of(fixture.guild));

        assertFalse(result.ok());
        assertEquals("That company has no free slot.", result.message());
        assertEquals(List.of("Sigrun"), company.getEnlisted());
    }

    @Test
    void joiningAFoundingCompanyIsRefused() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = new MercenaryCompany(
                fixture.guild, "Hired Blades", CompanyFixture.companyRegiment(), 120);

        MercenaryResult result = MercenaryCompanyService
                .join(company, "Sigrun", List.of(fixture.guild));

        assertFalse(result.ok());
        assertEquals("That company is still being founded.", result.message());
    }

    @Test
    void invitingIsLeaderOnlyAndNeedsAFreeSlot() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = formed(fixture);

        assertTrue(MercenaryCompanyService.canInvite(fixture.guild, "Ivar", "Sigrun").ok());
        assertEquals("Only the guild leader can enlist mercenaries.",
                MercenaryCompanyService.canInvite(fixture.guild, "Bjorn", "Sigrun").message());

        company.enlist("Sigrun");

        MercenaryResult full = MercenaryCompanyService.canInvite(fixture.guild, "Ivar", "Bjorn");
        assertFalse(full.ok());
        assertEquals("Every slot is already filled.", full.message());

        company.getRegiment().setCurrentSlots(2);

        MercenaryResult already = MercenaryCompanyService.canInvite(fixture.guild, "Ivar", "Sigrun");
        assertFalse(already.ok());
        assertEquals("Sigrun already serves in your company.", already.message());
    }

    @Test
    void kickingFreesTheSlotAndFreezesExpansion() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = formed(fixture);
        MercenaryCompanyService.join(company, "Sigrun", List.of(fixture.guild));
        assertTrue(company.canExpand().ok());

        MercenaryResult result = MercenaryCompanyService.kick(fixture.guild, "Ivar", "Sigrun");

        assertTrue(result.ok());
        assertEquals("Sigrun was dismissed from the company.", result.message());
        assertTrue(company.getEnlisted().isEmpty());
        assertEquals(1, company.getSlots());
        assertEquals("Fill every slot before adding another.", company.getExpansionBlockedReason());
    }

    @Test
    void onlyTheLeaderMayKick() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = formed(fixture);
        company.enlist("Sigrun");

        MercenaryResult result = MercenaryCompanyService.kick(fixture.guild, "Sigrun", "Sigrun");

        assertFalse(result.ok());
        assertEquals("Only the guild leader can dismiss mercenaries.", result.message());
        assertEquals(List.of("Sigrun"), company.getEnlisted());
    }

    @Test
    void kickingSomeoneElsesMercenaryIsRefused() {
        CompanyFixture fixture = new CompanyFixture(0);
        formed(fixture);

        MercenaryResult result = MercenaryCompanyService.kick(fixture.guild, "Ivar", "Bjorn");

        assertFalse(result.ok());
        assertEquals("Bjorn does not serve in your company.", result.message());
    }

    @Test
    void lookupsFindCompaniesByMemberAndName() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = formed(fixture);
        List<Guild> guilds = List.of(fixture.guild);
        company.enlist("Sigrun");

        assertSame(company, MercenaryCompanies.findByMember("sigrun", guilds));
        assertNull(MercenaryCompanies.findByMember("Bjorn", guilds));
        assertSame(company, MercenaryCompanies.findByName("hired blades", guilds));
        assertNull(MercenaryCompanies.findByName("Iron Wolves", guilds));
    }

    @Test
    void theEligibilitySeamIsConsultedByBothCreateAndJoin() {
        assertTrue(MercenaryEligibility.canCreate("Ivar"));
        assertTrue(MercenaryEligibility.canJoin("Sigrun"));

        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = formed(fixture);

        assertTrue(MercenaryCompanyService.canJoin(company, "Sigrun", List.of(fixture.guild)).ok());

        MercenaryEligibility.setProbe(player -> MercenaryEligibility.Status.INELIGIBLE);
        assertFalse(MercenaryEligibility.canCreate("Ivar"));
        assertFalse(MercenaryCompanyService.canJoin(company, "Sigrun", List.of(fixture.guild)).ok());
    }

    private static MercenaryCompany formed(CompanyFixture fixture) {
        return formed(fixture, "Hired Blades");
    }

    private static MercenaryCompany formed(CompanyFixture fixture, String name) {
        MercenaryCompany company = new MercenaryCompany(
                fixture.guild, name, CompanyFixture.companyRegiment(), 0);
        fixture.guild.setCompany(company);
        return company;
    }
}
