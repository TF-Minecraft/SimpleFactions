package me.Plugins.SimpleFactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.contract.ContractFixture;
import me.Plugins.SimpleFactions.mercenary.contract.ContractStatus;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryContract;

class MercenaryEligibilityTest {

    @BeforeEach
    void setUp() {
        CompanyFixture.installCompanyUpgrades();
        MercenaryEligibility.reset();
    }

    @AfterEach
    void tearDown() {
        MercenaryEligibility.reset();
        CompanyFixture.clearCompanyUpgrades();
        ContractFixture.tearDown();
    }

    @Test
    void createAndJoinRefuseAnIneligibleCharacter() {
        MercenaryEligibility.setProbe(ineligible("Ivar", "Sigrun"));
        CompanyFixture fixture = new CompanyFixture(500.0);
        CompanyFixture.installMercenaryPrototype();

        MercenaryResult create = MercenaryCompanyService
                .requestFormation(fixture.guild, "Ivar", "Hired Blades");
        assertFalse(create.ok());
        assertEquals("You are not the sort to run a mercenary company.", create.message());
        assertNull(fixture.company());

        MercenaryEligibility.reset();
        MercenaryCompany company = formed(fixture);
        MercenaryEligibility.setProbe(ineligible("Sigrun"));

        MercenaryResult join = MercenaryCompanyService
                .join(company, "Sigrun", List.of(fixture.guild));
        assertFalse(join.ok());
        assertEquals("You are not the sort to take mercenary work.", join.message());
        assertTrue(company.getEnlisted().isEmpty());

        CompanyFixture.clearRegiments();
    }

    @Test
    void anIneligibleLeaderDisbandsEvenWhileFounding() {
        CompanyFixture fixture = new CompanyFixture(500.0);
        CompanyFixture.installMercenaryPrototype();
        MercenaryCompanyService.requestFormation(fixture.guild, "Ivar", "Hired Blades");
        MercenaryCompany company = fixture.company();
        assertTrue(company.isForming());

        MercenaryEligibility.setProbe(ineligible("Ivar"));
        company.tick();

        assertNull(fixture.company());
        CompanyFixture.clearRegiments();
    }

    @Test
    void anIneligibleLeaderDisbandsAFormedCompanyAndBreachesContracts() {
        ContractFixture fixture = ContractFixture.formed(2);
        MercenaryContract contract = fixture.offer(ContractFixture.validTerms(2), System.currentTimeMillis());
        contract.activate();
        MercenaryContract offer = fixture.offer(ContractFixture.validTerms(1), System.currentTimeMillis());

        MercenaryEligibility.setProbe(ineligible(fixture.host.leader()));
        fixture.company.tick();

        assertNull(fixture.host.company());
        assertEquals(ContractStatus.BREACHED, contract.getStatus());
        assertEquals(ContractStatus.TERMINATED, offer.getStatus());
    }

    @Test
    void anIneligibleMemberIsKickedWithoutDisbanding() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = formed(fixture);
        company.getRegiment().setCurrentSlots(2);
        company.enlist("Sigrun");
        company.enlist("Bjorn");

        MercenaryEligibility.setProbe(ineligible("Sigrun"));
        company.tick();

        assertEquals(company, fixture.company());
        assertEquals(List.of("Bjorn"), company.getEnlisted());
        assertEquals(2, company.getSlots());
    }

    @Test
    void unknownPlayersAreLeftAlone() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = formed(fixture);
        company.enlist("Sigrun");

        MercenaryEligibility.setProbe(player -> MercenaryEligibility.Status.UNKNOWN);
        company.tick();

        assertEquals(company, fixture.company());
        assertEquals(List.of("Sigrun"), company.getEnlisted());
        assertTrue(company.isFormed());
    }

    @Test
    void kickingAnIneligibleMemberDoesNotInventASlotBreach() {
        ContractFixture fixture = ContractFixture.formed(2);
        fixture.company.enlist("Sigrun");
        MercenaryContract contract = fixture.offer(ContractFixture.validTerms(2), System.currentTimeMillis());
        contract.activate();

        MercenaryEligibility.setProbe(player -> {
            if ("Sigrun".equalsIgnoreCase(player)) return MercenaryEligibility.Status.INELIGIBLE;
            return MercenaryEligibility.Status.ELIGIBLE;
        });
        fixture.company.tick();

        assertEquals(fixture.company, fixture.host.company());
        assertFalse(fixture.company.isEnlisted("Sigrun"));
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
    }

    private static MercenaryCompany formed(CompanyFixture fixture) {
        MercenaryCompany company = new MercenaryCompany(
                fixture.guild, "Hired Blades", CompanyFixture.companyRegiment(), 0);
        fixture.guild.setCompany(company);
        return company;
    }

    private static MercenaryEligibility.Probe ineligible(String... names) {
        return player -> {
            for (String name : names) {
                if (name.equalsIgnoreCase(player)) return MercenaryEligibility.Status.INELIGIBLE;
            }
            return MercenaryEligibility.Status.ELIGIBLE;
        };
    }
}
