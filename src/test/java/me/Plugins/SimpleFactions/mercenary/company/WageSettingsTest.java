package me.Plugins.SimpleFactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The locked worked example: a 20% base against the config minimums pays a
 * soldier 2 denars a day and 10 denars a battle.
 */
class WageSettingsTest {

    @Test
    void twentyPercentOfTheMinimumsPaysTwoADayAndTenABattle() {
        WageSettings wages = new WageSettings();
        wages.setActivePercent(20);

        assertEquals(2.0, wages.activeShareOf(10.0, "Sigrun"), 1e-9);
        assertEquals(10.0, wages.activeShareOf(50.0, "Sigrun"), 1e-9);
    }

    @Test
    void anOverrideBeatsTheBase() {
        WageSettings wages = new WageSettings();
        wages.setActivePercent(20);
        wages.setActiveOverride("Sigrun", 50.0);

        assertEquals(25.0, wages.activeShareOf(50.0, "Sigrun"), 1e-9);
        assertEquals(10.0, wages.activeShareOf(50.0, "Bjorn"), 1e-9);
    }

    @Test
    void anOverrideIsCaseInsensitiveAndClearable() {
        WageSettings wages = new WageSettings();
        wages.setActivePercent(20);
        wages.setActiveOverride("Sigrun", 50.0);

        assertEquals(50.0, wages.activePercentFor("sigrun"), 1e-9);
        wages.clearActiveOverride("SIGRUN");
        assertNull(wages.getActiveOverride("Sigrun"));
        assertEquals(20.0, wages.activePercentFor("Sigrun"), 1e-9);
    }

    @Test
    void percentagesClampToTheirRange() {
        WageSettings wages = new WageSettings();
        wages.setActivePercent(-30);
        assertEquals(0.0, wages.getActivePercent(), 1e-9);
        wages.setActivePercent(400);
        assertEquals(100.0, wages.getActivePercent(), 1e-9);
        wages.setActiveOverride("Sigrun", 400.0);
        assertEquals(100.0, wages.activePercentFor("Sigrun"), 1e-9);
    }

    @Test
    void aZeroBasePaysNothing() {
        WageSettings wages = new WageSettings();
        assertEquals(0.0, wages.activeShareOf(50.0, "Sigrun"), 1e-9);
        assertEquals(0.0, wages.peacetimeFor("Sigrun"), 1e-9);
    }

    @Test
    void peacetimeTakesABaseAndAnOverride() {
        WageSettings wages = new WageSettings();
        wages.setPeacetimePerDay(4.0);
        wages.setPeacetimeOverride("Sigrun", 12.0);

        assertEquals(4.0, wages.peacetimeFor("Bjorn"), 1e-9);
        assertEquals(12.0, wages.peacetimeFor("Sigrun"), 1e-9);
    }

    @Test
    void aNegativePeacetimeWageIsRefused() {
        WageSettings wages = new WageSettings();
        wages.setPeacetimePerDay(-10.0);
        assertEquals(0.0, wages.getPeacetimePerDay(), 1e-9);
        wages.setPeacetimeOverride("Sigrun", -10.0);
        assertEquals(0.0, wages.peacetimeFor("Sigrun"), 1e-9);
    }
}
