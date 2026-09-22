package me.Plugins.SimpleFactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.income.Ledger;
import me.Plugins.SimpleFactions.Guild.upgrade.Upgrade;
import me.Plugins.SimpleFactions.Managers.Inventory.CompanyCreator;
import me.Plugins.SimpleFactions.Utils.Formatter;

/** The company screens, checked through the lore builders as the ledger screen is. */
class CompanyGuiTest {
    private final CompanyCreator creator = new CompanyCreator();
    private CompanyFixture fixture;

    @BeforeEach
    void setUp() {
        CompanyFixture.installMercenaryPrototype();
        CompanyFixture.installCompanyUpgrades();
        Cache.mercenarySlotUpkeep = 8.0;
        Cache.mercenaryFormationCost = 100.0;
        Cache.mercenaryFormationSeconds = 86400;
        fixture = new CompanyFixture(1000);
    }

    @AfterEach
    void tearDown() {
        CompanyFixture.clearCompanyUpgrades();
        CompanyFixture.clearRegiments();
        CompanyFixture.clearGlobalGuilds();
    }

    private MercenaryCompany formedCompany() {
        MercenaryCompanyService.requestFormation(fixture.guild, fixture.leader(), "Hired Blades");
        MercenaryCompany company = fixture.company();
        for (int i = 0; i < Cache.mercenaryFormationSeconds; i++) {
            company.tick();
        }
        return company;
    }

    @Test
    void burnLineEqualsTheSumOfItsParts() {
        MercenaryCompany company = formedCompany();
        company.enlist("Bjorn");
        assertTrue(company.enqueueExpansion().ok());
        for (int i = 0; i < 86400; i++) {
            company.tick();
        }
        company.getUpgrade("company_health").setLevel(3);

        company.getWageSettings().setPeacetimePerDay(4.0);

        assertEquals(2, company.getSlots());
        assertEquals(16.0, company.getSlotUpkeep());
        assertEquals(30.0, company.getUpgradeUpkeep());
        assertEquals(4.0, company.getWageUpkeep(), "one enlisted player on a 4 denar retainer");
        assertEquals(50.0, company.getDailyBurn());

        List<String> lore = creator.buildCompanyLore(fixture.guild, company);
        assertTrue(lore.stream().anyMatch(line ->
                line.contains("Daily burn") && line.contains(Formatter.formatMoney(50.0))));
        assertTrue(lore.stream().anyMatch(line ->
                line.contains("Slots") && line.contains(Formatter.formatMoney(16.0))));
        assertTrue(lore.stream().anyMatch(line ->
                line.contains("Upgrades") && line.contains(Formatter.formatMoney(30.0))));
        assertTrue(lore.stream().anyMatch(line ->
                line.contains("Wages") && line.contains(Formatter.formatMoney(4.0))));
    }

    @Test
    void aCompanyWithNoContractsShowsPeacetimeBurnOnly() {
        MercenaryCompany company = formedCompany();
        company.enlist("Bjorn");
        company.getWageSettings().setPeacetimePerDay(4.0);

        assertEquals(0.0, company.getContractIncome());
        assertEquals(4.0, company.getWageUpkeep());
        assertEquals(-company.getDailyBurn(), company.getNetPosition());

        List<String> lore = creator.buildCompanyLore(fixture.guild, company);
        assertTrue(lore.stream().anyMatch(line ->
                line.contains("Contract income") && line.contains(Formatter.formatMoney(0.0))));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Net position")));
    }

    @Test
    void theWarningTriggersOnTheBoundaryAndNotBefore() {
        MercenaryCompany company = formedCompany();
        Ledger ledger = mock(Ledger.class);
        when(fixture.guild.getLedger()).thenReturn(ledger);
        double burn = company.getDailyBurn();

        // Net income already has the burn taken out of it, so zero net means the guild
        // covers its company exactly.
        when(ledger.getNetIncome()).thenReturn(0.0);
        assertFalse(CompanyCreator.burnExceedsIncome(fixture.guild, company));
        assertFalse(creator.buildCompanyLore(fixture.guild, company).stream()
                .anyMatch(line -> line.contains("Burn exceeds")));

        when(ledger.getNetIncome()).thenReturn(-0.01);
        assertTrue(CompanyCreator.burnExceedsIncome(fixture.guild, company));
        List<String> lore = creator.buildCompanyLore(fixture.guild, company);
        assertTrue(lore.stream().anyMatch(line -> line.contains("Burn exceeds")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("voids every contract")));
        assertTrue(burn > 0, "a formed company always costs something");
    }

