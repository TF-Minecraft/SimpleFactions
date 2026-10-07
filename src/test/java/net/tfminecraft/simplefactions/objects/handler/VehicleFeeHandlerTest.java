package net.tfminecraft.simplefactions.objects.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;

class VehicleFeeHandlerTest {
    private Faction faction;
    private VehicleFeeHandler handler;

    @BeforeEach
    void setUp() {
        faction = mock(Faction.class);
        when(faction.hasFactionRule(any(Rules.class))).thenReturn(true);
        handler = new VehicleFeeHandler(faction);
    }

    @Test
    void nothingIsChargedBeforeALawSetsABracket() {
        handler.setRate(FeeKind.REGISTRATION_FEE, null, 2.0);

        assertFalse(handler.canCharge(FeeKind.REGISTRATION_FEE));
        assertEquals(0.0, handler.getChargedRate(FeeKind.REGISTRATION_FEE, "ironclad"));
    }

    @Test
    void bracketPullsTheGeneralRateInside() {
        handler.applyBracket(FeeKind.VEHICLE_TAX, new Bracket(5, 15));
        assertEquals(5.0, handler.getRate(FeeKind.VEHICLE_TAX));

        handler.setRate(FeeKind.VEHICLE_TAX, null, 40.0);
        assertEquals(15.0, handler.getRate(FeeKind.VEHICLE_TAX));

        handler.applyBracket(FeeKind.VEHICLE_TAX, new Bracket(0, 10));
        assertEquals(10.0, handler.getRate(FeeKind.VEHICLE_TAX));
    }

    @Test
    void typeRateOverridesGeneralAndStaysInsideTheBracket() {
        handler.applyBracket(FeeKind.TRANSFER_FEE, new Bracket(0, 3));
        handler.setRate(FeeKind.TRANSFER_FEE, null, 1.0);
        handler.setRate(FeeKind.TRANSFER_FEE, "Cruiser", 9.0);

        assertEquals(3.0, handler.getChargedRate(FeeKind.TRANSFER_FEE, "cruiser"));
        assertEquals(1.0, handler.getChargedRate(FeeKind.TRANSFER_FEE, "sloop"));
        assertTrue(handler.hasTypeRate(FeeKind.TRANSFER_FEE, "CRUISER"));

        handler.applyBracket(FeeKind.TRANSFER_FEE, new Bracket(0, 2));
        assertEquals(2.0, handler.getRate(FeeKind.TRANSFER_FEE, "cruiser"));
    }

    @Test
    void typeRateEqualToGeneralIsDropped() {
        handler.applyBracket(FeeKind.REGISTRATION_FEE, new Bracket(0, 5));
        handler.setRate(FeeKind.REGISTRATION_FEE, null, 1.0);
        handler.setRate(FeeKind.REGISTRATION_FEE, "sloop", 2.0);
        handler.setRate(FeeKind.REGISTRATION_FEE, "sloop", 1.0);
        assertFalse(handler.hasTypeRate(FeeKind.REGISTRATION_FEE, "sloop"));

        handler.setRate(FeeKind.REGISTRATION_FEE, "sloop", 2.0);
        handler.setRate(FeeKind.REGISTRATION_FEE, null, 2.0);
        assertFalse(handler.hasTypeRate(FeeKind.REGISTRATION_FEE, "sloop"));
    }

    @Test
    void ruleSwitchedOffStopsTheCharge() {
        handler.applyBracket(FeeKind.VEHICLE_TAX, new Bracket(5, 15));
        when(faction.hasFactionRule(Rules.VEHICLE_TAX)).thenReturn(false);

        assertFalse(handler.canCharge(FeeKind.VEHICLE_TAX));
        assertEquals(0.0, handler.getMax(FeeKind.VEHICLE_TAX));
        assertEquals(0.0, handler.getChargedRate(FeeKind.VEHICLE_TAX, "sloop"));
    }

    @Test
    void savedRatesAreReclampedOnLoad() {
        handler.applyBracket(FeeKind.VEHICLE_TAX, new Bracket(5, 15));
        Map<String, Double> rates = new HashMap<>(Map.of("VEHICLE_TAX", 50.0, "UNKNOWN", 1.0));
        Map<String, Map<String, Double>> typeRates = new HashMap<>();
        typeRates.put("VEHICLE_TAX", new HashMap<>(Map.of("sloop", 1.0, "cruiser", 12.0)));

        handler.load(rates, typeRates);

        assertEquals(15.0, handler.getRate(FeeKind.VEHICLE_TAX));
        assertEquals(5.0, handler.getRate(FeeKind.VEHICLE_TAX, "sloop"));
        assertEquals(12.0, handler.getRate(FeeKind.VEHICLE_TAX, "cruiser"));
        assertEquals(Map.of("VEHICLE_TAX", 15.0), handler.serializeRates());
    }

    @Test
    void absentBracketsAndIncompleteSavedOverridesPreserveValidRates() {
        handler.setRate(FeeKind.VEHICLE_TAX, "unknown", 0.0);
        handler.applyBracket(FeeKind.VEHICLE_TAX, null);
        assertFalse(handler.canCharge(FeeKind.VEHICLE_TAX));
        Map<String, Map<String, Double>> saved = new HashMap<>();
        saved.put(null, Map.of("cart", 3.0));
        saved.put("TRANSFER_FEE", null);
        saved.put("VEHICLE_TAX", Map.of("cart", 2.0));
        handler.load(Map.of(), saved);
        assertEquals(2.0, handler.getRate(FeeKind.VEHICLE_TAX, "cart"));
        assertFalse(handler.hasTypeRate(FeeKind.TRANSFER_FEE, "cart"));
    }

}
