package net.tfminecraft.simplefactions.laws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.enums.Brackets;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.utils.BracketToTaxTarget;

class VehicleTaxLawTest {
    private static ConfigurationSection faction(String law) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                VehicleTaxLawTest.class.getResourceAsStream("/laws.yml"), StandardCharsets.UTF_8));
        return config.getConfigurationSection("vehicle_tax.laws." + law + ".effects.faction");
    }

    @Test
    void shippedLawsSetAllThreeVehicleBrackets() {
        for (String law : new String[] {"low", "medium", "high"}) {
            LawEffect effect = new LawEffect(Scope.FACTION, faction(law));
            for (Brackets bracket : new Brackets[] {
                    Brackets.VEHICLE_TAX, Brackets.REGISTRATION_FEE, Brackets.TRANSFER_FEE}) {
                Bracket range = effect.getBrackets().get(bracket);
                assertNotNull(range, law + " " + bracket);
            }
            // Paid from personal banks, so the law has no guild income preview.
            assertFalse(effect.affectsEconomy(), law);
        }
        LawEffect low = new LawEffect(Scope.FACTION, faction("low"));
        assertEquals(0.5, low.getBrackets().get(Brackets.TRANSFER_FEE).getMax());
    }

    @Test
    void vehicleBracketsAreNotTaxTargets() {
        assertNull(BracketToTaxTarget.convert(Brackets.VEHICLE_TAX));
        assertNull(BracketToTaxTarget.convert(Brackets.REGISTRATION_FEE));
        assertNull(BracketToTaxTarget.convert(Brackets.TRANSFER_FEE));
    }
}
