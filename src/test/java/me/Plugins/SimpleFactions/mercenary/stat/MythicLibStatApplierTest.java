package me.Plugins.SimpleFactions.mercenary.stat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.mercenary.company.CompanyFixture;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/**
 * MythicLib and MMOCore are soft depends. The server has to come up and play a
 * battle without them, with the company buffs simply not applied, so the gate is
 * checked against the real applier rather than a fake one.
 */
class MythicLibStatApplierTest {
    private MythicLibStatApplier applier;
    private CompanyFixture fixture;
    private MercenaryCompany company;

    @BeforeEach
    void setUp() {
        CompanyFixture.installCompanyUpgrades();
        fixture = new CompanyFixture(0);
        company = new MercenaryCompany(
                fixture.guild, "Hired Blades", CompanyFixture.companyRegiment(), 0);
        fixture.guild.setCompany(company);
        company.getRegiment().setCurrentSlots(2);
        company.enlist("Sigrun");
        CompanyFixture.registerGlobally(fixture.guild);

        applier = new MythicLibStatApplier();
        MercenaryStatService.reset();
    }

    @AfterEach
    void tearDown() {
        MercenaryStatService.reset();
        CompanyFixture.clearCompanyUpgrades();
        CompanyFixture.clearGlobalGuilds();
    }

    @Test
    void noServerAtAllIsUnavailableRatherThanAThrow() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(null);

            assertFalse(applier.isAvailable());
        }
    }

    @Test
    void bothPluginsAreRequired() {
        assertFalse(availableWith(false, false), "neither plugin present");
        assertFalse(availableWith(true, false), "MythicLib alone is not enough");
        assertFalse(availableWith(false, true), "MMOCore alone is not enough");
        assertTrue(availableWith(true, true));
    }

    @Test
    void withoutTheDependenciesApplyingIsARepeatableNoOp() {
        MercenaryStatService.setGate(player -> true);
        MercenaryStatService.setApplier(applier);
        company.getUpgrade("company_health").setLevel(4);
        Player sigrun = player("Sigrun");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(null);

            // The plan is not empty, so this is the dependency gate refusing, not the
            // battle gate or a missing upgrade.
            assertFalse(MercenaryStatService.planFor("Sigrun").isEmpty());
            assertFalse(MercenaryStatService.apply(sigrun));
            assertFalse(MercenaryStatService.apply(sigrun));
            assertFalse(MercenaryStatService.isApplied(sigrun));
            assertDoesNotThrow(() -> MercenaryStatService.clear(sigrun));
        }
    }

    private boolean availableWith(boolean mythicLib, boolean mmoCore) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            Server server = mock(Server.class);
            PluginManager plugins = mock(PluginManager.class);
            when(plugins.isPluginEnabled("MythicLib")).thenReturn(mythicLib);
            when(plugins.isPluginEnabled("MMOCore")).thenReturn(mmoCore);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);

            return applier.isAvailable();
        }
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        return player;
    }
}
