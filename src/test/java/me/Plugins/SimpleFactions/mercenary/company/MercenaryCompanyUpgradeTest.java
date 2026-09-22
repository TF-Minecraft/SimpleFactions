package me.Plugins.SimpleFactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URISyntaxException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Guild.upgrade.Upgrade;
import me.Plugins.SimpleFactions.Loaders.CompanyUpgradeLoader;
import me.Plugins.SimpleFactions.Loaders.UpgradeLoader;
import me.Plugins.SimpleFactions.enums.GuildModifier;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;

class MercenaryCompanyUpgradeTest {

    @BeforeEach
    void setUp() {
        CompanyFixture.installCompanyUpgrades();
    }

    @AfterEach
    void tearDown() {
        CompanyFixture.clearCompanyUpgrades();
    }

    @Test
    void levellingStopsAtTheCap() {
        Upgrade upgrade = CompanyFixture.upgrade("company_health", "max_health 0 0.5", 10);

        for (int i = 0; i < 15; i++) {
            upgrade.levelUp();
        }

        assertEquals(10, upgrade.getLevel());
        assertTrue(upgrade.isMaxed());
        assertTrue(upgrade.hasMaxLevel());
        assertEquals(10, upgrade.getMaxLevel());
    }

    @Test
    void guildUpgradesWithoutACapStillClimb() {
        Upgrade upgrade = CompanyFixture.upgrade("admin_power_gain", "admin_power_gain 0 0.5", 0);

        for (int i = 0; i < 15; i++) {
            upgrade.levelUp();
        }

        assertEquals(15, upgrade.getLevel());
        assertFalse(upgrade.hasMaxLevel());
        assertFalse(upgrade.isMaxed());
    }

    @Test
    void amountIsPerLevelTimesLevel() {
        Upgrade health = CompanyFixture.upgrade("company_health", "max_health 0 0.5", 10);

        assertEquals(0.0, health.getAmount(GuildModifier.MAX_HEALTH));
        health.setLevel(4);
        assertEquals(2.0, health.getAmount(GuildModifier.MAX_HEALTH));
        health.setLevel(10);
        assertEquals(5.0, health.getAmount(GuildModifier.MAX_HEALTH));
    }

    @Test
    void settingALevelPastTheCapIsClamped() {
        Upgrade health = CompanyFixture.upgrade("company_health", "max_health 0 0.5", 10);

        health.setLevel(40);

        assertEquals(10, health.getLevel());
    }

    @Test
    void companyModifiersSumAcrossItsOwnUpgrades() {
        MercenaryCompany company = formedCompany();
        company.getUpgrade("company_health").setLevel(4);
        company.getUpgrade("company_mana").setLevel(3);
        company.getUpgrade("company_mana_regen").setLevel(3);

        assertEquals(2.0, company.getModifier(GuildModifier.MAX_HEALTH));
        assertEquals(3.0, company.getModifier(GuildModifier.MAX_MANA));
        assertEquals(0.3, company.getModifier(GuildModifier.MANA_REGEN));
    }

    @Test
    void companyUpgradesAreNeverLoadedAsGuildUpgrades() {
        assertNull(UpgradeLoader.getByString("company_health"));
        assertNull(UpgradeLoader.getByString("company_mana"));
        assertNull(UpgradeLoader.getByString("company_mana_regen"));
        assertFalse(UpgradeLoader.getList().stream()
                .anyMatch(u -> u.getId().startsWith("company_")));
    }

    @Test
    void queueingAMaxedUpgradeIsRefused() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = formedCompany(fixture);
        company.getUpgrade("company_health").setLevel(10);

        MercenaryResult result = MercenaryCompanyService
                .upgrade(fixture.guild, "Ivar", "company_health");

        assertFalse(result.ok());
        assertTrue(result.message().endsWith("is already at its maximum level."));
        assertTrue(company.getUpgradeQueue().isEmpty());
    }

    @Test
    void aQueuedUpgradeLevelsUpWhenItFinishes() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = formedCompany(fixture);

        assertTrue(MercenaryCompanyService.upgrade(fixture.guild, "Ivar", "company_mana").ok());
        for (int i = 0; i < 4; i++) {
            company.tick();
        }

        assertEquals(1, company.getUpgrade("company_mana").getLevel());
        assertTrue(company.getUpgradeQueue().isEmpty());
        assertEquals(1.0, company.getModifier(GuildModifier.MAX_MANA));
    }

    @Test
    void upgradeUpkeepCountsOnlyPurchasedLevels() {
        MercenaryCompany company = formedCompany();

        assertEquals(0.0, company.getUpgradeUpkeep());

        company.getUpgrade("company_health").setLevel(2);

        assertEquals(20.0, company.getUpgradeUpkeep());
    }

    @Test
    void shippedCompanyUpgradeFileMatchesTheCappedPvpStats() throws URISyntaxException {
        CompanyUpgradeLoader loader = new CompanyUpgradeLoader();
        loader.load(resource("/Guilds/company-upgrades.yml"));

        assertEquals(3, CompanyUpgradeLoader.getList().size());
        assertCapped("company_health", GuildModifier.MAX_HEALTH, 0.5);
        assertCapped("company_mana", GuildModifier.MAX_MANA, 1.0);
        assertCapped("company_mana_regen", GuildModifier.MANA_REGEN, 0.1);
    }

    private static void assertCapped(String id, GuildModifier modifier, double perLevel) {
        Upgrade upgrade = CompanyUpgradeLoader.getByString(id);
        assertNotNull(upgrade, id + " missing from company-upgrades.yml");
        assertEquals(10, upgrade.getMaxLevel());
        assertEquals(10.0, upgrade.getUpkeep());
        assertNotNull(upgrade.getModifier(modifier));
        assertEquals(perLevel, upgrade.getModifier(modifier).getPerLevel());
        assertEquals(0.0, upgrade.getModifier(modifier).getBase());
        assertTrue(modifier.isPositive());
    }

    private static File resource(String path) throws URISyntaxException {
        return new File(MercenaryCompanyUpgradeTest.class.getResource(path).toURI());
    }

    private static MercenaryCompany formedCompany() {
        return formedCompany(new CompanyFixture(0));
    }

    private static MercenaryCompany formedCompany(CompanyFixture fixture) {
        MercenaryCompany company = new MercenaryCompany(
                fixture.guild, "Hired Blades", CompanyFixture.companyRegiment(), 0);
        fixture.guild.setCompany(company);
        return company;
    }
}
