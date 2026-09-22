package me.Plugins.SimpleFactions.mercenary.stat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.mercenary.company.CompanyFixture;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

class MercenaryStatServiceTest {
    private CompanyFixture fixture;
    private MercenaryCompany company;
    private RecordingStatApplier applier;
    private Player sigrun;

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

        applier = new RecordingStatApplier();
        MercenaryStatService.reset();
        MercenaryStatService.setApplier(applier);
        sigrun = player("Sigrun");
    }

    @AfterEach
    void tearDown() {
        MercenaryStatService.reset();
        CompanyFixture.clearCompanyUpgrades();
        CompanyFixture.clearGlobalGuilds();
    }

    @Test
    void appliedAmountsMatchUpgradeLevels() {
        openGate();
        company.getUpgrade("company_health").setLevel(4);
        company.getUpgrade("company_mana").setLevel(3);
        company.getUpgrade("company_mana_regen").setLevel(2);

        assertTrue(MercenaryStatService.apply(sigrun));

        assertEquals(1, applier.applications.size());
        assertEquals(new MercenaryStatPlan(2.0, 3.0, 0.2), applier.applications.get(0));
        assertEquals(new MercenaryStatPlan(2.0, 3.0, 0.2), MercenaryStatService.appliedTo(sigrun));
        assertTrue(MercenaryStatService.isApplied(sigrun));
    }

    @Test
    void theProductionGateIsClosedSoNothingIsApplied() {
        company.getUpgrade("company_health").setLevel(4);

        assertFalse(MercenaryStatService.apply(sigrun));

        assertTrue(MercenaryStatService.planFor("Sigrun").isEmpty());
        assertTrue(applier.applications.isEmpty());
        assertFalse(MercenaryStatService.isApplied(sigrun));
    }

    @Test
    void aPlayerWithoutACompanyGetsNothing() {
        openGate();
        company.getUpgrade("company_health").setLevel(4);
        Player bjorn = player("Bjorn");

        assertFalse(MercenaryStatService.apply(bjorn));
        assertTrue(applier.applications.isEmpty());
    }

    @Test
    void doubleApplyDoesNotStack() {
        openGate();
        company.getUpgrade("company_health").setLevel(4);

        assertTrue(MercenaryStatService.apply(sigrun));
        assertTrue(MercenaryStatService.apply(sigrun));

        assertEquals(1, applier.applications.size());
        assertEquals(0, applier.strips);
    }

    @Test
    void aChangedPlanIsStrippedBeforeItIsReapplied() {
        openGate();
        company.getUpgrade("company_health").setLevel(4);
        MercenaryStatService.apply(sigrun);

        company.getUpgrade("company_health").setLevel(6);
        MercenaryStatService.apply(sigrun);

        assertEquals(2, applier.applications.size());
        assertEquals(3.0, applier.applications.get(1).maxHealth());
        assertEquals(1, applier.strips);
    }

    @Test
    void clearEmptiesTheMapAndStrips() {
        openGate();
        company.getUpgrade("company_mana").setLevel(2);
        MercenaryStatService.apply(sigrun);

        MercenaryStatService.clear(sigrun);

        assertFalse(MercenaryStatService.isApplied(sigrun));
        assertEquals(MercenaryStatPlan.EMPTY, MercenaryStatService.appliedTo(sigrun));
        assertEquals(1, applier.strips);
    }

    @Test
    void strippingIsSafeWhenNothingWasEverApplied() {
        assertDoesNotThrow(() -> MercenaryStatService.clear(player("Bjorn")));
        assertFalse(MercenaryStatService.isApplied(sigrun));
    }

    @Test
    void missingDependenciesLeaveThePlayerUntouched() {
        openGate();
        applier.available = false;
        company.getUpgrade("company_health").setLevel(4);

        assertFalse(MercenaryStatService.apply(sigrun));

        assertTrue(applier.applications.isEmpty());
        assertEquals(0, applier.strips);
        assertFalse(MercenaryStatService.isApplied(sigrun));
    }

    @Test
    void losingTheBuffSourceStripsOnTheNextApply() {
        openGate();
        company.getUpgrade("company_health").setLevel(4);
        MercenaryStatService.apply(sigrun);

        company.getUpgrade("company_health").setLevel(0);

        assertFalse(MercenaryStatService.apply(sigrun));
        assertFalse(MercenaryStatService.isApplied(sigrun));
        assertEquals(1, applier.strips);
    }

    @Test
    void endingABattleDropsEveryParticipant() {
        openGate();
        company.getUpgrade("company_health").setLevel(4);
        MercenaryStatService.apply(sigrun);

        MercenaryStatService.clearParticipants(Set.of(sigrun.getUniqueId()));

        assertFalse(MercenaryStatService.isApplied(sigrun));
    }

    @Test
    void planForReadsStraightFromTheCompanyUpgrades() {
        openGate();
        assertTrue(MercenaryStatService.planFor("Sigrun").isEmpty());

        company.getUpgrade("company_mana_regen").setLevel(10);

        assertEquals(new MercenaryStatPlan(0, 0, 1.0), MercenaryStatService.planFor("sigrun"));
    }

    private static void openGate() {
        MercenaryStatService.setGate(player -> true);
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        return player;
    }
}
