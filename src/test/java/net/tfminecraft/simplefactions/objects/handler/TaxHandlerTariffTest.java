package net.tfminecraft.simplefactions.objects.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;

class TaxHandlerTariffTest {
    private TaxHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TaxHandler(mock(Faction.class), 0, 0, 0, 0, 15.0);
    }

    @Test
    void factionSpecificTariffOverridesTheBaseRate() {
        handler.setTaxRate(TaxTarget.TARIFF_ID, "fig", 0.0);

        assertEquals(0.0, handler.getTaxRate(TaxTarget.TARIFFS, "fig", false));
        assertEquals(15.0, handler.getTaxRate(TaxTarget.TARIFFS, "other", false));
        assertEquals(15.0, handler.getTaxRate(TaxTarget.TARIFFS, null, false));
    }

    @Test
    void factionSpecificTariffAppliesWhenTheBaseRateIsZero() {
        handler.setTaxRate(TaxTarget.TARIFFS, null, 0.0);
        handler.setTaxRate(TaxTarget.TARIFF_ID, "rival", 20.0);

        assertEquals(20.0, handler.getTaxRate(TaxTarget.TARIFFS, "rival", false));
        assertEquals(0.0, handler.getTaxRate(TaxTarget.TARIFFS, "fig", false));
    }

    @Test
    void closingTariffsClampsFactionSpecificRates() {
        handler.setTaxRate(TaxTarget.TARIFF_ID, "rival", 30.0);

        handler.applyBracket(TaxTarget.TARIFFS, new Bracket(0, 0));

        assertEquals(0.0, handler.getTaxRate(TaxTarget.TARIFFS, "rival", false));
        assertFalse(handler.hasSpecificTax(TaxTarget.TARIFFS, "rival"));
    }
}
