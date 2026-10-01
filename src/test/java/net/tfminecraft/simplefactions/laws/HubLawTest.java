package net.tfminecraft.simplefactions.laws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.Brackets;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.LawHandler;
import net.tfminecraft.simplefactions.objects.handler.TaxHandler;
import net.tfminecraft.simplefactions.utils.BracketToTaxTarget;
import net.tfminecraft.simplefactions.utils.LoreWriter;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

class HubLawTest {
    private final double previousCap = Cache.supplyHubMaxTax;
    private Faction faction;
    private TaxHandler taxes;
    private LawGroup group;

    @BeforeEach
    void setUp() throws Exception {
        Cache.supplyHubMaxTax = 50;
        group = new LawGroup("economy", bundled().getConfigurationSection("economy"));
        faction = mock(Faction.class);
        LawHandler laws = mock(LawHandler.class);
        when(faction.getLawHandler()).thenReturn(laws);
        when(laws.getCurrentLaws()).thenAnswer(call -> List.of(group.getCurrent()));
        when(faction.getGovernment()).thenReturn(mock(Government.class));
        doCallRealMethod().when(faction).hasFactionRule(any(Rules.class));
        doCallRealMethod().when(faction).getExplicitRule(any(Scope.class), any(Rules.class));
        doCallRealMethod().when(faction).applyLaw(any(Law.class), any(LawGroup.class));
        taxes = new TaxHandler(faction, 0, 0, 0, 0, 20);
        setField("lawHandler", laws);
        setField("taxHandler", taxes);
        group.setCurrent(group.getLaw("isolationism"));
        faction.applyLaw(group.getCurrent(), group);
    }

    @AfterEach
    void restore() {
        IncomePreviewContext.clear();
        Cache.supplyHubMaxTax = previousCap;
    }

    @Test
    void bundledEconomyLawsHaveIndependentSettingsAndLore() {
        assertEquals(TaxTarget.HUB_TAX, BracketToTaxTarget.convert(Brackets.HUB_TAX));
        for (Map.Entry<String, Double> setting : Map.of(
                "free_trade", 10.0, "mercantilism", 25.0,
                "protectionism", 40.0, "isolationism", 50.0).entrySet()) {
            faction.applyLaw(group.getLaw(setting.getKey()), group);
            LawEffect effect = group.getCurrent().getScopedEffects().get(Scope.FACTION);
            assertEquals(0, effect.getBrackets().get(Brackets.HUB_TAX).getMin());
            assertEquals(setting.getValue(), taxes.getMax(TaxTarget.HUB_TAX));
            assertTrue(faction.hasFactionRule(Rules.HUB_TAX));
            assertTrue(faction.hasFactionRule(Rules.SUPPLY_HUBS));
            List<String> lore = new ArrayList<>();
            LoreWriter.writeEffect(Scope.FACTION, effect, lore);
            assertTrue(lore.stream().anyMatch(line -> line.contains("Hub Tax") && line.contains("Range:")));
        }
        faction.applyLaw(group.getLaw("decentralized"), group);
        LawEffect effect = group.getCurrent().getScopedEffects().get(Scope.FACTION);
        assertFalse(effect.getRules().get(Rules.HUB_TAX));
        assertFalse(effect.getRules().get(Rules.SUPPLY_HUBS));
        assertTrue(effect.affectsEconomy());
        List<String> lore = new ArrayList<>();
        LoreWriter.writeEffect(Scope.FACTION, effect, lore);
        assertTrue(lore.contains(StringFormatter.formatHex("  #d65c5c✖ #d4c9aeCan Collect Hub Taxes")));
        assertTrue(lore.contains(StringFormatter.formatHex("  #d65c5c✖ #d4c9aeCan Build Supply Hubs")));
    }

