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
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;

class TaxHandlerHubTaxTest {
    private double previousCap;
    private Faction faction;
    private TaxHandler handler;

    @BeforeEach
    void setUp() {
        previousCap = Cache.supplyHubMaxTax;
        SupplyHubService.loadConfig(null);
        faction = mock(Faction.class);
        Government government = mock(Government.class);
        when(faction.hasFactionRule(Rules.TARIFFS)).thenReturn(true);
        when(faction.hasFactionRule(Rules.HUB_TAX)).thenReturn(true);
        when(faction.getGovernment()).thenReturn(government);
        when(government.getTaxEfficiency()).thenReturn(0.8);
        handler = new TaxHandler(faction, 0, 0, 0, 0, 15);
    }

    @AfterEach
    void tearDown() {
        IncomePreviewContext.clear();
        Cache.supplyHubMaxTax = previousCap;
    }

    @Test
    void defaultsToZeroAndCapsAtTenWithTaxEfficiency() {
        assertEquals(0, handler.getHubTax());
        assertEquals(10, handler.getMax(TaxTarget.HUB_TAX));
        handler.setTaxRate(TaxTarget.HUB_TAX, null, 90);
        assertEquals(10, handler.getTaxRate(TaxTarget.HUB_TAX, "any", false));
        assertEquals(8, handler.getTaxRate(TaxTarget.HUB_TAX, null, true));
        handler.setHubTax(-1);
        assertEquals(0, handler.getHubTax());
    }

    @Test
    void configuredCapAndHubTaxBracketsBothApply() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-hubs.max-tax", 25);
        SupplyHubService.loadConfig(config);
        handler.applyBracket(TaxTarget.HUB_TAX, new Bracket(0, 50));
        handler.setHubTax(80);
        assertEquals(25, handler.getHubTax());
        assertEquals(25, handler.getMax(TaxTarget.HUB_TAX));
        handler.applyBracket(TaxTarget.HUB_TAX, new Bracket(5, 20));
        assertEquals(20, handler.getHubTax());
        assertEquals(5, handler.getMin(TaxTarget.HUB_TAX));
        assertEquals(20, handler.getMax(TaxTarget.HUB_TAX));
        handler.applyBracket(TaxTarget.HUB_TAX, new Bracket(0, 0));
        assertEquals(0, handler.getHubTax());
    }

    @Test
    void lawPreviewUsesTheProposedHubTaxBracketAndConfiguredCap() {
        handler.applyBracket(TaxTarget.TARIFFS, new Bracket(0, 10));
        handler.setHubTax(5);
        YamlConfiguration config = new YamlConfiguration();
        config.set("effects.FACTION.brackets.TARIFFS", "20-80");
        config.set("effects.FACTION.brackets.HUB_TAX", "20-40");
        Law proposed = new Law("taxes", "proposed", config);
        LawGroup group = mock(LawGroup.class);

        IncomePreviewContext.open(IncomePreviewContext.law(faction, group, proposed));
        assertEquals(20, handler.getTaxRate(TaxTarget.TARIFFS, null, false));
        assertEquals(20, handler.getTaxRate(TaxTarget.HUB_TAX, null, false));
        assertEquals(16, handler.getTaxRate(TaxTarget.HUB_TAX, null, true));
        Cache.supplyHubMaxTax = 15;
        assertEquals(15, handler.getTaxRate(TaxTarget.HUB_TAX, null, false));
        IncomePreviewContext.clear();

        assertEquals(5, handler.getHubTax());
        assertEquals(5, handler.getTaxRate(TaxTarget.HUB_TAX, null, false));
        IncomePreviewContext.open(IncomePreviewContext.tax(faction, TaxTarget.HUB_TAX, null, 40));
        assertEquals(5, handler.getTaxRate(TaxTarget.HUB_TAX, null, false));
    }

    @Test
    void tariffsHaveNoEffectOnHubTaxAndForbiddenHubTaxCannotBeSet() {
        handler.setHubTax(9);
        handler.applyBracket(TaxTarget.TARIFFS, new Bracket(0, 0));
        when(faction.hasFactionRule(Rules.TARIFFS)).thenReturn(false);
        assertEquals(9, handler.getHubTax());
        assertEquals(10, handler.getMax(TaxTarget.HUB_TAX));
        assertFalse(handler.canCollectTax(TaxTarget.TARIFFS));

        when(faction.hasFactionRule(Rules.HUB_TAX)).thenReturn(false);
        handler.setTaxRate(TaxTarget.HUB_TAX, null, 10);
        assertEquals(0, handler.getHubTax());
        assertEquals(0, handler.getTaxRate(TaxTarget.HUB_TAX, null, true));
        assertEquals(0, handler.getMax(TaxTarget.HUB_TAX));
        assertEquals(0, handler.getMin(TaxTarget.HUB_TAX));
        assertFalse(handler.canCollectTax(TaxTarget.HUB_TAX));
    }

    @Test
    void lawPreviewUsesHubTaxRuleAndDefaultsWithoutChangingLiveRate() {
        handler.applyBracket(TaxTarget.HUB_TAX, new Bracket(0, 50));
        handler.setHubTax(40);
        YamlConfiguration config = new YamlConfiguration();
        config.set("effects.faction.rules", java.util.List.of("hub_tax false"));
        LawGroup group = mock(LawGroup.class);
        IncomePreviewContext.open(IncomePreviewContext.law(faction, group, new Law("economy", "banned", config)));
        assertEquals(0, handler.getTaxRate(TaxTarget.HUB_TAX, null, false));
        IncomePreviewContext.clear();
        assertEquals(40, handler.getHubTax());

        config.set("effects.faction.rules", java.util.List.of("tariffs false"));
        IncomePreviewContext.open(IncomePreviewContext.law(faction, group, new Law("economy", "legacy", config)));
        assertEquals(10, handler.getTaxRate(TaxTarget.HUB_TAX, null, false));
        IncomePreviewContext.clear();
        assertEquals(40, handler.getHubTax());
    }

    @Test
    void hubTaxCannotAcquireSpecificRatesAndRestoresItsSavedState() {
        handler.applyBracket(TaxTarget.HUB_TAX, new Bracket(0, 50));
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
    void savedHubTaxAndPermitsLoadWithoutChangingTheLiveRate() {
        handler.applyBracket(TaxTarget.HUB_TAX, new Bracket(0, 50));
        handler.setHubTax(8);
        FactionData data = JsonUtil.GSON.fromJson(
                "{\"hub tax\":23.5,\"hub permits\":[\"merchants\"]}", FactionData.class);
        assertEquals(23.5, data.hubTax);
        assertEquals(java.util.List.of("merchants"), data.hubPermits);
        assertEquals(8, handler.getHubTax());
        String fresh = JsonUtil.GSON.toJson(new FactionData());
        assertFalse(fresh.contains("hub tax"));
        assertFalse(fresh.contains("hub permits"));
    }
}
