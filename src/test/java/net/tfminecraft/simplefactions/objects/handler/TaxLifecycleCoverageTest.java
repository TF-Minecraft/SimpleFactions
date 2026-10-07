package net.tfminecraft.simplefactions.objects.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class TaxLifecycleCoverageTest {
  private FactionDomainFixture fixture;
  private Faction faction;

  @BeforeEach
  void setUp() {
    fixture = new FactionDomainFixture();
    fixture.player("Leader");
    faction = fixture.saved("home", "Leader");
  }

  @AfterEach
  void tearDown() {
    IncomePreviewContext.clear();
    fixture.close();
  }

  @ParameterizedTest
  @CsvSource({"GUILDS,GUILD_ID", "VASSALS,VASSAL_ID", "TARIFFS,TARIFF_ID"})
  void specificTaxEditsRespectTheSameLawBracketAsTheirCategory(
      TaxTarget category, TaxTarget specific) {
    TaxHandler taxes = faction.getTaxHandler();
    Bracket bracket = new Bracket(5.0, 25.0);
    assertEquals("[5.0-25.0]", ChatColor.stripColor(bracket.getString()));
    taxes.applyBracket(category, bracket);
    assertTrue(taxes.canCollectTax(specific));
    assertEquals(5.0, taxes.getMin(category));
    assertEquals(25.0, taxes.getMax(category));
    assertEquals(5.0, taxes.getMin(specific), "Specific rates must not bypass the law's minimum");
    assertEquals(25.0, taxes.getMax(specific), "Specific rates must not bypass the law's maximum");
    assertSame(bracket, taxes.getBracket(specific));
  }

  @ParameterizedTest
  @CsvSource({"GUILDS,GUILD_ID", "VASSALS,VASSAL_ID", "TARIFFS,TARIFF_ID"})
  void outOfBracketSpecificTaxChatKeepsTheProposalPending(TaxTarget category, TaxTarget specific) {
    TaxHandler taxes = faction.getTaxHandler();
    taxes.applyBracket(category, new Bracket(5, 25));
    double original = taxes.getTaxRate(specific, "target", false);
    InventoryManager inventory = new InventoryManager();
    Player leader = fixture.online.get("Leader");
    inventory.setChanging(faction, leader, specific, "target");
    for (String message : List.of("4.99", "25.01")) {
      AsyncPlayerChatEvent event = mock(AsyncPlayerChatEvent.class);
      when(event.getMessage()).thenReturn(message);
      inventory.taxChat(leader, event);
      assertTrue(inventory.chatTrigger(leader));
      assertEquals(original, taxes.getTaxRate(specific, "target", false));
      assertTrue(
          faction.getGovernment().getCouncil().getProposalHandler().getProposals().isEmpty());
    }
    verify(leader).sendMessage(contains("minimum tax rate"));
    verify(leader).sendMessage(contains("maximum tax rate"));
  }

  @Test
  void snapshotRestoresAllRatesAndIndependentOverridesExactlyOnce() {
    TaxHandler taxes = faction.getTaxHandler();
    taxes.setCitizenTax(11.0);
    taxes.setGuildTax(22.0);
    taxes.setVassalTax(33.0);
    taxes.setDividendTax(44.0);
    taxes.setTariffs(55.0);
    taxes.setTaxRate(TaxTarget.GUILD_ID, "merchant", 12.0);
    taxes.setTaxRate(TaxTarget.VASSAL_ID, "duchy", 23.0);
    taxes.setTaxRate(TaxTarget.TARIFF_ID, "ally", 34.0);
    Map<TaxTarget, HashMap<String, Double>> original = new HashMap<>();
    taxes.getSpecificTaxes().forEach((key, value) -> original.put(key, new HashMap<>(value)));
    taxes.saveState();
    HashMap<String, Double> displaced = taxes.getSpecificTaxes().get(TaxTarget.GUILDS);
    displaced.put("merchant", 99.0);
    for (TaxTarget category :
        List.of(
            TaxTarget.CITIZENS,
            TaxTarget.GUILDS,
            TaxTarget.VASSALS,
            TaxTarget.DIVIDENDS,
            TaxTarget.TARIFFS)) taxes.setTaxRate(category, null, 0);
    taxes.setTaxRate(TaxTarget.GUILD_ID, "temporary", 98.0);
    taxes.restoreState();
    assertEquals(
        List.of(11.0, 22.0, 33.0, 44.0, 55.0),
        List.of(
            taxes.getCitizenTax(),
            taxes.getGuildTax(),
            taxes.getVassalTax(),
            taxes.getDividendTax(),
            taxes.getTariffs()));
    assertEquals(original, taxes.getSpecificTaxes());
    displaced.put("merchant", 97.0);
    assertEquals(12.0, taxes.getSpecificTax(TaxTarget.GUILDS, "merchant"));
    taxes.setGuildTax(40.0);
    taxes.restoreState();
    assertEquals(40.0, taxes.getGuildTax(), "A consumed snapshot must not roll back a later edit");
  }

  @Test
  void loadingRedundantLegacyCitizenAndDividendOverridesKeepsTheScalarRates() {
    var data = fixture.data("legacy", "LegacyLeader");
    data.citizenTax = 12.5;
    data.dividendTax = 22.5;
    data.specificTaxes.put("CITIZENS", new HashMap<>(Map.of("legacy-player", 12.5)));
    data.specificTaxes.put("DIVIDENDS", new HashMap<>(Map.of("legacy-guild", 22.5)));
    TaxHandler taxes = fixture.saved(data).getTaxHandler();
    assertEquals(12.5, taxes.getCitizenTax());
    assertEquals(22.5, taxes.getDividendTax());
    assertTrue(taxes.getSpecificTaxes().isEmpty());
  }

  @ParameterizedTest
  @CsvSource({"GUILDS,GUILD_ID", "VASSALS,VASSAL_ID", "TARIFFS,TARIFF_ID"})
  void returningAnOverrideToTheDefaultRemovesOnlyThatOverride(
      TaxTarget category, TaxTarget specific) {
    TaxHandler taxes = faction.getTaxHandler();
    taxes.setTaxRate(category, null, 20.0);
    taxes.setTaxRate(specific, "absent", 20.0);
    assertFalse(taxes.getSpecificTaxes().containsKey(category));
    taxes.setTaxRate(specific, "first", 5.0);
    taxes.setTaxRate(specific, "second", 15.0);
    assertEquals(5.0, taxes.getTaxRate(category, "first", false));
    assertEquals(5.0, taxes.getTaxRate(specific, "first", false));
    assertEquals(20.0, taxes.getTaxRate(specific, null, false));
    assertEquals(20.0, taxes.getTaxRate(specific, "absent", false));
    taxes.setTaxRate(specific, "first", 20.0);
    assertEquals(Map.of("second", 15.0), taxes.getSpecificTaxes().get(category));
    assertEquals(-1.0, taxes.getSpecificTax(category, "first"));
    taxes.setTaxRate(specific, "second", 20.0);
    assertFalse(taxes.getSpecificTaxes().containsKey(category));
    assertEquals(20.0, taxes.getTaxRate(category, "second", false));
  }

  @ParameterizedTest
  @CsvSource({"GUILDS,GUILD_ID", "VASSALS,VASSAL_ID", "TARIFFS,TARIFF_ID"})
  void aLawBracketClampsOverridesAndDropsOnlyThoseEqualToTheNewDefault(
      TaxTarget category, TaxTarget specific) {
    TaxHandler taxes = faction.getTaxHandler();
    taxes.setTaxRate(category, null, 40.0);
    taxes.setTaxRate(specific, "low", 2.0);
    taxes.setTaxRate(specific, "high", 90.0);
    taxes.setTaxRate(specific, "middle", 17.5);
    taxes.applyBracket(category, new Bracket(5.0, 25.0));
    assertEquals(25.0, taxes.getTaxRate(category, null, false));
    assertEquals(Map.of("low", 5.0, "middle", 17.5), taxes.getSpecificTaxes().get(category));
    assertEquals(25.0, taxes.getTaxRate(specific, "high", false));
    taxes.applyBracket(category, new Bracket(0, 0));
    assertFalse(taxes.getSpecificTaxes().containsKey(category));
    assertEquals(0.0, taxes.getTaxRate(category, null, false));
  }

  @ParameterizedTest
  @EnumSource(
      value = TaxTarget.class,
      names = {"CITIZENS", "DIVIDENDS"})
  void citizenAndDividendLawsClampBothEndsWithoutChangingOtherTaxes(TaxTarget target) {
    TaxHandler taxes = faction.getTaxHandler();
    taxes.setTaxRate(target, null, 4.0);
    taxes.applyBracket(target, new Bracket(10, 30));
    assertEquals(10.0, taxes.getTaxRate(target, null, false));
    taxes.setTaxRate(target, null, 99.0);
    taxes.applyBracket(target, new Bracket(10, 30));
    assertEquals(30.0, taxes.getTaxRate(target, null, false));
    assertEquals(20.0, taxes.getVassalTax());
  }

  @ParameterizedTest
  @EnumSource(TaxTarget.class)
  void permittedTaxTargetsDefaultToTheFullRange(TaxTarget target) {
    TaxHandler taxes = faction.getTaxHandler();
    assertTrue(taxes.canCollectTax(target));
    assertNull(taxes.getBracket(target));
    assertEquals(0.0, taxes.getMin(target));
    assertEquals(100.0, taxes.getMax(target));
  }

  @ParameterizedTest
  @CsvSource({
    "CITIZENS,CITIZEN_TAX",
    "GUILDS,GUILD_TAX",
    "GUILD_ID,GUILD_TAX",
    "VASSALS,VASSAL_TAX",
    "VASSAL_ID,VASSAL_TAX",
    "DIVIDENDS,DIVIDEND_TAX",
    "TARIFFS,TARIFFS",
    "TARIFF_ID,TARIFFS"
  })
  void disabledTaxRulesHideBothLimitsEvenWhenABracketWasLoaded(TaxTarget target, String rule) {
    fixture.lawGroup("limits", Map.of("effects.faction.rules", List.of(rule + " false")));
    Faction restricted = fixture.saved("restricted", "RestrictedLeader");
    TaxHandler taxes = restricted.getTaxHandler();
    taxes.applyBracket(target, new Bracket(5, 25));
    assertFalse(taxes.canCollectTax(target));
    assertEquals(0.0, taxes.getMin(target));
    assertEquals(0.0, taxes.getMax(target));
  }

  @Test
  void effectiveTaxUsesTheCurrentGovernmentAndPreviewDoesNotMutateTheRate() {
    TaxHandler taxes = faction.getTaxHandler();
    taxes.setCitizenTax(12.75);
    double efficiency = faction.getGovernment().getTaxEfficiency();
    assertEquals(
        Math.round(12.75 * efficiency * 100.0) / 100.0,
        taxes.getTaxRate(TaxTarget.CITIZENS, null, true));
    IncomePreviewContext.open(IncomePreviewContext.tax(faction, TaxTarget.CITIZENS, null, 37.5));
    assertEquals(37.5, taxes.getTaxRate(TaxTarget.CITIZENS, null, false));
    assertEquals(12.75, taxes.getCitizenTax());
    IncomePreviewContext.clear();
    assertEquals(12.75, taxes.getTaxRate(TaxTarget.CITIZENS, null, false));
  }

  @Test
  void taxChangePreviewConservesRealGuildBalancesAndTaxIncome() {
    Guild realm = faction.getOrCreateMainGuild();
    Guild merchants = fixture.guild(faction, "merchants", "Merchant");
    realm.getBank().deposit(100.0);
    merchants.getBank().deposit(100.0);
    merchants.getTradeBreakdown().setIncome(200.0);
    faction.getTaxHandler().setGuildTax(10.0);
    faction.getGovernment().setPower(faction.getGovernment().getMaxPower());
    double efficiency = faction.getGovernment().getTaxEfficiency();
    double beforeRealm = realm.getLedger().getNetIncome();
    double beforeMerchants = merchants.getLedger().getNetIncome();
    Map<Guild, Double> deltas =
        faction.getTaxHandler().getTaxChangeEffects(TaxTarget.GUILDS, null, 25.0);
    double extraTax = Math.round(200.0 * 0.15 * efficiency * 100.0) / 100.0;
    assertTrue(extraTax > 0);
    assertEquals(extraTax, deltas.get(realm));
    assertEquals(-extraTax, deltas.get(merchants));
    assertEquals(0.0, deltas.values().stream().mapToDouble(Double::doubleValue).sum(), 0.0001);
    assertEquals(100.0, realm.getBank().getWealth());
    assertEquals(100.0, merchants.getBank().getWealth());
    assertEquals(beforeRealm, realm.getLedger().getNetIncome());
    assertEquals(beforeMerchants, merchants.getLedger().getNetIncome());
    assertEquals(10.0, faction.getTaxHandler().getGuildTax());
    assertNull(IncomePreviewContext.current());
  }
}