    @Test
    void freeTradeAllowsTenPercentHubTaxWhileTariffsStayOff() {
        taxes.setHubTax(45);
        taxes.setSpecificTax(TaxTarget.TARIFFS, "foreign", 35);
        faction.applyLaw(group.getLaw("free_trade"), group);
        assertEquals(10, taxes.getHubTax());
        assertTrue(taxes.canCollectTax(TaxTarget.HUB_TAX));
        assertEquals(0, taxes.getTariffs());
        assertEquals(0, taxes.getTaxRate(TaxTarget.TARIFFS, "foreign", false));
        assertFalse(taxes.canCollectTax(TaxTarget.TARIFFS));
    }

    @Test
    void lawChangesPullDownTheSavedRateAndRestoreDefaultsAfterABan() {
        taxes.setHubTax(50);
        faction.applyLaw(group.getLaw("protectionism"), group);
        assertEquals(40, taxes.getHubTax());
        faction.applyLaw(group.getLaw("mercantilism"), group);
        assertEquals(25, taxes.getHubTax());
        faction.applyLaw(group.getLaw("decentralized"), group);
        assertEquals(0, taxes.getHubTax());
        assertEquals(0, taxes.getMax(TaxTarget.HUB_TAX));
        assertFalse(faction.hasFactionRule(Rules.SUPPLY_HUBS));
        faction.applyLaw(group.getLaw("free_trade"), group);
        assertEquals(10, taxes.getMax(TaxTarget.HUB_TAX));
        taxes.setHubTax(8);
        assertEquals(8, taxes.getHubTax());
        assertTrue(faction.hasFactionRule(Rules.SUPPLY_HUBS));
    }

    @Test
    void oldLawsFilesDefaultBothRulesToAllowedAndTheBracketToZeroToTen() {
        YamlConfiguration config = bundled();
        for (String id : config.getConfigurationSection("economy.laws").getKeys(false)) {
            String path = "economy.laws." + id + ".effects.faction";
            config.set(path + ".brackets.hub_tax", null);
            List<String> rules = new ArrayList<>(config.getStringList(path + ".rules"));
            rules.removeIf(rule -> rule.startsWith("hub_tax ") || rule.startsWith("supply_hubs "));
            config.set(path + ".rules", rules);
        }
        group = new LawGroup("economy", config.getConfigurationSection("economy"));
        for (Law law : group.getLaws().values()) {
            faction.applyLaw(law, group);
            assertTrue(faction.hasFactionRule(Rules.HUB_TAX), law.getId());
            assertTrue(faction.hasFactionRule(Rules.SUPPLY_HUBS), law.getId());
            assertEquals(0, taxes.getMin(TaxTarget.HUB_TAX));
            assertEquals(10, taxes.getMax(TaxTarget.HUB_TAX));
            taxes.setHubTax(50);
            assertEquals(10, taxes.getHubTax());
        }
    }

    @Test
    void previewsUseProposedSettingsAndLeaveLiveLawsAndRateAlone() {
        taxes.setHubTax(40);
        IncomePreviewContext.open(IncomePreviewContext.law(faction, group, group.getLaw("free_trade")));
        assertEquals(10, taxes.getTaxRate(TaxTarget.HUB_TAX, null, false));
        IncomePreviewContext.clear();
        assertEquals(40, taxes.getHubTax());
        IncomePreviewContext.open(IncomePreviewContext.law(faction, group, group.getLaw("decentralized")));
        assertEquals(0, taxes.getTaxRate(TaxTarget.HUB_TAX, null, false));
        assertFalse(faction.hasFactionRule(Rules.SUPPLY_HUBS));
        IncomePreviewContext.clear();
        assertEquals("isolationism", group.getCurrent().getId());
        assertEquals(40, taxes.getHubTax());
    }

    private void setField(String name, Object value) throws Exception {
        Field field = Faction.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(faction, value);
    }

    private static YamlConfiguration bundled() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                HubLawTest.class.getResourceAsStream("/laws.yml"), StandardCharsets.UTF_8));
    }
}
