package net.tfminecraft.simplefactions.objects.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;

class TaxHandlerHubTaxTest {
    private double previousCap;
    private int previousLimit;
    private double previousUpkeep;
    private double previousGrowth;
    private TaxHandler handler;

    @BeforeEach
    void setUp() {
        previousCap = Cache.supplyHubMaxTax;
        previousLimit = Cache.supplyHubBaseLimit;
        previousUpkeep = Cache.supplyHubBaseUpkeep;
        previousGrowth = Cache.supplyHubUpkeepGrowth;
        SupplyHubService.loadConfig(null);
        Faction faction = mock(Faction.class);
        Government government = mock(Government.class);
        when(faction.hasFactionRule(Rules.TARIFFS)).thenReturn(true);
        when(faction.getGovernment()).thenReturn(government);
        when(government.getTaxEfficiency()).thenReturn(0.8);
        handler = new TaxHandler(faction, 0, 0, 0, 0, 15);
    }

    @AfterEach
    void tearDown() {
        Cache.supplyHubMaxTax = previousCap;
        Cache.supplyHubBaseLimit = previousLimit;
        Cache.supplyHubBaseUpkeep = previousUpkeep;
        Cache.supplyHubUpkeepGrowth = previousGrowth;
    }

    @Test
    void defaultsToZeroAndCapsAtFiftyWithTariffEfficiency() {
        assertEquals(0, handler.getHubTax());
        assertEquals(50, handler.getMax(TaxTarget.HUB_TAX));
        handler.setTaxRate(TaxTarget.HUB_TAX, null, 90);
        assertEquals(50, handler.getTaxRate(TaxTarget.HUB_TAX, "any", false));
        assertEquals(40, handler.getTaxRate(TaxTarget.HUB_TAX, null, true));
        handler.setHubTax(-1);
        assertEquals(0, handler.getHubTax());
    }

    @Test
    void configuredCapAndTariffBracketsBothApply() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.max-tax", 25);
        SupplyHubService.loadConfig(config);
        handler.setHubTax(80);
        assertEquals(25, handler.getHubTax());
        assertEquals(25, handler.getMax(TaxTarget.HUB_TAX));
        handler.applyBracket(TaxTarget.TARIFFS, new Bracket(5, 20));
        assertEquals(20, handler.getHubTax());
        assertEquals(5, handler.getMin(TaxTarget.HUB_TAX));
        assertEquals(20, handler.getMax(TaxTarget.HUB_TAX));
        handler.applyBracket(TaxTarget.TARIFFS, new Bracket(0, 0));
        assertEquals(0, handler.getHubTax());
    }

    @Test
    void hubTaxCannotAcquireSpecificRatesAndRestoresItsSavedState() {
        handler.setHubTax(12);
        handler.setSpecificTax(TaxTarget.HUB_TAX, "foreign", 30);
        assertFalse(handler.hasSpecificTax(TaxTarget.HUB_TAX, "foreign"));
        assertEquals(12, handler.getTaxRate(TaxTarget.HUB_TAX, "foreign", false));
        handler.saveState();
        handler.setHubTax(30);
        handler.restoreState();
        assertEquals(12, handler.getHubTax());
    }

    @Test
    void factionRateRoundTripsAndOldSavesDefaultToZero() {
        handler.setHubTax(23.5);
        FactionData data = new FactionData();
        data.hubTax = handler.getHubTax();
        FactionData restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(data), FactionData.class);
        handler.setHubTax(restored.hubTax == null ? 0 : restored.hubTax);
        assertEquals(23.5, handler.getHubTax());
        FactionData legacy = JsonUtil.GSON.fromJson("{\"id\":\"old\"}", FactionData.class);
        handler.setHubTax(legacy.hubTax == null ? 0 : legacy.hubTax);
        assertEquals(0, handler.getHubTax());
    }
}