    @Test
    void aSolventGuildIsNotWarned() {
        MercenaryCompany company = formedCompany();
        Ledger ledger = mock(Ledger.class);
        when(fixture.guild.getLedger()).thenReturn(ledger);
        when(ledger.getNetIncome()).thenReturn(500.0);

        assertFalse(CompanyCreator.burnExceedsIncome(fixture.guild, company));
    }

    @Test
    void companyLoreNamesTheLiveLeaderAndSlotFill() {
        MercenaryCompany company = formedCompany();
        company.enlist("Bjorn");
        fixture.setLeader("Sigrid");

        List<String> lore = creator.buildCompanyLore(fixture.guild, company);
        assertTrue(lore.stream().anyMatch(line -> line.contains("Leader") && line.contains("Sigrid")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Slots") && line.contains("1/1")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Home") && line.contains("None")));
    }

    @Test
    void everyUpgradeItemCarriesTheBuffScopeWarning() {
        MercenaryCompany company = formedCompany();
        List<Upgrade> upgrades = company.getUpgrades();
        assertEquals(3, upgrades.size());
        for (Upgrade upgrade : upgrades) {
            List<String> lore = creator.buildUpgradeLore(upgrade);
            assertTrue(lore.stream().anyMatch(line ->
                            line.contains("Only while fighting as a hired mercenary")),
                    "missing buff scope warning on " + upgrade.getId());
            assertTrue(lore.stream().anyMatch(line -> line.contains("Level: ")
                    && line.contains("10")));
        }
    }

    @Test
    void maxedUpgradeSaysSoAndKeepsTheWarning() {
        MercenaryCompany company = formedCompany();
        Upgrade upgrade = company.getUpgrade("company_mana");
        upgrade.setLevel(10);

        List<String> lore = creator.buildUpgradeLore(upgrade);
        assertTrue(lore.stream().anyMatch(line -> line.contains("Maximum level reached")));
        assertTrue(lore.stream().anyMatch(line ->
                line.contains("Only while fighting as a hired mercenary")));
    }

    @Test
    void emptySlotLoreExplainsWhyExpansionIsFrozen() {
        MercenaryCompany company = formedCompany();

        List<String> empty = creator.buildSlotLore(null);
        assertTrue(empty.stream().anyMatch(line -> line.contains("Empty")));
        assertTrue(empty.stream().anyMatch(line -> line.contains("blocks expansion")));

        List<String> taken = creator.buildSlotLore("Bjorn");
        assertTrue(taken.stream().anyMatch(line -> line.contains("Bjorn")));
        assertFalse(taken.stream().anyMatch(line -> line.contains("Empty")));

        assertEquals("Fill every slot before adding another.",
                company.getExpansionBlockedReason());
        List<String> lore = creator.buildCompanyLore(fixture.guild, company);
        assertTrue(lore.stream().anyMatch(line -> line.contains("Cannot expand")));
        assertTrue(lore.stream().anyMatch(line ->
                line.contains("Fill every slot before adding another.")));
    }

    @Test
    void foundingButtonQuotesTheChartersCostAndTime() {
        List<String> lore = creator.buildFoundingLore();
        assertTrue(lore.stream().anyMatch(line ->
                line.contains("Cost") && line.contains(Formatter.formatMoney(100.0))));
        assertTrue(lore.stream().anyMatch(line -> line.contains("/company found")));

        MercenaryCompanyService.requestFormation(fixture.guild, fixture.leader(), "Hired Blades");
        List<String> forming = creator.buildFormingLore(fixture.company());
        assertTrue(forming.stream().anyMatch(line -> line.contains("Founding")));
        assertTrue(forming.stream().anyMatch(line -> line.contains("Ready in")));
    }

    @Test
    void noPlayerFacingEmDashes() {
        MercenaryCompany company = formedCompany();
        List<String> lines = new java.util.ArrayList<>();
        lines.addAll(creator.buildFoundingLore());
        lines.addAll(creator.buildFormingLore(company));
        lines.addAll(creator.buildCompanyLore(fixture.guild, company));
        lines.addAll(creator.buildSlotLore(null));
        lines.addAll(creator.buildSlotLore("Bjorn"));
        for (Upgrade upgrade : company.getUpgrades()) {
            lines.addAll(creator.buildUpgradeLore(upgrade));
        }
        for (String line : lines) {
            assertFalse(line.contains("\u2014"), "em dash in company lore: " + line);
        }
    }
}
